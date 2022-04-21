package com.im.njams.sdk.njams;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

public class NjamsState {

    private static final String NOT_STARTED_EXCEPTION_MESSAGE = "The instance needs to be started first!";

    private boolean started = false;

    /**
     * @return if this client instance is started
     */
    public boolean isStarted() {
        return started;
    }

    public boolean isStopped() {
        return !started;
    }

    public void start() {
        started = true;
    }

    public void stop() {
        started = false;
    }

    public void handleStopBeforeStart() {
        throw new NjamsSdkRuntimeException(NOT_STARTED_EXCEPTION_MESSAGE);
    }
}
