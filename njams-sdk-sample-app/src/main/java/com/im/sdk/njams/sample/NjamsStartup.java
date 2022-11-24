package com.im.sdk.njams.sample;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.Path;
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
        try (InputStream is = this.getClass().getResourceAsStream("/" + filename)) {
            props.load(is);
        } catch (IOException e) {
            LOG.error(e.getMessage());
        }

        Settings settings = new Settings();
        settings.addAll(props);
        return settings;
    }

    private Settings getEmsJmsProperties() {
        Settings communicationProperties = new Settings();
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
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION, "njams4.dev.bjoern.commands");

        return communicationProperties;
    }

    private Settings getActiveMqJmsProperties() {
        Settings communicationProperties = new Settings();
        communicationProperties.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY,
            "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_SECURITY_PRINCIPAL, "admin");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_SECURITY_CREDENTIALS, "admin");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, "tcp://localhost:61616");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_USERNAME, "admin");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_PASSWORD, "admin");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams");
        //optional: if you want to use a topic for commands not following the name of the other destinations, specify it here
        //communicationProperties.put(JmsConstants.NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION, "njams4.dev.bjoern.commands");

        return communicationProperties;
    }
}
