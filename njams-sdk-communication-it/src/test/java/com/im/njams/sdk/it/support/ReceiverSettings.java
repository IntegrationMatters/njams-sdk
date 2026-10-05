package com.im.njams.sdk.it.support;

import java.util.Properties;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Settings shared by the receiver ITs. The connection goes through the Toxiproxy proxies, so faults injected there
 * affect sender and receiver alike.
 */
public final class ReceiverSettings {
    /** Commands topic for {@code PROPERTY_JMS_DESTINATION=njams} (the receiver's topic is destination + ".commands"). */
    public static final String COMMANDS_TOPIC = "njams.commands";

    private ReceiverSettings() {
    }

    /** JMS settings through the JMS proxy, discard policy {@code none}. */
    public static ClientSettings jms(DockerEnvironment env) {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams");
        env.configureJms(settings);
        env.disableMessageDiscarding(settings);
        return settings;
    }

    /** HTTP settings through the HTTP proxy. */
    public static ClientSettings http(DockerEnvironment env) {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        return settings;
    }
}
