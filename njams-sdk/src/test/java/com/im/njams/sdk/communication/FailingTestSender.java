package com.im.njams.sdk.communication;

/**
 * Registered via SPI (test scope) but always fails to be instantiated, to cover the lookup of unusable senders.
 */
public class FailingTestSender extends TestSender {

    public FailingTestSender() {
        throw new IllegalStateException("sender constructor failed");
    }
}
