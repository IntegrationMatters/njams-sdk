/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.faizsiegeln.test.argos;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.argos.ArgosCollector;
import com.im.njams.sdk.argos.ArgosComponent;
import com.im.njams.sdk.argos.ArgosMetric;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;

import java.util.Random;

/**
 * This is a sample client that sends random numbers for my own random number collector.
 *
 * @author krautenberg
 */
public class RandomNumberSenderClient {

    private static Njams njams;

    public static void main(String[] args) throws InterruptedException {

        createAndStartNjams();

        addRandomNumberCollector();

        //Start client and flush resources, which will create a projectmessage to send all resources to the server
        //It starts the DataPublisher aswell
        njams.start();

        Thread.sleep(1500000);

        //If you are finished with processing or the application goes down, stop the client...
        njams.stop();
    }

    private static void createAndStartNjams() {
        String technology = "sdk4";

        //Specify a client path. This path specifies where your client instance will be visible in the object tree.
        Path clientPath = new Path("SDK4", "Client", "Argos");

        //Create communicationProperties, which specify how your client will communicate with the server
        Settings settings = getProperties();

        //Instantiate client for first application
        njams = new Njams(clientPath, "4.0.11", technology, settings);
    }

    private static Settings getProperties() {
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
        communicationProperties.put(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION, "njams4.dev.phillip.commands");

        //Argos relevant properties
        communicationProperties.put(NjamsSettings.PROPERTY_ARGOS_SUBAGENT_HOST, "os1113");
        communicationProperties.put(NjamsSettings.PROPERTY_ARGOS_SUBAGENT_PORT, "6450");
        communicationProperties.put(NjamsSettings.PROPERTY_ARGOS_SUBAGENT_ENABLED, "true");
        return communicationProperties;
    }

    private static void addRandomNumberCollector() {
        RandomNumberCollector randomNumberCollector = new RandomNumberCollector();
        njams.addArgosCollector(randomNumberCollector);
    }

    private static class RandomNumberCollector extends ArgosCollector<RandomNumberMetric> {

        private Random randomGenerator;

        public RandomNumberCollector() {
            super(new ArgosComponent("testRandomNumberId", "testRandomNumberName", "testRandomNumberContainer",
                "testRandomNumberMeasurement", "testRandomNumberType"));
            randomGenerator = new Random();
        }

        @Override
        protected RandomNumberMetric create() {
            RandomNumberMetric statistics = new RandomNumberMetric(getArgosComponent());
            statistics.setRandomNumber(randomGenerator.nextInt(100));
            return statistics;
        }
    }

    private static class RandomNumberMetric extends ArgosMetric {

        private int randomNumber = 0;

        public RandomNumberMetric(ArgosComponent argosComponent) {
            super(argosComponent);
        }

        public int getRandomNumber() {
            return randomNumber;
        }

        public void setRandomNumber(int randomNumber) {
            this.randomNumber = randomNumber;
        }

    }
}
