package com.im.njams.sdk.client;

import com.im.njams.sdk.NjamsMetadata;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.configuration.Configuration;

public class CleanTracepointsTaskEntry {
    public final NjamsMetadata instanceMetadata;
    public Configuration configuration;
    public Sender sender;

    public CleanTracepointsTaskEntry(NjamsMetadata instanceMetadata, Configuration configuration, Sender sender) {
        this.instanceMetadata = instanceMetadata;
        this.configuration = configuration;
        this.sender = sender;
    }
}
