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

public class TestCloudClientShared {

    public static void main(String[] args) throws InterruptedException {

        Njams njamsA = createNjamsClient("ClientA");
        Njams njamsB = createNjamsClient("ClientB");
        Njams njamsC = createNjamsClient("ClientC");

        ProcessModel processModelA = createProcess(njamsA);
        ProcessModel processModelB = createProcess(njamsB);
        ProcessModel processModelC = createProcess(njamsC);

        njamsA.start();
        njamsB.start();
        njamsC.start();

        /**
         * Running a process by creating a job
         */

        int i = 0;

        boolean payload = true;
        boolean useA = true;

        while (i++ < 10000) {

            if (useA) {
                createJob(processModelA);
                createJob(processModelC);
                useA = false;

            } else {
                createJob(processModelB);
                useA = true;
            }

            Thread.sleep(10000);
        }

        //If you are finished with processing or the application goes down, stop the client...
        Thread.sleep(30000);
        njamsA.stop();
        njamsB.stop();
        njamsC.stop();
    }

    private static Njams createNjamsClient(String path) {
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

    private static ProcessModel createProcess(Njams njams) {
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

    private static void createJob(ProcessModel process) {
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

    private static String createDataSize(int msgSize) {
        StringBuilder sb = new StringBuilder(msgSize);
        for (int i = 0; i < msgSize; i++) {
            sb.append('a');
        }
        return sb.toString();
    }

    private static Settings getCloudProperties() {
        Settings communicationProperties = new Settings();
        communicationProperties.put(CommunicationFactory.COMMUNICATION, CloudConstants.NAME);
        communicationProperties.put(CloudConstants.ENDPOINT, "ingest.dev.njams.cloud");
        communicationProperties.put(CloudConstants.APIKEY,
                "/Users/lukas/Downloads/njams_hcpqjtw_certificate/661ab6fea1-api.key");
        communicationProperties.put(CloudConstants.CLIENT_INSTANCEID,
                "/Users/lukas/Downloads/njams_hcpqjtw_certificate/661ab6fea1-instanceId");
        communicationProperties.put(CloudConstants.CLIENT_CERTIFICATE,
                "/Users/lukas/Downloads/njams_hcpqjtw_certificate/661ab6fea1-certificate.pem");
        communicationProperties.put(CloudConstants.CLIENT_PRIVATEKEY,
                "/Users/lukas/Downloads/njams_hcpqjtw_certificate/661ab6fea1-private.pem.key");

        communicationProperties.put(Settings.PROPERTY_USE_DEPRECATED_PATH_FIELD_FOR_SUBPROCESSES, "false");
        communicationProperties.put("njams.client.sdk.sharedcommunications", "true");

        return communicationProperties;
    }

}
