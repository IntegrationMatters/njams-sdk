/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.argos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.settings.encoding.Transformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class will send statistics to the nJAMS Agent.
 *
 * @author krautenberg
 * @version 4.0.11
 */
public class ArgosSender implements Runnable, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ArgosSender.class);

    /**
     * Property {@value NJAMS_SUBAGENT_PORT} for defining the port the receiving nJAMS Agent is listening to.
     */
    public static final String NJAMS_SUBAGENT_PORT = "njams.client.subagent.port";

    /**
     * Property {@value NJAMS_SUBAGENT_ENABLED} for defining if the SDK should create and send statistics.
     */
    public static final String NJAMS_SUBAGENT_ENABLED = "njams.client.subagent.enabled";

    /**
     * Property {@value NJAMS_SUBAGENT_HOST} for defining the hostname of the nJAMS Agent.
     */
    public static final String NJAMS_SUBAGENT_HOST = "njams.client.subagent.host";

    //Defaults
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 6450;
    private static final String DEFAULT_ENABLED = "true";

    //For serializing the statistics
    private final ObjectWriter writer = new ObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .writer().withDefaultPrettyPrinter();

    private static final long INITIAL_DELAY = 10;
    private static final long INTERVAL = 10;

    private DatagramSocket socket;
    private String host;
    private InetAddress ip;
    private Integer port;
    private boolean enabled;

    // the scheduler to run this
    private ScheduledExecutorService execService;

    // All registered ArgosCollectors that will create statistics.
    private Map<ArgosComponent, ArgosCollector> argosCollectors;

    /**
     * Reads the properties {@value NJAMS_SUBAGENT_HOST}, {@value NJAMS_SUBAGENT_PORT} and
     * {@value NJAMS_SUBAGENT_ENABLED} from the given settings.
     *
     * @param settings the settings for connection establishment
     */
    public ArgosSender(Settings settings) {
        Properties properties = settings.getProperties();

        enabled = Boolean
                .parseBoolean(Transformer.decode(properties.getProperty(NJAMS_SUBAGENT_ENABLED, DEFAULT_ENABLED)));

        host = Transformer.decode(properties.getProperty(NJAMS_SUBAGENT_HOST, DEFAULT_HOST));

        try {
            this.port = Integer.parseInt(Transformer.decode(properties.getProperty(NJAMS_SUBAGENT_PORT)));
        } catch (NumberFormatException e) {
            LOG.debug("Could not parse property: ", e);
            LOG.warn("Could not parse property " + NJAMS_SUBAGENT_PORT + " to an Integer. " + "Using default Port " +
                     DEFAULT_PORT + " instead");
            this.port = DEFAULT_PORT;
        }

        argosCollectors = new HashMap<>();
    }

    /**
     * Adds a collector that will create statistics every time {@link #run() run()} is called.
     *
     * @param collector The collector that collects statistics
     */
    public void addArgosCollector(ArgosCollector collector) {
        argosCollectors.put(collector.getArgosComponent(), collector);
    }

    /**
     * Tries to establish a connection and starts a sending thread.
     */
    public void start() {
        if (enabled) {
            try {
                ip = InetAddress.getByName(host);
                socket = new DatagramSocket();
                LOG.info("Enabled Argos Sender with target address {}:{}", ip, port);

            } catch (SocketException | UnknownHostException e) {
                LOG.error("Failed to resolve address: {}", ip, e);
                this.enabled = false;
                LOG.warn("Argos Sender is disabled. Will not send any Metrics.");
                return;
            }

            execService = Executors.newSingleThreadScheduledExecutor();
            execService.scheduleAtFixedRate(this, INITIAL_DELAY, INTERVAL, TimeUnit.SECONDS);
        } else {
            LOG.info("Argos Sender is disabled. Will not send any Metrics.");
        }
    }

    /**
     * Create and send statistics
     */
    @Override
    public void run() {
        if (socket == null) {
            LOG.warn("Socket connection for Argos Sender is not open. Cannot send Metrics.");
            return;
        }
        publishData();
    }

    private void publishData() {
        Iterator<ArgosCollector> iterator = argosCollectors.values().iterator();
        while (iterator.hasNext()) {
            try {
                ArgosCollector collector = iterator.next();
                ArgosStatistics collectedStatistics = collector.collect();

                String data = serializeStatistics(collectedStatistics);
                if (LOG.isTraceEnabled()) {
                    LOG.trace(data);
                }
                byte[] buf = data.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, ip, port);
                socket.send(packet);
            } catch (Exception e) {
                LOG.error("Failed to send data. Cause: ", e);
            }
        }
    }

    private String serializeStatistics(ArgosStatistics statisticsToSerialize) {
        try {
            return writer.writeValueAsString(statisticsToSerialize);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return "";
    }

    /**
     * Stop the sending thread and close the connection
     */
    @Override
    public void close() {
        if (execService != null) {
            execService.shutdown();
        }
        if (socket != null) {
            socket.close();
        }
    }
}
