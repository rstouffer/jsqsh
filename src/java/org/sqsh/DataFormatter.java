/*
 * Copyright (C) 2007 by Scott C. Gray
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, write to the Free Software Foundation, 675 Mass Ave,
 * Cambridge, MA 02139, USA.
 */
package org.sqsh;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.sqsh.format.BitFormatter;
import org.sqsh.format.BlobFormatter;
import org.sqsh.format.BooleanFormatter;
import org.sqsh.format.ByteFormatter;
import org.sqsh.format.ClobFormatter;
import org.sqsh.format.NumberFormatter;
import org.sqsh.format.DateFormatter;
import org.sqsh.format.Unformatter;
import org.sqsh.format.XMLFormatter;

/**
 * Helper class that is capable of formatting the value of a column into
 * a string according to sqsh configuration variables.
 */
public class DataFormatter {
    
    private String nullFormat = "[NULL]";
    private String dateFormat = "dd-MMM-yyyy";
    private int maxDateWidth = -1;
    private String datetimeFormat = "MMM dd yyyy HH:mm:ss";
    private int maxDatetimeWidth = -1;
    private String timeFormat = "HH:mm";
    private int maxTimeWidth = -1;;
    
    /*
     * Number of decimal places of precision to use when displaying
     * floating point values (except for BigDecimal).
     */
    private int scale = 5;
    
    /* ====================================================================
     *                           NULL
     * ==================================================================== */
    
    /**
     * Sets the string that will be used to represent a null value.
     * 
     * @param str the string that will be used to represent a null value.
     */
    public void setNull(String str) {
        
        this.nullFormat = str;
    }
    
    /**
     * Returns the representation of null.
     * 
     * @return the representation of null.
     */
    public String getNull() {
        
        return nullFormat;
    }
    
    /**
     * Returns the number of characters required to display a NULL.
     * 
     * @return the number of characters required to display a NULL.
     */
    public int getNullWidth() {
        
        return nullFormat.length();
    }
    
    /* ====================================================================
     *                              DATE
     * ==================================================================== */
    
    /**
     * @return the dateFormat
     */
    public String getDateFormat () {
    
        return dateFormat;
    }
    
    /**
     * @param dateFormat the dateFormat to set
     */
    public void setDateFormat (String dateFormat) {
    
        this.dateFormat = dateFormat;
        maxDateWidth = -1;
    }
    
    public Formatter getDateFormatter() {
        
        if (maxDateWidth == -1) {
            
            maxDateWidth = getMaxDateWidth(dateFormat);
        }
        
        return new DateFormatter(dateFormat, maxDateWidth);
    }
    
    /* ====================================================================
     *                              TIME
     * ==================================================================== */
    
    /**
     * @return the timeFormat
     */
    public String getTimeFormat () {
    
        return timeFormat;
    }
    
    /**
     * @param dateFormat the dateFormat to set
     */
    public void setTimeFormat (String timeFormat) {
    
        this.timeFormat = timeFormat;
        maxTimeWidth = -1;
    }
    
    public Formatter getTimeFormatter() {
        
        if (maxTimeWidth == -1) {
            
            maxTimeWidth = getMaxDateWidth(timeFormat);
        }
        
        return new DateFormatter(timeFormat, maxTimeWidth);
    }
    
    /* ====================================================================
     *                              DATETIME
     * ==================================================================== */
    
    /**
     * @return the dateFormat
     */
    public String getDatetimeFormat () {
    
        return datetimeFormat;
    }
    
    /**
     * @param dateFormat the dateFormat to set
     */
    public void setDatetimeFormat (String datetimeFormat) {
    
        this.datetimeFormat = datetimeFormat;
        maxDatetimeWidth = -1;
    }
    
    public Formatter getDatetimeFormatter() {
        
        if (maxDatetimeWidth == -1) {
            
            maxDatetimeWidth = getMaxDateWidth(datetimeFormat);
        }
        
        return new DateFormatter(datetimeFormat, maxDatetimeWidth);
    }
    
    /* ====================================================================
     *                    NUMBERS (REAL, FLOAT, ETC.)
     * ==================================================================== */
    
    /**
     * Sets the number of decimal digits that will be displayed when floating
     * point values are displayed. 
     * 
     * @param scale The scale to display.
     */
    public void setScale(int scale) {
        
        this.scale = scale;
    }
    
    /**
     * Returns the number of decimal digits that will be displayed when floating
     * point values are displayed. 
     * @return The scale.
     */
    public int getScale() {
        
        return scale;
    }
    
