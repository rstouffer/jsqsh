/*
 * Copyright 2007-2012 Scott C. Gray
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sqsh;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * This class is responsible for parsing a jsqsh command line. The parsing
 * rules attempt to basically follow the same rules as UNIX sh, except that
 * jsqsh can be lazier as the expectations are that everything following a
 * pipe will be passed off, unmollested, to the operating system's shell.
 * As a result much of the fancy file descriptor redirection that a normal
 * shell needs to do, jsqsh doesn't need to bother with.
 */
public class Tokenizer {

    private static final Logger LOG = 
        Logger.getLogger(Tokenizer.class.getName());
    
    /**
     * The line that we are parsing.
     */
    private String line;
    
    /**
     * Current parse index into the line to be parsed.
     */
    private int curIdx;
    
    /**
     * Number of tokens processed.
     */
    private int tokenCount;
    
    /**
     * Line terminator
     */
    private int terminator;
    
    /**
     * If true, then literals contained within double quotes will retain the
     * surrounding double quotes.
     */
    private boolean retainDoubleQuotes = false;
    
    /**
     * Creates an empty tokenizer. The tokenizer must not be used until
     * reset() is called on it with valid input.
     */
    public Tokenizer () {
        
        reset(null, -1, retainDoubleQuotes);
    }
    
    /**
     * Creates a command line parser representing the parsed 
     * version of the provided string.
     * 
     * @param str String to be parsed
     * @param terminator The line termination character. This is passed
     *   in to ensure that it is treated as a standalone token.
     */
    public Tokenizer (String str, int terminator) {
        
        reset(str, terminator, retainDoubleQuotes);
    }
    
    /**
     * Allows the tokenizer to be re-used with a new input.
     * 
     * @param str The string to parse
     * @param terminator The current command terminator
     * @param retainDoubleQuotes If true, then double quotes will be retained
     *   as parsed.
     */
    public void reset (String str, int terminator, boolean retainDoubleQuotes) {
        
        this.line = str;
        this.terminator = terminator;
        this.tokenCount = 0;
        this.curIdx = 0;
        this.retainDoubleQuotes = retainDoubleQuotes;
    }
    
    /**
     * Returns the next token on the command line.
     * @return The parsed token, or null if the end-of-line has been reached.
     */
    public Token next() 
        throws CommandLineSyntaxException {
        
        skipWhiteSpace();
        
        /*
         * If we are already at the end of the line, then just return null.
         */
        if (curIdx >= line.length()) {
            
            return null;
        }
        
        char ch = line.charAt(curIdx);
        
        Token token = null;
        
        /*
         * First we are going to check to see if we have hit a file descriptor 
         * output redirection operation. This can be of the form:
         * 
         *    X>&Y
         *    >
         *    >>
         *    X>
         *    X>>
         *    >+X
         *    >>+X
         */
        if (ch == '>'
            || (curIdx < (line.length()-1) && Character.isDigit(ch) 
                    && line.charAt(curIdx+1) == '>')) {
            
            token = parseOutputRedirection();
        }
        else if (ch == '|') {
            
            token = parsePipe();
        }
        else if (isTerminator(ch)) {
            
            token = parseTerminator();
        }
        else {
            
            /*
              * Failing the above, we have to assume that we are just parsing
              * a plain old string.
              */
            int startIdx = curIdx;
            token = new StringToken(line, startIdx, parseString());
        }
        
        ++tokenCount;
        return token;
    }
    
    /**
     * @return A token representing the terminator.
     */
    private TerminatorToken parseTerminator() {
        
        TerminatorToken token = 
            new TerminatorToken(line, curIdx, line.charAt(curIdx));
        
        ++curIdx;
        return token;
    }
    
    /**
     * Parsing logic for a pipe (|).
     * 
     * @return The pipe command line parsed.
     * 
     * @throws CommandLineSyntaxException If there's a problem.
     */
    private Token parsePipe ()
        throws CommandLineSyntaxException {
        
        int startIdx = curIdx;

        ++curIdx;
        skipWhiteSpace();

        if (curIdx >= line.length()) {

            throw new CommandLineSyntaxException("Expected "
                + "a command following '|'", curIdx, line);
        }

        PipeToken pipe = new PipeToken(
            line, startIdx, line.substring(curIdx));

        curIdx = line.length();
        return pipe;
    }
    
