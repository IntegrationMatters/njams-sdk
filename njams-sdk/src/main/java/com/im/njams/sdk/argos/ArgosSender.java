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

import java.io.Closeable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.im.njams.sdk.settings.Settings;

/**
 * This Singleton class cares about collecting and sending Argos Metrics via UPD to an nJAMS Agent.
 * It will send every 10 seconds all available Metrics.
 * <p>
 * To provide Metrics you must register an @see {@link ArgosCollector}.
 */
public class ArgosSender implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(ArgosSender.class);

    /**
     * Name of the property flag to enable or disable collecting Argos Metrics.
     */
    public static final String NJAMS_SUBAGENT_ENABLED = "njams.sdk.subagent.enabled";

    /**
     * Name of the property port where the nJAMS Agent runs and ArgosSender will send metrics
     */
    public static final String NJAMS_SUBAGENT_PORT = "njams.sdk.subagent.port";

    /**
     * Name of the property host where the nJAMS Agent runs and ArgosSender will send metrics
     */
    public static final String NJAMS_SUBAGENT_HOST = "njams.sdk.subagent.host";

    //Defaults
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 6450;
    private static final String DEFAULT_ENABLED = "true";

    //For serializing the metrics
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

    // All registered ArgosCollectors that will create metrics.
    private final Map<ArgosComponent, ArgosMultiCollector> argosCollectors = new HashMap<>();

    private boolean isRunning = false;
    private boolean isInitialized = false;

    private static ArgosSender instance = null;

    protected ArgosSender() {
    }

    // Lazy Initialization (If required then only)
    public synchronized static ArgosSender getInstance() {
        if (instance == null) {
            LOG.debug("Instantiate singleton for ArgosSender.");
            instance = new ArgosSender();
        }
        return instance;
    }

    /**
     * Reads the properties {@value NJAMS_SUBAGENT_HOST}, {@value NJAMS_SUBAGENT_PORT} and
     * {@value NJAMS_SUBAGENT_ENABLED} from the given settings.
     *
     * @param settings the settings for connection establishment
     */
    public synchronized void init(Settings settings) {
        if (isInitialized) {
            LOG.debug("ArgosSender already initialized.");
            return;
        }
        LOG.debug("Initialize ArgosSender.");
        Properties properties = settings.getAllProperties();
        enabled = Boolean.parseBoolean(getProperty(properties, NJAMS_SUBAGENT_ENABLED, DEFAULT_ENABLED));
        host = getProperty(properties, NJAMS_SUBAGENT_HOST, DEFAULT_HOST);
        try {
            port =
                    Integer.parseInt(getProperty(properties, NJAMS_SUBAGENT_PORT, String.valueOf(DEFAULT_PORT)));
        } catch (NumberFormatException e) {
            LOG.debug("Could not parse property: ", e);
            LOG.warn("Could not parse property " + NJAMS_SUBAGENT_PORT + " to an Integer. " + "Using default Port " +
                    DEFAULT_PORT + " instead");
            port = DEFAULT_PORT;
        }
        isInitialized = true;
    }

    private String getProperty(Properties properties, String key, String defaultValue) {
        // SDK-175: njams.client.* parameters are deprecated
        return properties.getProperty(key, properties.getProperty(key.replace("\\.sdk\\.", ".client."), defaultValue));
    }

    /**
     * Adds a collector that will create metrics every time {@link #run() run()} is called.
     *
     * @param collector The collector that collects metrics
     */
    public void addArgosCollector(ArgosMultiCollector collector) {
        if (collector == null) {
            return;
        }
        synchronized (argosCollectors) {
            argosCollectors.put(collector.getArgosComponent(), collector);
        }
        LOG.debug("Added collector: {} ({})", collector.getArgosComponent().getId(), collector.getArgosComponent()
                .getMeasurement());
        start();
    }

    /**
     * Removes all collectors and closes the sender instance.
     */
    public void removeAllArgosCollectors() {
        synchronized (argosCollectors) {
            argosCollectors.clear();
            close();
        }
    }

    /**
     * Removes the given collector from the sender instance.
     * @param collector The collector instance to be removed from the sender.
     * @return <code>true</code> only if the given collector was registered with the sender.
     */
    public boolean removeArgosCollector(ArgosMultiCollector collector) {
        if (collector == null) {
            return false;
        }
        synchronized (argosCollectors) {
            boolean removed = argosCollectors.remove(collector.getArgosComponent()) != null;
            if (removed && argosCollectors.isEmpty()) {
                close();
            }
            return removed;
        }
    }

    /**
     * Tries to establish a connection and starts a sending thread.
     */
    public void start() {
        if (enabled) {
            synchronized (argosCollectors) {
                if (argosCollectors.isEmpty() || isRunning) {
                    return;
                }
                isRunning = true;
            }
            Executors.newSingleThreadExecutor().execute(() -> asyncStart());
        } else {
            LOG.info("Argos Sender is disabled. Will not send any Metrics.");
        }
    }

    private void asyncStart() {
        try {
            ip = InetAddress.getByName(host);
            socket = new DatagramSocket();
            LOG.info("Enabled Argos Sender with target address {}:{}", ip, port);

        } catch (SocketException | UnknownHostException e) {
            LOG.error("Failed to resolve address: {}", ip, e);
            enabled = false;
            LOG.warn("Argos Sender is disabled. Will not send any Metrics.");
            return;
        }

        execService = Executors.newSingleThreadScheduledExecutor();
        setSendingScheduleTo(INITIAL_DELAY, INTERVAL, TimeUnit.SECONDS);
    }

    boolean isExecutorSet(){
        return execService != null;
    }

    void setSendingScheduleTo(long initialDelay, long interval, TimeUnit seconds) {
        execService.scheduleAtFixedRate(this::run, initialDelay, interval, seconds);
    }

    /**
     * Create and send metrics
     */
    private void run() {
        if (socket == null) {
            LOG.warn("Socket connection for Argos Sender is not open. Cannot send Metrics.");
            return;
        }
        publishData();
    }

    private void publishData() {
        final Collection<ArgosMultiCollector> collectors;
        synchronized (argosCollectors) {
            collectors = new ArrayList<>(argosCollectors.values());
        }
        int count = 0;
        for (ArgosMultiCollector collector : collectors) {
            try {
                final Collection<ArgosMetric> allCollectedStatistics = collector.collectAll();
                for (ArgosMetric collectedStatistics : allCollectedStatistics) {
                    final String data = serializeStatistics(collectedStatistics);
                    LOG.trace("Publishing metric:\n{}", data);
                    final byte[] buf = data.getBytes();
                    final DatagramPacket packet = new DatagramPacket(buf, buf.length, ip, port);
                    socket.send(packet);
                    count++;
                }
            } catch (Exception e) {
                LOG.error("Failed to send data. Cause: ", e);
            }
        }
        LOG.debug("Published {} metrics to {}:{}", count, ip, port);
    }

    private String serializeStatistics(ArgosMetric statisticsToSerialize) {
        try {
            return writer.writeValueAsString(statisticsToSerialize);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize statistics. Cause: ", e);
        }

        return "";
    }

    /**
     * Stop the sending thread and close the connection
     */
    @Override
    public void close() {
        synchronized (argosCollectors) {
            isRunning = false;
        }
        if (execService != null) {
            execService.shutdown();
            try {
                execService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.debug("Interrupted while waiting for termination.", e);
            }
        }
        if (socket != null) {
            socket.close();
        }
    }
}
