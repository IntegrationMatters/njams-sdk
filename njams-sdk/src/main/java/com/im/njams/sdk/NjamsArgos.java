/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk;

import java.util.ArrayList;
import java.util.Collection;

import com.im.njams.sdk.argos.ArgosMultiCollector;
import com.im.njams.sdk.argos.ArgosSender;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Owns Argos metric collector registration for an {@link Njams} client.
 * Obtain via {@code njams.argos()}.
 */
public final class NjamsArgos {

    private final ArgosSender argosSender;
    private final Collection<ArgosMultiCollector<?>> collectors = new ArrayList<>();

    NjamsArgos(ClientSettings settings) {
        argosSender = ArgosSender.getInstance();
        argosSender.init(settings);
    }

    /**
     * Adds a collector that will create statistics.
     *
     * @param collector The collector that collects statistics
     */
    public void add(ArgosMultiCollector<?> collector) {
        collectors.add(collector);
        argosSender.addArgosCollector(collector);
    }

    /**
     * Removes the given collector.
     *
     * @param collector The collector to remove
     */
    public void remove(ArgosMultiCollector<?> collector) {
        collectors.remove(collector);
        argosSender.removeArgosCollector(collector);
    }

    /** Deregisters all collectors; called from Njams.stop(). */
    void stop() {
        collectors.forEach(argosSender::removeArgosCollector);
        collectors.clear();
    }
}
