package com.im.sdk.njams.sample;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.jms.JmsConstants;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Singleton
@Startup
public class NjamsStartup {
    private static final Logger LOG = LoggerFactory.getLogger(NjamsStartup.class);

    public static Njams njams;

    @PostConstruct
    public void init() {
        LOG.info("Starting nJAMS Client.");
        String technology = "sdk4";

        Settings settings = getFromFileWithClassloader("settings_activemq.properties");
        //Settings settings = getJmsPropertiesFromFile("settings.properties");

        // Specify a client path. This path specifies where your client instance will be visible in the object tree.
        Path clientPath = new Path("SDK4", "Client", "Simple");

        // Instantiate client for first application
        njams = new Njams(clientPath, "1.0.0", technology, settings);

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

        LOG.info("Started nJAMS Client " + njams.getClientPath());
    }

    @PreDestroy
    public void stop() {
        njams.stop();
    }

    private Settings getFromFileWithClassloader(String filename) {
        Properties props = new Properties();
        try (InputStream is = this.getClass().getResourceAsStream("/" + filename)){
            props.load(is);
        } catch (IOException e) {
            LOG.error(e.getMessage());
        }

        Settings settings = new Settings();
        settings.setProperties(props);
        return settings;
    }

    private Settings getEmsJmsProperties() {
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
        communicationProperties.put(JmsConstants.DESTINATION, "njams.endurance");
        //optional: if you want to use a topic for commands not following the name of the other destinations, specify it here
        communicationProperties.put(JmsConstants.COMMANDS_DESTINATION, "njams4.dev.bjoern.commands");

        //Create client settings and add the properties
        Settings settings = new Settings();
        settings.setProperties(communicationProperties);
        return settings;
    }

    private Settings getActiveMqJmsProperties() {
        Properties communicationProperties = new Properties();
        communicationProperties.put(CommunicationFactory.COMMUNICATION, "JMS");
        communicationProperties.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
        communicationProperties.put(JmsConstants.SECURITY_PRINCIPAL, "admin");
        communicationProperties.put(JmsConstants.SECURITY_CREDENTIALS, "admin");
        communicationProperties.put(JmsConstants.PROVIDER_URL, "tcp://localhost:61616");
        communicationProperties.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(JmsConstants.USERNAME, "admin");
        communicationProperties.put(JmsConstants.PASSWORD, "admin");
        communicationProperties.put(JmsConstants.DESTINATION, "njams");
        //optional: if you want to use a topic for commands not following the name of the other destinations, specify it here
        //communicationProperties.put(JmsConstants.COMMANDS_DESTINATION, "njams4.dev.bjoern.commands");

        //Create client settings and add the properties
        Settings settings = new Settings();
        settings.setProperties(communicationProperties);
        return settings;
    }
}
