package com.im.njams.sdk.communication.it;

import org.apache.activemq.broker.BrokerService;
import org.junit.rules.ExternalResource;

/**
 * Starts an in-process, non-persistent ActiveMQ broker reachable via the vm:// transport.
 * Supports stop/start to simulate a transient outage.
 */
public class EmbeddedActiveMqBroker extends ExternalResource {

    public static final String BROKER_NAME = "sdk375-baseline";
    public static final String BROKER_URL = "vm://" + BROKER_NAME + "?create=false&waitForStart=5000";

    private BrokerService broker;

    public String brokerUrl() {
        return BROKER_URL;
    }

    public void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName(BROKER_NAME);
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setDeleteAllMessagesOnStartup(true);
        broker.start();
        broker.waitUntilStarted();
    }

    public void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
            broker = null;
        }
    }

    public void restart() throws Exception {
        stopBroker();
        startBroker();
    }

    @Override
    protected void before() throws Throwable {
        startBroker();
    }

    @Override
    protected void after() {
        try {
            stopBroker();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stop embedded broker", e);
        }
    }
}
