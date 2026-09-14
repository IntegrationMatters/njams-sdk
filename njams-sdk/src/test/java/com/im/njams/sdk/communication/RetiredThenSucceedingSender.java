package com.im.njams.sdk.communication;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;

/**
 * Fails its first send the way a sender retired mid-send does, then succeeds — standing in for "the pool
 * replaced this connection while the send was still retrying".
 */
public class RetiredThenSucceedingSender extends AbstractSender {

    private final AtomicInteger sends = new AtomicInteger();
    private final CountDownLatch succeeded = new CountDownLatch(1);

    public int sendCount() {
        return sends.get();
    }

    public boolean awaitSuccessfulSend(long timeout, TimeUnit unit) throws InterruptedException {
        return succeeded.await(timeout, unit);
    }

    @Override
    public String getName() {
        return "retired-then-succeeding";
    }

    @Override
    protected void send(LogMessage msg, String clientSessionId) {
        attempt();
    }

    @Override
    protected void send(ProjectMessage msg, String clientSessionId) {
        attempt();
    }

    @Override
    protected void send(TraceMessage msg, String clientSessionId) {
        attempt();
    }

    private void attempt() {
        if (sends.incrementAndGet() == 1) {
            // Wrapped the way JmsSender.send would wrap it, so this also proves dispatch's chain walk works.
            throw new com.im.njams.sdk.common.NjamsSdkRuntimeException("Unable to send LogMessage",
                new SenderRetiredException(new IllegalStateException("queue full")));
        }
        succeeded.countDown();
    }

    @Override
    protected boolean isCongestion(Throwable failure) {
        return false;
    }

    @Override
    protected boolean isMessageRejected(Throwable failure) {
        return false;
    }
}
