package com.im.njams.sdk.client;

import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.njams.NjamsSender;
import com.im.njams.sdk.configuration.Configuration;

public class CleanTracepointsTaskEntry {
    public final NjamsMetadata instanceMetadata;
    public Configuration configuration;
    public NjamsSender njamsSender;

    public CleanTracepointsTaskEntry(NjamsMetadata instanceMetadata, Configuration configuration, NjamsSender njamsSender) {
        this.instanceMetadata = instanceMetadata;
        this.configuration = configuration;
        this.njamsSender = njamsSender;
    }
}
