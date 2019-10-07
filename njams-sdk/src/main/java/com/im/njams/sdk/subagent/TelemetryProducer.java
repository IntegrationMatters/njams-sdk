/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.subagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class TelemetryProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryProducer.class);

    private Map<String, TelemetrySupplierFactory> telemetrySupplierFactories;

    public TelemetryProducer() {
        telemetrySupplierFactories = new HashMap<>();
    }

    public void addTelemetrySupplierFactory(TelemetrySupplierFactory factory) {
        telemetrySupplierFactories.put(factory.getMeasurement(), factory);
    }

    public TelemetrySupplierFactory getTelemetrySupplierFactoryByMeasurement(String measurement){
        return telemetrySupplierFactories.get(measurement);
    }

    public Iterator<TelemetrySupplier> iterator() {
        List<TelemetrySupplier> newTelemetrySupplier = createAll();
        return newTelemetrySupplier.iterator();
    }

    private List<TelemetrySupplier> createAll() {
        List<TelemetrySupplier> suppliers = new ArrayList<>();
        for (TelemetrySupplierFactory telemetrySupplierFactory : telemetrySupplierFactories.values()) {
            try {
                List<TelemetrySupplier> telemetrySuppliers = telemetrySupplierFactory.createAll();
                suppliers.addAll(telemetrySuppliers);
            } catch (Exception couldntCreateSuppliers) {
                LOG.error(couldntCreateSuppliers.getMessage(), couldntCreateSuppliers);
            }
        }
        return suppliers;
    }
}
