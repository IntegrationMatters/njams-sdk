package com.im.njams.sdk;

import com.im.njams.sdk.client.NjamsMetadata;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

public class NjamsSenderFactory {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsSenderFactory.class);

    public static Sender getSender(Settings njamsSettings, NjamsMetadata njamsMetadata) {
        NjamsSender sender;
        if ("true".equalsIgnoreCase(njamsSettings.getProperty(Settings.PROPERTY_SHARED_COMMUNICATIONS))) {
            LOG.debug("Using shared sender pool for {}", njamsMetadata.clientPath);
            sender = NjamsSender.takeSharedSender(njamsSettings);
        } else {
            LOG.debug("Creating individual sender pool for {}", njamsMetadata.clientPath);
            sender = new NjamsSender(njamsSettings);
        }
        return sender;
    }
}