    /**
     * Parses a command line output reidrection token.
     * 
     * @return The parsed token.
     * @throws CommandLineSyntaxException
     */
    private Token parseOutputRedirection()
            throws CommandLineSyntaxException {
        
        int startIdx = curIdx;
        int leftFd = 1;
        boolean isAppend = false;
        
        /*
         * Check for the beginning of a X> or X>&Y. Our caller
         * has ensured that we will have a '>' character follwing
         * this number.
         */
        if (Character.isDigit(line.charAt(curIdx))) {
            
            leftFd = line.charAt(curIdx) - '0';
            ++curIdx;
        }
        
        /*
         * Skip the current '>' character.
         */
        ++curIdx;
        
        /*
          * If we hit another '>', then we have a '>>'. Alternatively
         * if we hit a '&', then we are performing a file descriptor
         * duplication.
          */
        if (curIdx < line.length() && line.charAt(curIdx) == '>') {
                
            isAppend = true;
            ++curIdx;
        }
        else if (curIdx < line.length() && line.charAt(curIdx) == '&') {
                
            ++curIdx;
            return new FileDescriptorDupToken(line, startIdx,
                leftFd, parseNumber());
        }
        
        /*
         * At this point we have parsed one of the following:
         *   X>
         *   X>>
         *   >
         *   >>
         * Now, if there is a + then the redirection is for a 
         * session target.
         */
        if (curIdx < line.length() && line.charAt(curIdx) == '+') {
            
            int sessionId = -1;
            
            ++curIdx;
            if (curIdx < line.length() && Character.isDigit(
                line.charAt(curIdx))) {
                
                sessionId = parseNumber();
            }
            
            return new SessionRedirectToken(line, startIdx,
                sessionId, isAppend);
        }
        
        String filename = parseString();
        if (filename == null) {
            
            throw new CommandLineSyntaxException("Expected a target filename "
                + "following redirection", curIdx, line);
        }
        
        /*
         * At this point we have consumed one of the following
         *    [n]>
         *    [n]>>
         * so we are finished!
         */
        return new RedirectOutToken(line, startIdx, leftFd,
            filename, isAppend);
    }
    
    /**
     * Helper method to skip over white space.
     */
    private void skipWhiteSpace() {
        
        while (curIdx < line.length() 
                && Character.isWhitespace(line.charAt(curIdx))) {
            
            ++curIdx;
        }
    }
    
    /**
     * Test if a character is the terminator
     * @param ch The character
     * @return true if it is the terminator
     */
    private final boolean isTerminator(char ch) {
        
        return (terminator < 0 ? false : (ch == terminator));
    }
    
