/**
 * Copyright 2012 Lennart Koopmann <lennart@socketfeed.com>
 *
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.graylog2.emailoutput.output;

public class LogMessageUtil {

    private LogMessageUtil() {}

    public static String getLevelFullName(int level) {
        switch(level) {
            case 0:  return "Emergency";
            case 1:  return "Alert";
            case 2:  return "Critical";
            case 3:  return "Error";
            case 4:  return "Warning";
            case 5:  return "Notice";
            case 6:  return "Informational";
            case 7:  return "Debug";
            default: return "Invalid";
        }
    }    
}
