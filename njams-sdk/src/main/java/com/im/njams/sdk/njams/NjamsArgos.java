/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.njams;

import com.im.njams.sdk.argos.ArgosMetric;
import com.im.njams.sdk.argos.ArgosMultiCollector;
import com.im.njams.sdk.argos.ArgosSender;
import com.im.njams.sdk.settings.Settings;

import java.util.ArrayList;
import java.util.Collection;

public class NjamsArgos {
    private final ArgosSender argosSender;
    private final Collection<ArgosMultiCollector<?>> argosCollectors;

    public NjamsArgos(Settings settings) {
        argosSender = ArgosSender.getInstance();
        argosSender.init(settings);
        argosCollectors = new ArrayList<>();
    }

    /**
     * Adds a collector that will create statistics.
     *
     * @param collector The collector that collects statistics
     * @param <T> The type of metric that will be created by the collector
     */
    public <T extends ArgosMetric> void addCollector(ArgosMultiCollector<T> collector) {
        argosCollectors.add(collector);
        argosSender.addArgosCollector(collector);
    }

    /**
     * Removes a previously set Argos Collector. By that, no more statistics from this collector will be sent to njams
     * agent.
     *
     * @param collector the Collector that will be removed
     * @param <T> The type of metric that will no longer be created by the collector
     */
    public <T extends ArgosMetric> void remove(ArgosMultiCollector<T> collector) {
        argosCollectors.remove(collector);
        argosSender.removeArgosCollector(collector);
    }

    public void stop() {
        argosCollectors.forEach(argosSender::removeArgosCollector);
        argosCollectors.clear();
    }
}
