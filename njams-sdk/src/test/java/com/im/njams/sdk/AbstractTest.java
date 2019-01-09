/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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

import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.logmessage.Activity;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.logmessage.JobImpl;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

/**
 * This class is a helper class for all test classes that need some jobs or activities.
 * 
 * @author krautenberg@integrationmatters.com
 * @version 4.0.4
 */
public abstract class AbstractTest {

    //The name of the ProcessPath
    public static final String PROCESSPATHNAME = "PROCESSES";
    //The default activityModel id
    public static final String ACTIVITYMODELID = "act";

    //The njams instance
    protected static Njams njams;

    //The default processModel
    private static ProcessModel process;
    
    /**
     * This constructor creates the njams instance with path SDK4-TEST-PROCESSES.
     * Empty Settings were set.
     */
    public AbstractTest(){
        this(new Settings());
    }
    /**
     * This constructor creates the njams instance with path SDK4-TEST-PROCESSES.
     * Settings can be set, if a JMS connection, etc. is necessary.
     * @param settings the Settings for JMS, JNDI, etc.
     */
    public AbstractTest(Settings settings){
        Path clientPath = new Path("SDK4", "TEST");

        njams = new Njams(clientPath, "TEST", "sdk4", settings);
        Path processPath = new Path("PROCESSES");
        process = njams.createProcess(processPath);
    }

    /**
     * This method creates(!) a job for a default ProcessModel.
     * @return A JobImpl that has been created, but not started.
     */
    protected JobImpl createDefaultJob() {
        return (JobImpl) process.createJob();
    }
  
    /**
     * This method creates a default Activity to the given job. This Activity consists
     * of a default ActivityModel with id = ACTIVITYMODELID. 
     * @param job the job where the activity will be safed.
     * @return the Activity that is created.
     */
    protected Activity createDefaultActivity(Job job){
        return job.createActivity(getDefaultActivityModel()).build();
    }
    
    /**
     * This method creates a default ActivityModel to the default ProcessModel.
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
}
