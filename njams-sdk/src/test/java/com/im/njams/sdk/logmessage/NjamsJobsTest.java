package com.im.njams.sdk.logmessage;

import com.im.njams.sdk.NjamsConfiguration;
import com.im.njams.sdk.NjamsSender;
import com.im.njams.sdk.metadata.NjamsMetadata;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.ProcessModelUtils;
import com.im.njams.sdk.model.layout.ProcessModelLayouter;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;
import com.im.njams.sdk.serializer.NjamsSerializers;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class NjamsJobsTest {

    private static final NjamsMetadata NOT_USED_JOB_NJAMS_METADATA = null;
    private static final NjamsMetadata NOT_USED_PROCESS_NJAMS_METADATA = null;
    private static final NjamsSerializers NOT_USED_SERIALIZERS = null;
    private static final NjamsConfiguration NOT_USED_CONFIGURATION = null;
    private static final ProcessDiagramFactory NOT_USED_PROCESSDIAGRAM_FACTORY = null;
    private static final ProcessModelLayouter NOT_USED_PROCESS_MODEL_LAYOUTER = null;
    private static final NjamsSender NOT_USED_NJAMS_SENDER = null;
    private NjamsState njamsState;
    private ProcessModel processModel;

    @Before
    public void init(){
        njamsState = new NjamsState();

        processModel = getPreparedProcessModel(njamsState);

    }

    private ProcessModel getPreparedProcessModel(NjamsState njamsState) {
        final NjamsFeatures njamsFeatures = new NjamsFeatures();
        final Settings jobSettings = new Settings();
        final NjamsJobs njamsJobs = new NjamsJobs(NOT_USED_JOB_NJAMS_METADATA, njamsState, njamsFeatures, jobSettings);
        final ProcessModelUtils processModelUtils = new ProcessModelUtils(njamsJobs, NOT_USED_PROCESS_NJAMS_METADATA,
            NOT_USED_SERIALIZERS, NOT_USED_CONFIGURATION, new Settings(), NOT_USED_PROCESSDIAGRAM_FACTORY,
            NOT_USED_PROCESS_MODEL_LAYOUTER, NOT_USED_NJAMS_SENDER);

        final Path processPath = new Path("PROCESSES");
        return new ProcessModel(processPath, processModelUtils);
    }

    @Test
    public void njamsStateIsStopped_aModelCannotCreateANewJob_anExceptionIsThrown() {
        njamsState.stop();

        NjamsSdkRuntimeException njamsSdkRuntimeException = assertThrows(NjamsSdkRuntimeException.class,
            processModel::createJob);
        assertThat(njamsSdkRuntimeException.getMessage(), is(equalTo("The instance needs to be started first!")));
    }

    @Test
    public void njamsStateIsStarted_aNewJobCanBeCreated() {
        njamsState.start();

        ProcessModel model = getPreparedProcessModel(njamsState);
        assertNotNull(model.createJob());
    }
}