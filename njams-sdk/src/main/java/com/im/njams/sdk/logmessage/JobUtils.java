package com.im.njams.sdk.logmessage;

import com.im.njams.sdk.njams.NjamsConfiguration;
import com.im.njams.sdk.njams.NjamsJobs;
import com.im.njams.sdk.njams.NjamsSender;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.serializer.NjamsSerializers;
import com.im.njams.sdk.settings.Settings;

public class JobUtils {

    public final Settings settings;
    public final NjamsMetadata instanceMetaData;
    public final NjamsConfiguration njamsConfiguration;
    public final NjamsSender njamsSender;
    public final NjamsJobs njamsJobs;
    public final NjamsSerializers njamsSerializers;

    public JobUtils(NjamsJobs njamsJobs, NjamsMetadata instanceMetaData, NjamsSerializers njamsSerializers,
        NjamsConfiguration njamsConfiguration, Settings settings, NjamsSender njamsSender){
        this.njamsJobs = njamsJobs;
        this.instanceMetaData = instanceMetaData;
        this.njamsSerializers = njamsSerializers;
        this.njamsConfiguration = njamsConfiguration;
        this.settings = settings;
        this.njamsSender = njamsSender;
    }
}
