package com.faizsiegeln.test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.jms.JmsConstants;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

import java.util.Properties;

/**
 * This client shows how to add an additional process after the start of Njams.
 */
public class AdditionalProcessClient {

    public static void main(String[] args) throws InterruptedException {

        String technology = "sdk4";

        //Specify a client path. This path specifies where your client instance will be visible in the object tree.
        Path clientPath = new Path("SDK4", "Client", "Simple");

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

        // Start client and flush resources, which will create a projectmessage to send all resources to the server
        njams.start();

        /**
         * Creating a process by adding a ProcessModel
         */
        //Specify a process path, which is relative to the client path
        Path processPath2 = new Path("Processes", "AddedProcess");

        //Create an new empty process model
        ProcessModel process2 = njams.createProcess(processPath2);

        //start the model with a start activity by id, name and type, where type should match one of your previously registered images
        ActivityModel startModel2 = process2.createActivity("start", "Start", "startType");
        startModel2.setStarter(true);
        //step to the next activity
        ActivityModel logModel2 = startModel2.transitionTo("log", "Log", "stepType");
        //step to the end activity
        ActivityModel endModel2 = logModel2.transitionTo("end", "End", "endType");

        // Send this additional process to the server.
        njams.sendAdditionalProcess(process2);

        Thread.sleep(1000);

        //If you are finished with processing or the application goes down, stop the client...
        njams.stop();
    }

    private static Properties getJmsProperties() {
        Properties communicationProperties = new Properties();
        communicationProperties.put(CommunicationFactory.COMMUNICATION, "JMS");
        communicationProperties.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        communicationProperties.put(JmsConstants.SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(JmsConstants.PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        communicationProperties.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(JmsConstants.USERNAME, "njams");
        communicationProperties.put(JmsConstants.PASSWORD, "njams");
        communicationProperties.put(JmsConstants.DESTINATION, "njams4.dev.bjoern");
        //optional: if you want to use a topic for commands not following the name of the other destinations, specify it here
        communicationProperties.put(JmsConstants.COMMANDS_DESTINATION, "njams4.dev.bjoern.commands");
        return communicationProperties;
    }

}
