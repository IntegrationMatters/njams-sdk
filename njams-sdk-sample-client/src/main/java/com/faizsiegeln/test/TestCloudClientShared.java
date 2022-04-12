/* 
 * Copyright (c) 2020 Faiz & Siegeln Software GmbH
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

import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.cloud.CloudConstants;
import com.im.njams.sdk.logmessage.Activity;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

class MyThread extends Thread {

    String name;

    public MyThread(String name) {
        this.name = name;
    }

    public void run() {
        try {
            Njams njams = TestCloudClientShared.createNjamsClient(name);
            ProcessModel processModel = TestCloudClientShared.createProcess(njams);

            njams.start();

            int i = 0;

            while (i++ < 10000) {
                TestCloudClientShared.createJob(processModel);
                Thread.sleep(10000);
            }
            Thread.sleep(30000);
            njams.stop();

        } catch (Exception e) {
            // Throwing an exception 
            System.out.println("Exception is caught");
        }
    }
}

public class TestCloudClientShared {

    public static void main(String[] args) throws InterruptedException {

        int n = 3; // Number of threads 
        for (int i = 0; i < n; i++) {
            MyThread object = new MyThread("Client" + i);
            object.start();
        }
    }

    public static Njams createNjamsClient(String path) {
        String technology = "sdk4";

        //Specify a client path. This path specifies where your client instance will be visible in the object tree.
        Path clientPath = new Path(path, "Client", "Test");

        //Create communicationProperties, which specify how your client will communicate with the server
        //Create client settings and add the properties
        Settings config = getCloudProperties();

        //Instantiate client for first application
        Njams njams = new Njams(clientPath, "1.0.0", technology, config);

        //add custom image for your technology
        njams.addImage(technology, "images/njams_java_sdk_process_step.png");

        //add custom images for your activites
        njams.addImage("startType", "images/njams_java_sdk_process_start.png");
        njams.addImage("stepType", "images/njams_java_sdk_process_step.png");
        njams.addImage("endType", "images/njams_java_sdk_process_end.png");

        return njams;
    }

    public static ProcessModel createProcess(Njams njams) {
        /**
         * Creating a process by adding a ProcessModel
         */
        //Specify a process path, which is relative to the client path
        Path processPath = new Path("Processes", "SimpleProcess");

        //Create an new empty process model
        ProcessModel process = njams.createProcess(processPath);

        //start the model with a start activity by id, name and type, where type should match one of your previously registered images
        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);
        //step to the next activity
        ActivityModel logModel = startModel.transitionTo("log", "Log", "stepType");
        //step to the end activity
        ActivityModel endModel = logModel.transitionTo("end", "End", "endType");

        return process;
    }

    public static void createJob(ProcessModel process) {
        //Create a job from a previously created ProcessModel
        Job job = process.createJob();

        // Starts the job, i.e., sets the according status, job start date if not set before, and flags the job to begin flushing.
        job.start();

        //Create the start activity from the previously creates startModel
        Activity start = job.createActivity(process.getActivity("start")).build();
        start.processInput(createDataSize(900000));
        start.processOutput("startOutput");

        //step to the next activity from the previous one.
        Activity log = start.stepTo(process.getActivity("log")).build();
        log.processInput("logInput");
        log.processOutput("logOutput");

        //step to the end
        Activity end = log.stepTo(process.getActivity("end")).build();
        end.processInput("endInput");
        end.processOutput("endOutput");

        //End the job, which will flush all previous steps into a logmessage wich will be send to the server
        job.end();
    }

    public static String createDataSize(int msgSize) {
        StringBuilder sb = new StringBuilder(msgSize);
        for (int i = 0; i < msgSize; i++) {
            sb.append('a');
        }
        return sb.toString();
    }

    private static Settings getCloudProperties() {
        Settings communicationProperties = new Settings();
        communicationProperties.put(CommunicationFactory.COMMUNICATION, CloudConstants.NAME);
        communicationProperties.put(CloudConstants.ENDPOINT, "<cloud url>");
        communicationProperties.put(CloudConstants.COMMANDENDPOINT, "<command endpoint>");
        communicationProperties.put(CloudConstants.APIKEY, "<cloud apikey>");
        communicationProperties.put(CloudConstants.CLIENT_INSTANCEID, "<cloud client instance>");
        communicationProperties.put(CloudConstants.CLIENT_CERTIFICATE, "<cloud client certificate>");
        communicationProperties.put(CloudConstants.CLIENT_PRIVATEKEY, "<cloud client privatekey>");
        return communicationProperties;
    }

}
