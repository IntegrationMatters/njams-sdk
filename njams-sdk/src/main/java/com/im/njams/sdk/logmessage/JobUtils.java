package com.im.njams.sdk.logmessage;

import com.im.njams.sdk.NjamsJobs;
import com.im.njams.sdk.NjamsMetadata;
import com.im.njams.sdk.NjamsSerializers;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.settings.Settings;

public class JobUtils {

    public final Settings settings;
    public final NjamsMetadata instanceMetaData;
    public final Configuration configuration;
    public final Sender sender;
    public final NjamsJobs njamsJobs;
    public final NjamsSerializers njamsSerializers;

    public JobUtils(NjamsJobs njamsJobs, NjamsMetadata instanceMetaData, NjamsSerializers njamsSerializers, Configuration configuration, Settings settings, Sender sender){
        this.njamsJobs = njamsJobs;
        this.instanceMetaData = instanceMetaData;
        this.njamsSerializers = njamsSerializers;
        this.configuration = configuration;
        this.settings = settings;
        this.sender = sender;
    }
}
