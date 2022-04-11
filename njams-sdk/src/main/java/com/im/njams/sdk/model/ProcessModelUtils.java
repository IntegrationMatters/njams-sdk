package com.im.njams.sdk.model;

import com.im.njams.sdk.NjamsJobs;
import com.im.njams.sdk.NjamsMetadata;
import com.im.njams.sdk.serializer.NjamsSerializers;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.model.layout.ProcessModelLayouter;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;
import com.im.njams.sdk.settings.Settings;

public class ProcessModelUtils {

    public final NjamsJobs njamsJobs;
    public final NjamsMetadata instanceMetaData;
    public final NjamsSerializers njamsSerializers;
    public final Configuration configuration;
    public final Settings settings;
    public final ProcessModelLayouter processModelLayouter;
    public final ProcessDiagramFactory processDiagramFactory;
    public final Sender sender;

    public ProcessModelUtils(NjamsJobs njamsJobs, NjamsMetadata instanceMetaData, NjamsSerializers njamsSerializers, Configuration configuration,
        Settings settings, ProcessDiagramFactory processDiagramFactory, ProcessModelLayouter layouter, Sender sender) {
        this.njamsJobs = njamsJobs;
        this.instanceMetaData = instanceMetaData;
        this.njamsSerializers = njamsSerializers;
        this.configuration = configuration;
        this.settings = settings;
        this.processModelLayouter = layouter;
        this.processDiagramFactory = processDiagramFactory;
        this.sender = sender;
    }
}
