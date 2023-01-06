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
package com.faizsiegeln.test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.logmessage.Activity;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.logmessage.SubProcessActivity;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.SubProcessActivityModel;
import com.im.njams.sdk.settings.Settings;

/**
 * This is a simple sample client, which creates a simple process with three
 * steps.
 *
 * @author pnientiedt
 */
public class SubProcessClient {

    public static void main(String[] args) throws InterruptedException {

        String technology = "sdk4";

        //Specify a client path. This path specifies where your client instance will be visible in the object tree.
        Path clientPath = new Path("SDK4", "Client", "SubProcess");

        //Create communicationProperties, which specify how your client will communicate with the server
        //Settings settings = getJmsProperties();
        Settings settings = getHttpProperties();

        //Instantiate client for first application
        Njams njams = new Njams(clientPath, "1.0.0", technology, settings);

        //add custom image for your technology
        njams.addImage(technology, "images/njams_java_sdk_process_step.png");
        //add custom images for your activites
        njams.addImage("startType", "images/njams_java_sdk_process_start.png");
        njams.addImage("stepType", "images/njams_java_sdk_process_step.png");
        njams.addImage("endType", "images/njams_java_sdk_process_end.png");

        /**
         * Creating a process by adding a ProcessModel
         */
        //Specify a process path, which is relative to the client path
        Path processPath = new Path("Processes", "TheProcess");

        //Create an new empty process model for the main process
        ProcessModel process = njams.createProcess(processPath);

        //start model
        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);

        //step to subprocess caller
        SubProcessActivityModel subProcessActivityModel = startModel.transitionToSubProcess("subProcess", "SubProcess", "stepType");

        //step to end
        ActivityModel endModel = subProcessActivityModel.transitionTo("end", "End", "endType");

        //Create a new process model for the subprocess
        Path subProcessPath = new Path("PROCESSES", "SubProcess");
        ProcessModel subProcess = njams.createProcess(subProcessPath);
        ActivityModel subProcessStartModel = subProcess.createActivity("subProcessstart", "Start", "startType");
        subProcessStartModel.setStarter(true);
        ActivityModel subProcessLogModel = subProcessStartModel.transitionTo("subProcesslog", "Log", "stepType");
        ActivityModel subProcessEndModel = subProcessLogModel.transitionTo("subProcessend", "End", "endType");

        //set the subprocess processmodel on the subProcessActivityModel
        subProcessActivityModel.setSubProcess(subProcess);
        //subProcessActivityModel.setSubProcess(subProcess.getName(), subProcess.getPath());

        // Start client and flush resources, which will create a projectmessage to send all resources to the server
        njams.start();

        /**
         * Running a process by creating a job
         */
        //Create a job for the main process
        Job job = process.createJob();

        // Starts the job, i.e., sets the according status, job start date if not set before, and flags the job to begin flushing.
        job.start();

        Activity start = job.createActivity(startModel).build();
        start.processInput("testdata");
        start.processOutput("testdata");

        SubProcessActivity subProcessCaller = start.stepToSubProcess(subProcessActivityModel).build();
        subProcessCaller.processInput("testdata");
        subProcessCaller.processOutput("testdata");

        //start the subprocess by creating a child
        Activity subProcessStart = subProcessCaller.createChildActivity(subProcessStartModel).build();
        subProcessStart.processInput("testdata");
        subProcessStart.processOutput("testdata");
        Activity subProcessLog = subProcessStart.stepTo(subProcessLogModel).build();
        subProcessLog.processInput("testdata");
        subProcessLog.processOutput("testdata");
        Activity subProcessEnd = subProcessLog.stepTo(subProcessEndModel).build();
        subProcessEnd.processInput("testdata");
        subProcessEnd.processOutput("testdata");

        Activity end = subProcessCaller.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");

        //End the job, which will flush all previous steps into a logmessage wich will be send to the server
        job.end();

        Thread.sleep(1000);

        //If you are finished with processing or the application goes down, stop the client...
        njams.stop();
    }

    private static Settings getJmsProperties() {
        Settings communicationProperties = new Settings();

        //Use this if your nJAMS Server version is < 5.1
        //communicationProperties.put(Settings.PROPERTY_USE_DEPRECATED_PATH_FIELD_FOR_SUBPROCESSES, "true");

        communicationProperties.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_USERNAME, "njams");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_PASSWORD, "njams");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams.endurance");
        //optional: if you want to use a topic for commands not following the name of the other destinations, specify it here
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION, "njams4.dev.phillip.commands");
        return communicationProperties;
    }

    private static Settings getHttpProperties() {
        Settings communicationProperties = new Settings();
        communicationProperties.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        communicationProperties.put("njams.sdk.communication.http.base.url",
                "http://localhost:8080/njams/");
        communicationProperties.put("njams.sdk.communication.http.dataprovider.prefix", "sdk");
        communicationProperties.put("njams.client.sdk.sharedcommunications", "false");
        return communicationProperties;
    }
}
