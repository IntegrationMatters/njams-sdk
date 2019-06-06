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
package com.im.test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.logmessage.Activity;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.service.factories.SettingsProxyFactory;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.settings.SettingsProvider;
import com.im.njams.sdk.settings.proxy.PropertiesFileSettingsProxy;
import java.util.Properties;

/**
 * This sample client demonstrates how to load nJAMS config from a properties
 * file.
 *
 * @author bwand
 */
public class SettingsFromFileClient {

    public static void main(String[] args) throws InterruptedException {

        String technology = "sdk4";

        // Use Properties File Settings Provider
        SettingsProxyFactory factory = new SettingsProxyFactory(PropertiesFileSettingsProxy.NAME);
        SettingsProvider provider = factory.getInstance();

        // Specifiy location of properties file to load
        Properties fileConfig = new Properties();
        if (args.length < 1) {
            fileConfig.setProperty(PropertiesFileSettingsProxy.FILE_CONFIGURATION, "target/classes/settings.properties");
        } else {
            fileConfig.setProperty(PropertiesFileSettingsProxy.FILE_CONFIGURATION, args[0]);
        }
        provider.configure(fileConfig);
        Settings settings = provider.loadSettings();

        // Specify a client path. This path specifies where your client instance will be visible in the object tree.
        Path clientPath = new Path("SDK4", "Client", "Simple");

        // Instantiate client for first application
        Njams njams = new Njams(clientPath, "1.0.0", technology, settings);

        // add custom image for your technology
        njams.addImage(technology, "images/njams_java_sdk_process_step.png");

        // add custom images for your activites
        njams.addImage("startType", "images/njams_java_sdk_process_start.png");
        njams.addImage("stepType", "images/njams_java_sdk_process_step.png");
        njams.addImage("endType", "images/njams_java_sdk_process_end.png");

        /**
         * Creating a process by adding a ProcessModel
         */
        // Specify a process path, which is relative to the client path
        Path processPath = new Path("Processes", "SimpleProcess");

        // Create an new empty process model
        ProcessModel process = njams.createProcess(processPath);

        // start the model with a start activity by id, name and type, where type should match one of your previously
        // registered images
        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);
        // step to the next activity
        ActivityModel logModel = startModel.transitionTo("log", "Log", "stepType");
        // step to the end activity
        ActivityModel endModel = logModel.transitionTo("end", "End", "endType");

        // Start client and flush resources, which will create a projectmessage to send all resources to the server
        njams.start();

        /**
         * Running a process by creating a job
         */
        // Create a job from a previously created ProcessModel
        Job job = process.createJob();

        // Starts the job, i.e., sets the according status, job start date if not set before, and flags the job to begin
        // flushing.
        job.start();

        // Create the start activity from the previously creates startModel
        Activity start = job.createActivity(startModel).build();
        // add input and output data to the activity
        start.processInput("startInput");
        start.processOutput("startOutput");

        // step to the next activity from the previous one.
        Activity log = start.stepTo(logModel).build();
        log.processInput("logInput");
        log.processOutput("logOutput");

        // step to the end
        Activity end = log.stepTo(endModel).build();
        end.processInput("endInput");
        end.processOutput("endOutput");

        // End the job, which will flush all previous steps into a logmessage wich will be send to the server
        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop the client...
        njams.stop();
    }
}
