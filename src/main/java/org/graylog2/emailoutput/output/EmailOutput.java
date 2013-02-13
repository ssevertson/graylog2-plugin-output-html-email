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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.graylog2.emailoutput.output.html.HtmlEmailLayout;
import org.graylog2.plugin.GraylogServer;
import org.graylog2.plugin.logmessage.LogMessage;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.outputs.MessageOutputConfigurationException;
import org.graylog2.plugin.outputs.OutputStreamConfiguration;
import org.graylog2.plugin.streams.Stream;

/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 * @author Scott Severtson <ssevertson@digitalmeasures.com>
 */
public class EmailOutput implements MessageOutput {
    
    private static final String PLUGIN_NAME = "Email output";

    private static final Map<String, String> FIELDS_PLUGIN_CONFIG;
    static {
        Map<String, String> config = new LinkedHashMap<String, String>();
        config.put("from_email", "Email address of sender");
        config.put("from_name", "Name of sender");
        config.put("hostname", "SMTP Hostname");
        config.put("port", "SMTP Port");
        config.put("use_tls", "Use TLS? (true/false)");
        config.put("use_auth", "Use authentication? (true/false)");
        config.put("username", "SMTP username");
        config.put("password", "SMTP password");
        config.put("web_interface_url", "Web Interface URL (for links)");
        FIELDS_PLUGIN_CONFIG = Collections.unmodifiableMap(config);
    }
    private static final Collection<String> FIELDS_PLUGIN_REQUIRED = Collections.unmodifiableCollection(Arrays.asList( 
            "from_email",
            "from_name",
            "hostname",
            "port",
            "use_tls",
            "use_auth"));
    
    private static final Map<String, String> FIELDS_STREAM_CONFIG;
    static {
        Map<String, String> config = new LinkedHashMap<String, String>();
        config.put("receiver", "Receiver email address");
        config.put("subject", "Email subject");
        config.put("fields", "Include fields (regex)");
        FIELDS_STREAM_CONFIG = Collections.unmodifiableMap(config);
    }
    private static final Collection<String> FIELDS_STREAM_REQUIRED = Collections.unmodifiableCollection(Arrays.asList( 
            "receiver",
            "subject"));

    
    private Map<String, String> configuration;
    private Session session;
    private InternetAddress from;
    private EmailLayout layout = new HtmlEmailLayout();
    
    
    public String getName() {
        return PLUGIN_NAME;
    }
    public Map<String, String> getRequestedConfiguration() {
        return FIELDS_PLUGIN_CONFIG;
    }
    private void checkPluginConfiguration(Map<String, String> configuration) throws MessageOutputConfigurationException {
        checkRequiredFields(configuration, FIELDS_PLUGIN_REQUIRED);
        
        if (configuration.get("use_auth").equals("true")) {
            if (!configSet(configuration, "username")) { throw new MessageOutputConfigurationException("Missing configuration option: username"); }
            if (!configSet(configuration, "password")) { throw new MessageOutputConfigurationException("Missing configuration option: password"); }
        }
    }
    public Map<String, String> getRequestedStreamConfiguration() {
        return FIELDS_STREAM_CONFIG;
    }
    private void checkStreamConfiguration(Map<String, String> configuration) throws MessageOutputConfigurationException {
        checkRequiredFields(configuration, FIELDS_STREAM_REQUIRED);
    }

   private void checkRequiredFields(Map<String, String> configuration, Collection<String> requiredFields) throws MessageOutputConfigurationException {
        for (String field : requiredFields) {
            if (!configSet(configuration, field)) {
                throw new MessageOutputConfigurationException("Missing configuration option: " + field);
            }
        }
    }
    private boolean configSet(Map<String, String> target, String key) {
        return target != null && target.containsKey(key)
                && target.get(key) != null && !target.get(key).isEmpty();
    }

    
    public void initialize(Map<String, String> pluginConfiguration) throws MessageOutputConfigurationException {
        
        checkPluginConfiguration(pluginConfiguration);
        this.configuration = pluginConfiguration;
        
        this.session = JavaMailUtil.buildSession(
                pluginConfiguration.get("protocol"),
                Boolean.parseBoolean(pluginConfiguration.get("use_auth")),
                Boolean.parseBoolean(pluginConfiguration.get("use_tls")));
        
        this.from = toAddress(pluginConfiguration.get("from_email"), pluginConfiguration.get("from_name"));
        
        this.layout.initialize(pluginConfiguration);
    }

    public void write(List<LogMessage> messages, OutputStreamConfiguration streamConfiguration, GraylogServer server) throws Exception {
        
        Transport transport = null;
        try {
            for (LogMessage msg : messages) {
                for (Stream stream : msg.getStreams()) {
                    Set<Map<String, String>> configuredOutputs = streamConfiguration.get(stream.getId());
    
                    if (configuredOutputs != null && !configuredOutputs.isEmpty()) {
                        
                        for (Map<String, String> config : configuredOutputs) {
                            
                            transport = sendMessage(transport, msg, config);
                        }
                    }
                }
            }
        } finally {
            if(null != transport) {
                transport.close();
            }
        }
    }
    public Transport sendMessage(Transport transport, LogMessage message, Map<String, String> streamConfig)
            throws MessageOutputConfigurationException, NoSuchProviderException, MessagingException, IOException {
    	
        checkStreamConfiguration(streamConfig);
        
        if(null == transport) {
            transport = JavaMailUtil.buildTransport(
                    session,
                    configuration.get("hostname"),
                    Integer.parseInt(configuration.get("port")),
                    Boolean.parseBoolean(configuration.get("use_auth")),
                    configuration.get("username"),
                    configuration.get("password"));
        }
        
        MimeUtil.sendMessage(
                session,
                transport,
                from,
                toAddress(streamConfig.get("receiver")),
                layout.getSubject(message, streamConfig),
                layout.formatMessageBody(message, streamConfig),
                layout.getContentType());
        return transport;
    }


    private InternetAddress toAddress(String email, String name) throws MessageOutputConfigurationException {
        try {
            InternetAddress address = toAddress(email);
            address.setPersonal(name);
            return address;
        } catch (UnsupportedEncodingException e) {
            throw new MessageOutputConfigurationException("Could not encode name: " + name + "; " + e.getMessage());
        }
    }
    
    private InternetAddress toAddress(String email) throws MessageOutputConfigurationException {
        try {
            return new InternetAddress(email);
        } catch(AddressException e) {
            throw new MessageOutputConfigurationException("Could not parse email address: " + email + "; " + e.getMessage());
        }
    }
}
