package com.im.njams.sdk.model;

import com.im.njams.sdk.njams.NjamsConfiguration;
import com.im.njams.sdk.njams.NjamsSender;
import com.im.njams.sdk.njams.NjamsJobs;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.serializer.NjamsSerializers;
import com.im.njams.sdk.model.layout.ProcessModelLayouter;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;
import com.im.njams.sdk.settings.Settings;

public class ProcessModelUtils {

    public final NjamsJobs njamsJobs;
    public final NjamsMetadata instanceMetaData;
    public final NjamsSerializers njamsSerializers;
    public final NjamsConfiguration njamsConfiguration;
    public final Settings settings;
    public final ProcessModelLayouter processModelLayouter;
    public final ProcessDiagramFactory processDiagramFactory;
    public final NjamsSender njamsSender;

    public ProcessModelUtils(NjamsJobs njamsJobs, NjamsMetadata instanceMetaData, NjamsSerializers njamsSerializers,
        NjamsConfiguration njamsConfiguration,
        Settings settings, ProcessDiagramFactory processDiagramFactory, ProcessModelLayouter layouter, NjamsSender njamsSender) {
        this.njamsJobs = njamsJobs;
        this.instanceMetaData = instanceMetaData;
        this.njamsSerializers = njamsSerializers;
        this.njamsConfiguration = njamsConfiguration;
        this.settings = settings;
        this.processModelLayouter = layouter;
        this.processDiagramFactory = processDiagramFactory;
        this.njamsSender = njamsSender;
    }
}
