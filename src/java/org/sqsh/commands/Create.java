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
package org.sqsh.commands;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.kohsuke.args4j.Option;
import org.sqsh.Command;
import org.sqsh.DatabaseCommand;
import org.sqsh.SQLTools;
import org.sqsh.SessionRedrawBufferMessage;
import org.sqsh.SqshOptions;
import org.sqsh.SQLTools.ObjectDescription;
import org.sqsh.Session;

/**
 * Implements the jsqsh \create command.
 */
public class Create
    extends Command
    implements DatabaseCommand {
    
    private static class Options
        extends SqshOptions {
        
        @Option(name="-p",usage="Print CREATE to screen, not to SQL buffer")
            public boolean printOnly = false;
     }
    
    @Override
    public SqshOptions getOptions() {
       
        return new Options();
    }

    @Override
    public int execute (Session session, SqshOptions opts)
        throws Exception {
        
        Options options = (Options) opts;
        Connection conn = session.getConnection();
        
        /*
         * Make sure the caller provided some table names.
         */
        if (options.arguments.size() != 1) {
            
            session.err.println("Use: \\create table");
            return 1;
        }
        
        /*
         * Turn the supplied object name, which could contain 
         * separate parts (e.g. db.dbo.table), into component
         * parts.
         */
        ObjectDescription obj =
            SQLTools.parseObjectName(options.arguments.get(0));
        
        try {
            
            int maxNameLength = 0;
            DatabaseMetaData meta = conn.getMetaData();
            
            String catalog = 
                (obj.getCatalog() == null ? SQLTools.getCatalog(conn) 
                        : obj.getCatalog());
            
            /*
             * We make two passes through the metadata. The first is to 
             * find out how wide the widest column name is. I do this
             * so I can create a nicely formatted statement.
             */
            ResultSet res = meta.getColumns(
                (catalog == null ? "%" : catalog),
                (obj.getSchema() == null ? "%" : obj.getSchema()),
                obj.getName(), "%");
            while (res.next()) {
                
                String name = SQLTools.quoteIdentifier(
                    res.getString("COLUMN_NAME"));
                
                if (name.length() > maxNameLength) {
                    
                    maxNameLength = name.length();
                }
            }
            
            /*
             * Now, we go back and build our output.
             */
            String linesep = System.getProperty("line.separator");
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE ").append(options.arguments.get(0))
                .append(linesep);
            sb.append('(').append(linesep);
            
            res = meta.getColumns(
                (catalog == null ? "%" : catalog),
                (obj.getSchema() == null ? "%" : obj.getSchema()),
                obj.getName(), "%");
            
            int colCount = 0;
            while (res.next()) {
                
                ++colCount;
                if (colCount > 1) {
                    
                    sb.append(',').append(linesep);
                }
                sb.append("    ");
                
                String name = SQLTools.quoteIdentifier(
                    res.getString("COLUMN_NAME"));
                sb.append(name);
                sb.append(' ');
                
                for (int i = name.length(); i < maxNameLength; i++) {
                    
                    sb.append(' ');
                }
                
                int typeId = res.getInt("DATA_TYPE");
                String typeName = res.getString("TYPE_NAME");
                
                StringBuilder typeBuf = new StringBuilder();
                typeBuf.append(typeName);
                
                /*
                 * Numeric and decimal require precision and scale.
                 */
                if (typeId == Types.NUMERIC
                        || typeId == Types.DECIMAL) {
                    
                    int scale = res.getInt("DECIMAL_DIGITS");
                    int precision = res.getInt("NUM_PREC_RADIX");
                    
                    typeBuf.append('(');
                    typeBuf.append(precision).append(", ").append(scale);
                    typeBuf.append(')');
                }
                else if (typeId == Types.CHAR
                        // || typeId == Types.NCHAR
                        || typeId == Types.BINARY
                        || typeId == Types.VARBINARY
                        || typeId == Types.VARCHAR
                        // || typeId == Types.NVARCHAR
                        || typeId == Types.LONGVARCHAR
                        // || typeId == Types.LONGNVARCHAR
                        || typeId == Types.LONGVARBINARY) {
                    
                    /*
                     * The above datatypes require a size.
                     */
                    int size = res.getInt("COLUMN_SIZE");
                    typeBuf.append('(').append(size).append(')');
                }
                
                sb.append(typeBuf);
                sb.append(' ');
                for (int i = typeBuf.length(); i < 15; i++) {
                    
                    sb.append(' ');
                }
                
                String isNullable = res.getString("IS_NULLABLE");
                if ("NO".equals(isNullable)) {
                    
                    sb.append(" NOT NULL");
                }
                else {
                    
                    sb.append(" NULL");
                }
            }
            
            sb.append(linesep);
            sb.append(')');
            
            if (options.printOnly) {
                
                session.out.println(sb.toString());
            }
            else {
                
                session.getBufferManager().getCurrent().set(sb.toString());
                throw new SessionRedrawBufferMessage();
            }
        }
        catch (SQLException e) {
            
            SQLTools.printException(session.err, e);
            return 1;
        }
        
        return 0;
    }

}