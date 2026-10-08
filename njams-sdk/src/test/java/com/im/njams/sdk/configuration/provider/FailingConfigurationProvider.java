package com.im.njams.sdk.configuration.provider;

/**
 * Registered via SPI (test scope) but always fails to be instantiated, to cover the lookup of unusable providers.
 */
public class FailingConfigurationProvider extends MemoryConfigurationProvider {

    public FailingConfigurationProvider() {
        throw new IllegalStateException("config provider constructor failed");
    }
}
