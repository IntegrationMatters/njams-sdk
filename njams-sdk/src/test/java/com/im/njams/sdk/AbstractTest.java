/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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
 */
package com.im.njams.sdk;

import com.faizsiegeln.njams.messageformat.v4.common.SubProcess;
import com.faizsiegeln.njams.messageformat.v4.logmessage.Predecessor;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.connectable.sender.TestSender;
import com.im.njams.sdk.logmessage.Activity;
import com.im.njams.sdk.logmessage.ActivityImpl;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.logmessage.JobImpl;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

import java.util.HashMap;
import java.util.Map;

//import com.im.njams.sdk.communication.connectable.sender.TestSender;

/**
 * This class is a helper class for all test classes that need some jobs or activities.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.6
 */
public abstract class AbstractTest {

    //The name of the ProcessPath
    public static final String PROCESSPATHNAME = "PROCESSES";
    //The default activityModel id
    public static final String ACTIVITYMODELID = "act";

    public static final String CLIENTVERSION = "TEST";

    public static final String CATEGORY = "SDK4";

    //The njams instance
    protected static Njams njams;

    //The default processModel
    protected static ProcessModel process;

    /**
     * This constructor creates the njams instance with path SDK4-TEST-PROCESSES.
     * The TestSender and TestReceiver are used as communication devices.
     */
    public AbstractTest(){
        this(TestSender.getSettings());
    }

    /**
     * This constructor creates the njams instance with path SDK4-TEST-PROCESSES.
     * Settings can be set, if a JMS connection, etc. is necessary.
     *
     * @param settings the Settings for JMS, JNDI, etc.
     */
    public AbstractTest(Settings settings) {
        Path clientPath = new Path("SDK4", "TEST");

        njams = new Njams(clientPath, CLIENTVERSION, CATEGORY, settings);
        Path processPath = new Path("PROCESSES");
        process = njams.createProcess(processPath);

        njams.start();
    }


    /**
     * This method creates(!) a job for a default ProcessModel.
     *
     * @return A JobImpl that has been created, but not started!
     */
    protected JobImpl createDefaultJob() {
        return (JobImpl) process.createJob();
    }

    /**
     * This method creates and starts a job for a default ProcessModel.
     *
     * @return A JobImpl that has been created and started!
     */
    protected JobImpl createDefaultStartedJob() {
        JobImpl defaultJob = createDefaultJob();
        defaultJob.start();
        return defaultJob;
    }

    /**
     * This method creates a default Activity to the given job. This Activity consists
     * of a default ActivityModel with id = ACTIVITYMODELID.
     *
     * @param job the job where the activity will be safed.
     * @return the Activity that is created.
     */
    protected Activity createDefaultActivity(Job job) {
        return job.createActivity(getDefaultActivityModel()).build();
    }

    /**
     * This method creates a default Activity to the default job. See {@link #createDefaultStartedJob() createDefaultStartedJob()} and
     * {@link #createDefaultActivity(Job) createDefaultActivity(Job)}.
     *
     * @return an Activity to the default job.
     */
    protected Activity getDefaultActivity() {
        JobImpl defaultStartedJob = this.createDefaultStartedJob();
        return this.createDefaultActivity(defaultStartedJob);
    }

    /**
     * This method creates a default ActivityModel to the default ProcessModel.
     *
     * @return It returns the ActivityModel with id = ACTIVITYMODELID, name = "Act" and
     * type = null
     */
    private ActivityModel getDefaultActivityModel() {
        ActivityModel model = process.getActivity(ACTIVITYMODELID);
        if (model == null) {
            model = process.createActivity(ACTIVITYMODELID, "Act", null);
        }
        return model;
    }

    /**
     * This method creates an Activity and sets static data to all the fields
     * that are needed for the MessageFormat's Activity.
     *
     * @param job the job on which the activity is created.
     * @return a fully filled activity.
     */
    protected ActivityImpl createFullyFilledActivity(JobImpl job) {
        ActivityImpl act = (ActivityImpl) job.createActivity("id").build();
        act.setInput("SomeInput");
        act.setOutput("SomeOutput");
        act.setMaxIterations(5L);
        act.setParentInstanceId("SomeParent");
        act.setEventStatus(2);
        act.setEventMessage("SomeEventMessage");
        act.setEventCode("SomeEventCode");
        act.setEventPayload("SomeEventPayload");
        act.setStackTrace("SomeStackTrace");
        act.setStartData("SomeStartData");
        act.addPredecessor("SomeKey", "SomeValue");
        //Set a SubProcess
        SubProcess subProcess = new SubProcess();
        subProcess.setLogId("SomeSubProcessLogId");
        subProcess.setName("SomeSubProcessName");
        subProcess.setPath("SomeSubProcessPath");
        act.setSubProcess(subProcess);
        //Set one Predecessor
        Predecessor pre = new Predecessor();
        pre.setFromInstanceId("SomePredecessorInstanceId");
        pre.setModelId("SomeModelId");
        act.addPredecessor("SomeModelId", "SomeTransitionModelId");
        //Set a set of attributes (with one attribute) and add one more with addAttribute().
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put("SomeActAttribute1", "SomeValueForAttribute1");
        act.setAttributes(hashMap);
        act.addAttribute("SomeActAttribute2", "SomeValueForAttribute2");
        return act;
    }
}
