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
import com.im.njams.sdk.communication.Communication;
import com.im.njams.sdk.communication.cloud.CloudConstants;
import com.im.njams.sdk.communication.jms.JmsConstants;
import com.im.njams.sdk.logmessage.Activity;
import com.im.njams.sdk.logmessage.Group;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;
import java.util.Properties;

/**
 * This is a simple sample client, which creates a simple process with three
 * steps.
 *
 * @author pnientiedt
 */
public class GroupClient {

    public static void main(String[] args) throws InterruptedException {
        
        String technology = "sdk4";

        //Specify a client path. This path specifies where your client instance will be visible in the object tree.
        Path clientPath = new Path("SDK4", "Client", "Group");

        //Create communicationProperties, which specify how your client will communicate with the server
        Properties properties = getJmsProperties();
        //Properties properties = getCloudProperties();

        //Create client settings and add the properties
        Settings config = new Settings();
        config.setProperties(properties);

        //Instantiate client for first application
        Njams njams = new Njams(clientPath, "1.0.0", technology, config);

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
        Path processPath = new Path("Processes", "GroupProcess");

        //Create an new empty process model
        ProcessModel process = njams.createProcess(processPath);

        //start the model with a start activity by id, name and type, where type should match one of your previously registered images
        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);
        //step to the group activity
        GroupModel groupModel = startModel.transitionToGroup("group", "Group", "groupType");
        //start the group with a groupStart activity
        ActivityModel groupStartModel = groupModel.createChildActivity("groupStart", "GroupStart", "stepType");
        groupStartModel.setStarter(true);
        //transition to the upper activity in the group
        ActivityModel upperModel = groupStartModel.transitionTo("upper", "Upper", "stepType");
        //transition to the group end
        ActivityModel groupEndModel = upperModel.transitionTo("groupEnd", "GroupEnd", "stepType");
        //trannsition to the lower activity from the groupStart
        ActivityModel lowerModel = groupStartModel.transitionTo("lower", "Lower", "stepType");
        //transition to the group end
        lowerModel.transitionTo("groupEnd", "GroupEnd", "stepType");
        //transition from the group to the the end of the process
        ActivityModel endModel = groupEndModel.getParent().transitionTo("end", "End", "endType");
        
        //optional: register custom images for the tree
        njams.addImage("first", "images/root.png");
        njams.addImage("second", "images/folder.png");
        njams.addImage("third", "images/client.png");
        njams.addImage("fourth", "images/folder.png");
        njams.addImage("fifth", "images/process.png");
        //optional: and set the type of the tree elements to the image keys 
        njams.setTreeElementType(new Path("SDK4"), "first");
        njams.setTreeElementType(new Path("SDK4", "Client"), "second");
        njams.setTreeElementType(new Path("SDK4", "Client", "Group"), "third");
        njams.setTreeElementType(new Path("SDK4", "Client", "Group", "Processes"), "fourth");
        njams.setTreeElementType(new Path("SDK4", "Client", "Group", "Processes", "GroupProcess"), "fifth");

        // Start client and flush resources, which will create a projectmessage to send all resources to the server
        njams.start();

        /**
         * Running a process by creating a job
         */
        //Create a job from a previously created ProcessModel
        Job job = process.createJob();
        
        // Starts the job, i.e., sets the according status, job start date if not set before, and flags the job to begin flushing.
        job.start();
        
        //Create the start activity from the previously creates startModel
        Activity start = job.createActivity(startModel).build();
        //step to the next activity, which is a group
        Group group = start.stepToGroup(groupModel).build();
        //create the groupStart as child activity of the group
        Activity groupStart = group.createChildActivity(groupStartModel).build();
        //step to the upper activity of the group
        Activity upper = groupStart.stepTo(upperModel).build();
        //step to the group end
        Activity groupEnd = upper.stepTo(groupEndModel).build();
        //iterate the group
        group.iterate();
        //start the second iteration of the group by adding a new groupStart instance
        Activity groupStart_2 = group.createChildActivity(groupStartModel).build();
        //now step to the lower activity in the group
        Activity lower = groupStart_2.stepTo(lowerModel).build();
        //and finish this iteration by steping to the group end
        Activity groupEnd_2 = lower.stepTo(groupEndModel).build();
        
        //step from the group to the process end
        Activity end = groupEnd_2.getParent().stepTo(endModel).build();

        //End the job, which will flush all previous steps into a logmessage wich will be send to the server
        job.end();
        
        Thread.sleep(1000);

        //If you are finished with processing or the application goes down, stop the client...
        njams.stop();
    }

    private static Properties getCloudProperties() {
         Properties communicationProperties = new Properties();
        communicationProperties.put(Communication.COMMUNICATION, CloudConstants.COMMUNICATION_NAME);
        communicationProperties.put(CloudConstants.ENDPOINT, "<cloud url>");
        communicationProperties.put(CloudConstants.APIKEY, "<cloud apikey>");
        communicationProperties.put(CloudConstants.CLIENT_INSTANCEID, "<cloud client instance>");
        communicationProperties.put(CloudConstants.CLIENT_CERTIFICATE, "<cloud client certificate>");
        communicationProperties.put(CloudConstants.CLIENT_PRIVATEKEY, "<cloud client privatekey>");
        return communicationProperties;
    }

    private static Properties getJmsProperties() {
        Properties communicationProperties = new Properties();
        communicationProperties.put(Communication.COMMUNICATION, "JMS");
        communicationProperties.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        communicationProperties.put(JmsConstants.SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(JmsConstants.PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        communicationProperties.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(JmsConstants.USERNAME, "njams");
        communicationProperties.put(JmsConstants.PASSWORD, "njams");
        communicationProperties.put(JmsConstants.DESTINATION, "njams.endurance");
        //optional: if you want to use a topic for commands not following the name of the other destinations, specify it here
        communicationProperties.put(JmsConstants.COMMANDS_DESTINATION, "njams4.dev.phillip.commands");
        return communicationProperties;
    }
}
