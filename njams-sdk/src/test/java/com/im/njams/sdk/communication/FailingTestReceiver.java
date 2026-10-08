package com.im.njams.sdk.communication;

/**
 * Registered via SPI (test scope) but always fails to be instantiated, to cover the lookup of unusable receivers.
 */
public class FailingTestReceiver extends TestReceiver {

    public FailingTestReceiver() {
        throw new IllegalStateException("receiver constructor failed");
    }
}
