package com.im.njams.sdk.it.support;

import org.junit.rules.ExternalResource;

import com.im.njams.sdk.communication.CountingDiscardMonitor;

/**
 * Installs a fresh {@link CountingDiscardMonitor} for the duration of one test, so a scenario can assert on the
 * absolute number of discards it caused, and restores the SDK's real monitor afterward.
 */
public class DiscardObserver extends ExternalResource {

    private CountingDiscardMonitor monitor;

    @Override
    protected void before() {
        monitor = new CountingDiscardMonitor();
        monitor.install();
    }

    @Override
    protected void after() {
        CountingDiscardMonitor.restore();
    }

    public int count() {
        return monitor.count();
    }
}