    /**
     * Helper function used to consume a generic "string" from the
     * command line.
     * 
     * @return The parsed string, or null if EOF is reached.
     */
    private String parseString()
        throws CommandLineSyntaxException {
        
        StringBuilder str = new StringBuilder();
        int startIdx;
        
        skipWhiteSpace();
        while (curIdx < line.length()) {
            
            char ch = line.charAt(curIdx);
            
            if (ch == '\'') {
                
                /*
                 * Single quotes are easy. Just suck them up until we hit
                 * a close quote and don't bother expanding their contents.
                 */
                ++curIdx;
                startIdx = curIdx;
                
                while (curIdx < line.length() && line.charAt(curIdx) != '\'') {
                    
                    ++curIdx;
                }
                
                if (curIdx == line.length()) {
                    
                    throw new CommandLineSyntaxException("Did not find a "
                        + "matching closing single quote", startIdx, line);
                }
                
                str.append(line.substring(startIdx, curIdx));
                ++curIdx;
            }
            else if (ch == '"') {
                
                /*
                 * Double quotes are only slightly more difficult than single.
                 * With double quotes we need to honor the escape character
                 * and we need to honor the content of the string.
                 */
                StringBuilder fragment = new StringBuilder();
                
                /*
                 * Keep the double quotes if so requested.
                 */
                if (retainDoubleQuotes) {
                    
                    str.append('"');
                }
                    
                ++curIdx;
                while (curIdx < line.length() && line.charAt(curIdx) != '"') {
                    
                    ch = line.charAt(curIdx);
                    
                    /*
                     * Handle escape clause
                     */
                    if (ch == '\\' ) {
                        
                        ++curIdx;
                        if (curIdx < line.length()) {
                            
                            fragment.append(line.charAt(curIdx));
                        }
                    }
                    else {
                        
                        fragment.append(ch);
                    }
                    
                    ++curIdx;
                }
                
                if (curIdx == line.length()) {
                    
                    throw new CommandLineSyntaxException("Did not find a "
                        + "matching closing double quote", curIdx, line);
                }
                
                str.append(fragment);
                
                if (retainDoubleQuotes) {
                    
                    str.append('"');
                }
                
                ++curIdx;
            }
            else {
                
                /*
                 * This is the case of a regular series of characters
                 */
                StringBuilder fragment = new StringBuilder();
                while (curIdx < line.length()) {
                    
                    ch = line.charAt(curIdx);
                    if (Character.isWhitespace(ch)
                        || isTerminator(ch)
                        || ch == '\''
                        || ch == '"'
                        || ch == '|'
                        || ch == '<'
                        || ch == '>'
                        || ch == '&') {
                        
                        break;
                    }
                    else if (tokenCount > 0 && ch == '\\') {
                        
                        /*
                         * NOTE NOTE: This is a special case of escape handling.
                         * SqshContext likes to prefix its command with a back-slash 
                         * (e.g. "\echo hello"). So, we don't want escape handling
                         * to take place on the first tkoen processed.
                         */
                        
                        ++curIdx;
                        if (curIdx < line.length()) {
                            
                            fragment.append(line.charAt(curIdx));
                        }
                    }
                    else {
                        
                        fragment.append(ch);
                    }
                    
                    ++curIdx;
                }
                
                str.append(fragment.toString());
                
                /*
                 * If we reached the end of the current string because
                 * we hit a single or double quote, then we should continue
                 * processing our string. As an example:
                 * 
                 *    hello'scott'
                 * 
                 * will become 
                 * 
                 *    helloscott
                 * 
                 * if we ended on any other character, then we are finish
                 * with our current token
                 */
                if (ch != '\'' && ch != '"') {
                    
                    break;
                }
            }
        }
        
        return str.toString();
    }
    
    /**
     * Helper function used to consume a number from the command
     * line at the current parsing point. An exception will be thrown
     * if there is not a number to be found.
     * 
     * @return The parsed number
     * @throw CommandLineSyntaxException if there is not a number to
     *   be parsed at the current location.
     */
    private int parseNumber() 
        throws CommandLineSyntaxException {
        
        StringBuilder number = new StringBuilder();
        
        /*
         * Allow, but skip, leading white space in the number.
         */
        skipWhiteSpace();
        while (curIdx < line.length()
                && Character.isDigit(line.charAt(curIdx))) {
            
            number.append(line.charAt(curIdx));
            ++curIdx;
        }
        
        if (number.length() == 0) {
            
            throw new CommandLineSyntaxException("Expected a number following "
                + "file descriptor duplication token '>&'", curIdx-1, line);
        }
        
        return Integer.parseInt(number.toString());
    }
    
    /**
     * Provides a very simple command line loop that allows testing
     * of the command line parsing logic.
     */
    public static void main(String argv[]) {
        
        String line;
        BufferedReader input = new
        BufferedReader(new InputStreamReader(System.in));
        Tokenizer tokenizer;
        Token token;
        
        do {
            
            System.out.print("prompt> ");
            
            try {
                
                line = input.readLine();
            }
            catch (IOException e) {
                
                break;
            }
            
            if (line != null) {
                
                tokenizer = new Tokenizer(line, ';');
                
                try {
                    
                    token = tokenizer.next();
                    while (token != null) {
                        
                        System.out.print(token.getClass().getName());
                        System.out.print("=[");
                        System.out.print(token.toString());
                        System.out.println("]");
                        
                        token = tokenizer.next();
                    }
                }
                catch (CommandLineSyntaxException e) {
                    
                    System.err.println("Position " 
                        + e.getPosition() + ": " + e.getMessage());
                }
            }
        }
        while (line != null 
                && "quit".equals(line) == false
                && "exit".equals(line) == false);
    }
}
