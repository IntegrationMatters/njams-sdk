package com.im.njams.sdk.logmessage;

public class NjamsState {

    public static final String NOT_STARTED_EXCEPTION_MESSAGE = "The instance needs to be started first!";

    private boolean started = false;

    /**
     * @return if this client instance is started
     */
    public boolean isStarted() {
        return started;
    }

    public void start() {
        started = true;
    }

    public void stop() {
        started = false;
    }
}
