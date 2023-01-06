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
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

/**
 * This is a simple sample client, which creates a simple process with three
 * steps.
 *
 * @author pnientiedt
 * @author sfaiz (added getKafkaProperties)
 */
public class SimpleClient {

    public static void main(String[] args) throws InterruptedException {

        String technology = "sdk4";

        // Specify a client path. This path specifies where your client instance will be
        // visible in the object tree.
        Path clientPath = new Path("SDK4", "Client", "Simple");

        // Create communicationProperties, which specify how your client will
        // communicate with the server
        //Settings settings = getJmsProperties();
        //Settings settings = getCloudProperties();
        Settings settings = getHttpProperties();
        //Settings settings = getHttpsProperties();
        //Settings settings = getKafkaProperties();
        //Settings settings = getActiveMqsslProperties();

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

        // start the model with a start activity by id, name and type, where type should
        // match one of your previously registered images
        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);
        // step to the next activity
        ActivityModel logModel = startModel.transitionTo("log", "Log", "stepType");
        // step to the end activity
        ActivityModel endModel = logModel.transitionTo("end", "End", "endType");

        // Start client and flush resources, which will create a projectmessage to send
        // all resources to the server
        if (!njams.start()) {
            System.err.println("Failed to start nJAMS");
            System.exit(-1);
        }

        for (int i = 0; i < 10; i++) {
            /**
             * Running a process by creating a job
             */
            // Create a job from a previously created ProcessModel
            Job job = process.createJob();

            // Starts the job, i.e., sets the according status, job start date if not set
            // before, and flags the job to begin flushing.
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

            // End the job, which will flush all previous steps into a logmessage wich will
            // be send to the server
            job.end(true);
            System.out.println("Sent job with logId: " + job.getLogId());
            Thread.sleep(1000);
        }

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop the
        // client...
        njams.stop();

        Thread.sleep(1000);
        System.out.println("Finsihed " + SimpleClient.class.getSimpleName());
    }

    private static Settings getJmsProperties() {
        Settings communicationProperties = new Settings();
        communicationProperties.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, "tibjmsnaming://os0155:7222");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_USERNAME, "njams");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_PASSWORD, "njams");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams.endurance");
        // optional: if you want to use a topic for commands not following the name of
        // the other destinations, specify it here
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION, "njams4.blablub.commands");
        return communicationProperties;
    }

    private static Settings getKafkaProperties() {
        Settings communicationProperties = new Settings();

        // nJAMS properties
        communicationProperties.put(NjamsSettings.PROPERTY_COMMUNICATION, "KAFKA");
        // communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_REPLY_PRODUCER_IDLE_TIME, "300000"); // in ms
        communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_TOPIC_PREFIX, "njams");

        // Kafka properties
        communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_BOOTSTRAP_SERVERS, "kafka01:9092");

        // SASL_PLAIN
        /*
         * communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX +
         * "sasl.mechanism", "PLAIN");
         * communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX +
         * "sasl.jaas.config",
         * "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"njams\" password=\"njams-secret\";"
         * );
         */

        // SSL
        //        communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX + "security.protocol", "SSL");
        //        communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX + "ssl.truststore.location",
        //                "C:\\tmp\\certificates\\kafka.truststore");
        //        communicationProperties
        //                .put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX + "ssl.truststore.password", "Aa123456!");
        //        communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX + "ssl.keystore.location",
        //                "C:\\tmp\\certificates\\kafka.keystore");
        //        communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX + "ssl.keystore.password", "Aa123456!");
        //        communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX + "ssl.key.password", "Aa123456!");

        // SASL_SSL
        /*
         * communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX +
         * "sasl.mechanism", "PLAIN");
         * communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX +
         * "security.protocol", "SASL_SSL");
         * communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX +
         * "ssl.truststore.location", "C:\\tmp\\certificates\\kafka.truststore");
         * communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX +
         * "ssl.truststore.password", "Aa123456!");
         * communicationProperties.put(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX +
         * "sasl.jaas.config",
         * "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"njams\" password=\"njams-secret\";"
         * );
         */
        return communicationProperties;
    }

    private static Settings getHttpProperties() {
        Settings communicationProperties = new Settings();
        communicationProperties.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        communicationProperties.put("njams.sdk.communication.http.base.url",
                "http://localhost:8080/njams/");
        communicationProperties.put("njams.sdk.communication.http.dataprovider.prefix", "sdk");
        communicationProperties.put("njams.client.sdk.sharedcommunications", "true");
        return communicationProperties;
    }

    private static Settings getHttpsProperties() {
        Settings communicationProperties = new Settings();
        communicationProperties.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTPS");
        communicationProperties.put("njams.sdk.communication.http.base.url",
                "https://os0100.integrationmatters.com:8443/njams/");
        communicationProperties.put("njams.sdk.communication.http.dataprovider.prefix", "bw");
        communicationProperties
                .put("njams.sdk.communication.http.ssl.certificate.file",
                        "/Users/bwand/Development/SSLKeystore/cert.wildcard.integrationmatters.com/ca-root-integrationmatters.com.pem");

        communicationProperties.put("njams.client.sdk.sharedcommunications", "true");
        return communicationProperties;
    }

    private static Settings getActiveMqsslProperties() {
        Settings communicationProperties = new Settings();
        communicationProperties.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY, "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, "ssl://localhost:61714");
        //communicationProperties.put(NjamsSettings.PROPERTY_JMS_KEYSTORE, "/Users/bwand/Development/apache-activemq-5.17.1/conf/client.ks");
        //communicationProperties.put(NjamsSettings.PROPERTY_JMS_KEYSTOREPASSWORD, "password");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_TRUSTSTORE, "/Users/bwand/Development/apache-activemq-5.17.1/conf/client.ts");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_TRUSTSTOREPASSWORD, "password");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "ActiveMQSslConnectionFactory");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_USERNAME, "njams");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_PASSWORD, "njams");
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams");

        return communicationProperties;
    }
}
