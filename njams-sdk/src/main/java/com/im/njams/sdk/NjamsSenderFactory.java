package com.im.njams.sdk;

import com.im.njams.sdk.communication.CompositeSender;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.metadata.NjamsMetadata;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

public class NjamsSenderFactory {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsSenderFactory.class);

    public static NjamsSender getNjamsSender(Settings njamsSettings, NjamsMetadata njamsMetadata) {
        Sender sender;
        if ("true".equalsIgnoreCase(njamsSettings.getProperty(Settings.PROPERTY_SHARED_COMMUNICATIONS))) {
            LOG.debug("Using shared sender pool for {}", njamsMetadata.getClientPath());
            sender = CompositeSender.takeSharedSender(njamsSettings);
        } else {
            LOG.debug("Creating individual sender pool for {}", njamsMetadata.getClientPath());
            sender = new CompositeSender(njamsSettings);
        }
        return new NjamsSender(sender);
    }
}
