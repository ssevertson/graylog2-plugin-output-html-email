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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.graylog2.plugin.GraylogServer;
import org.graylog2.plugin.logmessage.LogMessage;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.outputs.MessageOutputConfigurationException;
import org.graylog2.plugin.outputs.OutputStreamConfiguration;
import org.graylog2.plugin.streams.Stream;

/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public class EmailOutput implements MessageOutput {

    private static final String NAME = "Email output";
    
    private Map<String, String> configuration;
    
    public static final Set<String> REQUIRED_FIELDS = new HashSet<String>() {{ 
        add("hostname");
        add("port");
        add("use_tls");
        add("use_auth");
        add("from_email");
        add("from_name");
    }};
    
    public void initialize(Map<String, String> configuration) throws MessageOutputConfigurationException {
        this.configuration = configuration;
        checkConfiguration();
    }
    
    public void write(List<LogMessage> messages, OutputStreamConfiguration streamConfiguration, GraylogServer server) throws Exception {
        for (LogMessage msg : messages) {
            for (Stream stream : msg.getStreams()) {
                Set<Map<String, String>> configuredOutputs = streamConfiguration.get(stream.getId());
                for (Map<String, String> config : configuredOutputs) {
                    sendMail(msg, config.get("receiver"), config.get("subject"));
                }
            }
        }
    }

    public Map<String, String> getRequestedConfiguration() {
        Map<String, String> config = new HashMap<String, String>();

        config.put("from_email", "Email address of sender");
        config.put("from_name", "Name of sender");
        config.put("hostname", "SMTP Hostname");
        config.put("port", "SMTP Port");
        config.put("use_tls", "Use TLS? (true/false)");
        config.put("use_auth", "Use authentication? (true/false)");
        config.put("username", "SMTP username");
        config.put("password", "SMTP password");
        
        return config;
    }
    
    public Map<String, String> getRequestedStreamConfiguration() {
        Map<String, String> config = new HashMap<String, String>();
        
        config.put("receiver", "Receiver email address");
        config.put("subject", "Email subject");
        
        return config;
    }

    public String getName() {
        return NAME;
    }
    
    private void checkConfiguration() throws MessageOutputConfigurationException {
        for (String field : REQUIRED_FIELDS) {
            if (!configSet(configuration, field)) { throw new MessageOutputConfigurationException("Missing configuration option: " + field); }
        }
        
        if (configuration.get("use_auth").equals("true")) {
            if (!configSet(configuration, "username")) { throw new MessageOutputConfigurationException("Missing configuration option: username"); }
            if (!configSet(configuration, "password")) { throw new MessageOutputConfigurationException("Missing configuration option: password"); }
        }
    }
    
    private boolean configSet(Map<String, String> target, String key) {
        return target != null && target.containsKey(key)
                && target.get(key) != null && !target.get(key).isEmpty();
    }

    private void sendMail(LogMessage msg, String receiver, String subject) throws EmailException {
        if (receiver == null || receiver.isEmpty()) { throw new EmailException("Missing configuration: receiver"); }
        
        if (subject == null || subject.isEmpty()) { throw new EmailException("Missing configuration: subject"); }
        
        SimpleEmail email = new SimpleEmail();

        email.setHostName(configuration.get("hostname"));
        email.setSmtpPort(Integer.parseInt(configuration.get("port")));

        if (configuration.get("use_auth").equals("true")) {
            email.setAuthentication(configuration.get("username"), configuration.get("password"));
            if (configuration.get("use_tls").equals("true")) {
                email.setTLS(true);
            }
        }

        email.setFrom(configuration.get("from_email"), configuration.get("from_name"));

        
        email.addTo(receiver);

        String subjectPrefix = configuration.get("subject_prefix");

        if (subjectPrefix != null && !subjectPrefix.isEmpty()) {
            subject = subjectPrefix + " " + subject;
        }

        email.setSubject(subject);
        email.setMsg(buildEmailText(msg));
        email.send();
    }
 
    private String buildEmailText(LogMessage msg) {
        StringBuilder sb = new StringBuilder();

        sb.append(msg.getShortMessage());
        
        if (msg.getFullMessage() != null) {
            sb.append("\n\n").append(msg.getFullMessage());
        }

        sb.append("\n\n------\n\n").append(msg.toString());
        
        return sb.toString();
    }
    
}
