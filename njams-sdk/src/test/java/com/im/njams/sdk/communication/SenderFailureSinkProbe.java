package com.im.njams.sdk.communication;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test helper that installs a recording {@link SenderFailureSink} on a sender, for tests outside this package
 * that need to observe {@link AbstractSender#notifyConnectionFailure(Exception)}.
 */
public final class SenderFailureSinkProbe {

    private SenderFailureSinkProbe() {
    }

    /**
     * @param sender the sender to observe
     * @return the live list of causes the sender reports from now on
     */
    public static List<Exception> attach(AbstractSender sender) {
        final List<Exception> causes = new CopyOnWriteArrayList<>();
        sender.setFailureSink((failed, cause) -> causes.add(cause));
        return causes;
    }
}
