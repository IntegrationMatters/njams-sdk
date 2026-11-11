package com.im.njams.sdk.communication;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * SDK-375: a receiver whose {@code connect()} fails while leaving its status at {@code CONNECTING} must still be
 * retried by the SDK's reconnect, and must not report itself as connecting between attempts.
 */
public class AbstractReceiverFailedConnectTest {

    private StuckConnectingReceiver receiver;

    @After
    public void tearDown() {
        if (receiver != null) {
            receiver.setShouldShutdown(true);
            receiver.cancelReconnect();
        }
    }

    @Test
    public void startupConnectFailureThatLeavesConnectingStatusIsRetried() throws InterruptedException {
        receiver = new StuckConnectingReceiver(1);

        receiver.beginConnect();

        assertTrue("receiver must be retried after a failed first connect that left the status at CONNECTING",
            awaitCondition(receiver::isConnected));
    }

    @Test
    public void receiverReportsDisconnectedBetweenFailingReconnectAttempts() throws InterruptedException {
        receiver = new StuckConnectingReceiver(Integer.MAX_VALUE);

        receiver.beginConnect();

        assertTrue("connect() must be attempted more than once", awaitCondition(() -> receiver.attempts.get() >= 2));
        assertTrue("a failed connect must not leave the receiver reporting CONNECTING",
            awaitCondition(receiver::isDisconnected));
    }

    private static boolean awaitCondition(BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }

    /** Fails its first {@code failures} connects the way HttpSseReceiver/KafkaReceiver do: status left CONNECTING. */
    private static class StuckConnectingReceiver extends AbstractReceiver {
        private final int failures;
        private final AtomicInteger attempts = new AtomicInteger();

        StuckConnectingReceiver(int failures) {
            this.failures = failures;
        }

        @Override
        public String getName() {
            return "StuckConnectingReceiver";
        }

        @Override
        public void init(ClientSettings settings) {
            // nothing to initialize
        }

        @Override
        protected Response extendRequest(Request req) {
            return null;
        }

        @Override
        public void connect() {
            connectionStatus = ConnectionStatus.CONNECTING;
            if (attempts.incrementAndGet() <= failures) {
                throw new NjamsSdkRuntimeException("connect fails");
            }
            connectionStatus = ConnectionStatus.CONNECTED;
        }

        @Override
        public void stop() {
            connectionStatus = ConnectionStatus.DISCONNECTED;
        }
    }
}
