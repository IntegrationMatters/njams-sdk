/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 *
 */

package com.im.njams.sdk.njams.jobs;

import com.im.njams.sdk.njams.NjamsConfiguration;
import com.im.njams.sdk.njams.NjamsFeatures;
import com.im.njams.sdk.njams.NjamsJobs;
import com.im.njams.sdk.njams.NjamsSender;
import com.im.njams.sdk.njams.NjamsSerializers;
import com.im.njams.sdk.njams.NjamsState;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.layout.ProcessModelLayouter;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;
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

        final Path processPath = new Path("PROCESSES");
        return new ProcessModel(processPath, njamsJobs, NOT_USED_PROCESS_NJAMS_METADATA,
            NOT_USED_SERIALIZERS, NOT_USED_CONFIGURATION, new Settings(), NOT_USED_PROCESSDIAGRAM_FACTORY,
            NOT_USED_PROCESS_MODEL_LAYOUTER, NOT_USED_NJAMS_SENDER);
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