    /**
     * Returns a formatter to format Doubles.
     * @return a new formatter.
     */
    public Formatter getDoubleFormatter() {
        
        return new NumberFormatter(20, scale);
    }
    
    /**
     * Returns a formatter to format floats.
     * @return a new formatter.
     */
    public Formatter getFloatFormatter() {
        
        return new NumberFormatter(20, scale);
    }
    
    /**
     * Returns a formatter to format a bit
     * @return a new formatter.
     */
    public Formatter getBitFormatter () {
    
        return new BitFormatter();
    }
    
    /**
     * Returns a formatter to format a tinyint
     * @return a new formatter.
     */
    public Formatter getTinyIntFormatter () {
    
        return new NumberFormatter(3, 0);
    }
    
    /**
     * Returns a formatter to format a short
     * @return a new formatter.
     */
    public Formatter getShortFormatter () {
    
        return new NumberFormatter(6, 0);
    }
    
    /**
     * Returns a formatter to format integers.
     * @return a new formatter.
     */
    public Formatter getIntFormatter () {
    
        return new NumberFormatter(11, 0);
    }
    
    /**
     * Returns a formatter to format longs.
     * @return a new formatter.
     */
    public Formatter getLongFormatter () {
    
        return new NumberFormatter(21, 0);
    }
    
    /**
     * Creates a formatter for BigInt.
     * 
     * @param precision The maximum precision of the integer.
     * @return The formatter.
     */
    public Formatter getBigIntFormatter(int precision) {
        
        return new NumberFormatter(precision, 0);
    }
    
    /**
     * Creates a formatter for BigDecimal.
     * 
     * @param precision The maximum precision of the bigdecimal.
     * @return The formatter.
     */
    public Formatter getBigDecimalFormatter(int precision, int scale) {
        
        return new NumberFormatter(precision, scale);
    }
    
    /* ====================================================================
     *                             STRING
     * ==================================================================== */
    
    /**
     * Returns a formatter for formatting strings.
     * 
     * @param maxWidth The maximum allowable width of the string.
     */
    public Formatter getStringFormatter(int maxWidth) {
        
        return new Unformatter(maxWidth);
    }
    
    /**
     * Returns a formatter for formatting strings.
     */
    public Formatter getClobFormatter() {
        
        return new ClobFormatter();
    }
    
    /**
     * Returns a formatter for formatting XML.
     * 
     * @return a new formatter.
     */
    public Formatter getXMLFormatter() {
        
        return new XMLFormatter();
    }
        
    /* ====================================================================
     *                             BOOLEAN
     * ==================================================================== */
    
    /**
     * Returns a formatter for formatting strings.
     * @return The formatter.
     */
    public Formatter getBooleanFormatter() {
        
        return new BooleanFormatter();
    }
    
    /**
     * Returns a formatter for formatting blobs.
     * @return The formatter.
     */
    public Formatter getBlobFormatter() {
        
        return new BlobFormatter();
    }
    
    /* ====================================================================
     *                             BYTE(S)
     * ==================================================================== */
    
    /**
     * Returns a formatter for formatting bytes.
     * @param maxBytes The maximum number of bytes that could be displayed.
     * @return The formatter.
     */
    public Formatter getByteFormatter(int maxBytes) {
        
        return new ByteFormatter(maxBytes);
    }
    
    /* ====================================================================
     *                              HELPERS 
     * ==================================================================== */
    
    /**
     * Used internally to determine the maximum amount of space required by
     * a specific date/time/datetime format.
     * @param format The format to test.
     * @return The maximum width.
     */
    private int getMaxDateWidth(String format) {
        
        Calendar cal = Calendar.getInstance();
        int maxWidth = 0;
        
        
        /*
         * The following will attempt to cover all of the months in the year
         * (on the assumption the current formatter includes the month name)
         * as well as all of the days of the week and double-digit day of month
         * numbers.
         */
        for (int month = 1; month <= 12; month++) {
            
            for (int dayOfMonth = 20; dayOfMonth < 28; dayOfMonth++) {
                
                cal.set(2007, month, dayOfMonth, 23, 59, 59);
                
                int width = formatDate(format, cal.getTime()).length();
                if (width > maxWidth) {
                    
                    maxWidth = width;
                }
            }
        }
        
        return maxWidth;
    }
    
    private String formatDate(String format, Date date) {

        DateFormat fmt;
        if (format == null || format.length() == 0) {

            fmt = DateFormat.getInstance();
        }
        else {

            fmt = new SimpleDateFormat(format);
        }

        return fmt.format(date);
    }

}
