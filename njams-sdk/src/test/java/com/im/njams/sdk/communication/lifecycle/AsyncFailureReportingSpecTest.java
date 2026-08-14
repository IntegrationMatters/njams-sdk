package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderPoolTestAccess;

public class AsyncFailureReportingSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void notifyConnectionFailureReachesThePool() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object sender = pool.acquire();
        // Simulate JMS's async ExceptionListener path: a failure with no send call in flight.
        pool.notifyConnectionFailureOn(sender, new IllegalStateException("async loss"));
        assertTrue("the pool must see the group as failed",
            pool.awaitConnectionFailure(5, TimeUnit.SECONDS));
    }
}
