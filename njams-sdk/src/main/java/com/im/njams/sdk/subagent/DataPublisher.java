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

package com.im.njams.sdk.subagent;

import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.settings.encoding.Transformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DataPublisher implements Runnable, AutoCloseable {

    public static final String NJAMS_SUBAGENT_PORT = "njams.client.subagent.port";
    public static final String NJAMS_SUBAGENT_ENABLED = "njams.client.subagent.enabled";
    public static final String NJAMS_SUBAGENT_HOST = "njams.client.subagent.host";

    private static final Logger LOG = LoggerFactory.getLogger(DataPublisher.class);

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 6450;
    private static final String DEFAULT_ENABLED = "true";

    private DatagramSocket socket;
    private InetAddress ip;
    private Integer port;
    private boolean enabled;

    private TelemetryProducer telemetryProducer;
    // the scheduler to run this
    private ScheduledExecutorService execService;

    public DataPublisher(Settings settings) {
        Properties properties = settings.getProperties();
        try {
            this.port = Integer.parseInt(Transformer.decode(properties.getProperty(NJAMS_SUBAGENT_PORT)));
        } catch (Exception couldntParseInt) {
            this.port = DEFAULT_PORT;
        }
        this.enabled = Boolean
                .parseBoolean(Transformer.decode(properties.getProperty(NJAMS_SUBAGENT_ENABLED, DEFAULT_ENABLED)));

        this.telemetryProducer = new TelemetryProducer();
        if (enabled) {
            try {
                String host = Transformer.decode(properties.getProperty(NJAMS_SUBAGENT_HOST, DEFAULT_HOST));
                ip = InetAddress.getByName(host);
                socket = new DatagramSocket();
                LOG.info("Enabled nJAMS data collector with target address {}:{}", ip, port);

            } catch (SocketException | UnknownHostException e) {
                LOG.error("Failed to resolve address: {}", ip, e);
                this.enabled = false;
            }
        }
    }

    public TelemetryProducer getTelemetryProducer() {
        return telemetryProducer;
    }

    public void start() {
        if (enabled) {
            execService = Executors.newSingleThreadScheduledExecutor();
            execService.scheduleAtFixedRate(this, 10, 10, TimeUnit.SECONDS);
        }
    }

    @Override
    public void run() {
        if (socket == null) {
            LOG.warn("no open connection. Aborting.");
            return;
        }
        publishData();
    }

    private void publishData() {
        Iterator<TelemetrySupplier> iterator = telemetryProducer.iterator();
        while (iterator.hasNext()) {
            try {
                TelemetrySupplier supplier = iterator.next();
                String data = supplier.toString();
                if (LOG.isTraceEnabled()) {
                    LOG.trace(data);
                }
                byte[] buf = data.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, ip, port);
                socket.send(packet);
            } catch (Exception e) {
                LOG.error("Failed to send data.", e);
            }
        }
    }


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
///*
// * Copyright (c) 2019 Faiz & Siegeln Software GmbH
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
// * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
// * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
// * the Software.
// *
// * The Software shall be used for Good, not Evil.
// *
// *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
// * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
// * IN THE SOFTWARE.
// */
//
//package com.im.njams.sdk.subagent;
//
//        import com.im.njams.sdk.subagent.bw6.AbstractStatistics;
//        import org.slf4j.Logger;
//        import org.slf4j.LoggerFactory;
//
//        import java.net.*;
//        import java.util.ArrayList;
//        import java.util.concurrent.Executors;
//        import java.util.concurrent.ScheduledExecutorService;
//        import java.util.concurrent.TimeUnit;
//
//public class DataPublisher implements Runnable, AutoCloseable {
//
//    private static final Logger LOG = LoggerFactory.getLogger(DataPublisher.class);
//
//    private static final String DEFAULT_HOST = "127.0.0.1";
//    private static final int DEFAULT_PORT = 6450;
//
//    private DatagramSocket socket = null;
//    private String host;
//    private Integer port;
//    private InetAddress ip;
//    private boolean enabled;
//
//    // the scheduler to run this
//    private ScheduledExecutorService execService;
//
//    public DataPublisher() {
//        this(DEFAULT_HOST, DEFAULT_PORT, true);
//    }
//
//    public DataPublisher(String host) {
//        this(host, DEFAULT_PORT, true);
//    }
//
//    public DataPublisher(String host, Integer port) {
//        this(host, port, true);
//    }
//
//    public DataPublisher(String host, Integer port, boolean enabled) {
//        this.host = host;
//        this.port = port;
//        this.enabled = enabled;
//        if (enabled) {
//            try {
//                ip = InetAddress.getByName(host);
//                socket = new DatagramSocket();
//                LOG.info("Enabled nJAMS data collector with target address {}:{}", ip, port);
//            } catch (SocketException | UnknownHostException e) {
//                LOG.error("Failed to resolve address: {}", ip, e);
//                this.enabled = false;
//            }
//        }
//    }
//
//    public void start() {
//        if (enabled) {
//            execService = Executors.newSingleThreadScheduledExecutor();
//            execService.scheduleAtFixedRate(this, 10, 10, TimeUnit.SECONDS);
//        }
//    }
//
//    @Override
//    public void run() {
//        if (socket == null) {
//            LOG.warn("no open connection. Aborting.");
//            return;
//        }
//    }
//
//    private void publishData(ArrayList<AbstractStatistics> statistics) {
//        if (statistics == null || statistics.size() == 0) {
//            return;
//        }
//        for (AbstractStatistics statistic : statistics) {
//            try {
//                String data = statistic.toString();
//                LOG.trace(data);
//                byte[] buf = data.getBytes();
//                DatagramPacket packet = new DatagramPacket(buf, buf.length, ip, port);
//                socket.send(packet);
//            } catch (Exception e) {
//                LOG.error("Failed to send data.", e);
//            }
//        }
//
//    }
//
//    @Override
//    public void close() {
//        if (execService != null) {
//            execService.shutdown();
//        }
//        if (socket != null) {
//            socket.close();
//        }
//    }
//}
