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
package org.graylog2.emailoutput.output.html;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.graylog2.emailoutput.output.EmailLayout;
import org.graylog2.emailoutput.output.LogMessageUtil;
import org.graylog2.plugin.logmessage.LogMessage;

public class HtmlEmailLayout implements EmailLayout
{
    private static final String CONTENT_TYPE = "text/html";
    private static final String LINK_TEXT = "View in Graylog2";
    private static final String SEPARATOR = "<hr style=\"height:1px;border:0px;color:#828181;background-color:#828181;\"/>\n";
    
    // Work around thread safety issues, while still reusing instances
    private static ThreadLocal<DateFormat> ISO_8601_DATE_FORMAT = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS z");
        }
    };
    
    private String subjectPrefix;
    private String webURL;
    
    public void initialize(Map<String, String> pluginConfiguration) {
        this.subjectPrefix = pluginConfiguration.get("subject_prefix");
        this.webURL = pluginConfiguration.get("web_interface_url");
    }
    
    public String getContentType() {
        return CONTENT_TYPE;
    }
    
    public String getSubject(LogMessage message, Map<String, String> streamConfiguration) {
        String subject = streamConfiguration.get("subject");
        
        if (subjectPrefix != null && !subjectPrefix.isEmpty()) {
            subject = subjectPrefix + " " + subject;
        }
        
        return subject;
    }
    
    public String formatMessageBody(LogMessage msg, Map<String, String> streamConfiguration) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>\n").append("<body>\n");
        
        sb.append("<table>");
        appendHTMLKeyValue(sb, "Date", getISO8601FormattedTimestamp(msg));
        appendHTMLKeyValue(sb, "Level", LogMessageUtil.getLevelFullName(msg.getLevel()));
        appendHTMLKeyValue(sb, "Host", msg.getHost());
        appendHTMLKeyValue(sb, "Facility", msg.getFacility());
        sb.append("</table>");
        sb.append(SEPARATOR);
        
        String messageURL = getMessageURL(msg);
        if (null != messageURL) {
            sb.append("<a href=\"").append(messageURL).append("\">").append(LINK_TEXT).append("</a>\n");
            sb.append(SEPARATOR);
        }
        
        sb.append(HtmlUtil.encode(getMessageText(msg))).append("<br/>\n");
        sb.append(SEPARATOR);
        
        Pattern fieldPattern = compilePattern(streamConfiguration.get("fields"));
        SortedMap<String, Object> fieldValues = buildFieldValuesMap(msg, fieldPattern);
        if (!fieldValues.isEmpty()) {
            sb.append("<table>");
            for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
                appendHTMLKeyValue(sb, entry.getKey(), entry.getValue());
            }
            sb.append("</table>");
            sb.append(SEPARATOR);
        }
        
        sb.append("</body>").append("</html>\n");
        
        return sb.toString();
    }
    
    private String getMessageURL(LogMessage msg) {
        return (null == webURL || webURL.isEmpty())
        		? null
        		: webURL + "/messages/" + msg.getId();
    }
    
    private Pattern compilePattern(String string) {
        return (null == string || string.isEmpty())
                ? null
                : Pattern.compile(string);
    }
    
    private SortedMap<String, Object> buildFieldValuesMap(LogMessage msg, Pattern fieldPattern) {
        SortedMap<String, Object> fieldValues = new TreeMap<String, Object>();
        if (fieldPattern != null) {
            Map<String, Object> additionalData = msg.getAdditionalData();
            for (String field : additionalData.keySet()) {
                if (fieldPattern.matcher(field).matches()) {
                    Object value = additionalData.get(field);
                    fieldValues.put(field, value);
                }
            }
        }
        return fieldValues;
    }
    
    private String getMessageText(LogMessage msg) {
        // Prefer full message, if available
        return (null != msg.getFullMessage())
                ? msg.getFullMessage()
                : msg.getShortMessage();
    }
    
    private String getISO8601FormattedTimestamp(LogMessage msg) {
        double createdAt = msg.getCreatedAt();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis((long) (createdAt * 1000));
        return ISO_8601_DATE_FORMAT.get().format(cal.getTime());
    }
    

    private void appendHTMLKeyValue(StringBuilder sb, String key, Object value) {
        sb.append("<tr>\n");
        sb.append("<th align=\"left\">").append(key).append("</th>\n");
        sb.append("<td>").append(HtmlUtil.encode(String.valueOf(value))).append("</td>\n");
        sb.append("</tr>\n");
    }
}
