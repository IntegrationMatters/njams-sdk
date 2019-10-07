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

import java.util.ArrayList;
import java.util.List;

public abstract class TelemetrySupplierFactory {

    private String measurement;

    private List<ArgosSignature> signatures;

    public TelemetrySupplierFactory(String measurement) {
        this.measurement = measurement;
        this.signatures = new ArrayList<>();
    }

    public String getMeasurement() {
        return measurement;
    }

    public void addSignatureIfAbsent(ArgosSignature signature) {
        if (!signatures.contains(signature)) {
            signatures.add(signature);
        }
    }

    public List<TelemetrySupplier> createAll() {
        List<TelemetrySupplier> suppliers = new ArrayList<>();
        for (ArgosSignature argosSignature : signatures) {
            try {
                TelemetrySupplier telemetrySupplier = create();
                addMeasurementIfAbsent(telemetrySupplier);
                addArgosSignatureToSupplierIfAbsent(telemetrySupplier, argosSignature);
                suppliers.add(telemetrySupplier);
            }catch(Exception e){
                //Do nothing
            }
        }
        return suppliers;
    }

    private void addMeasurementIfAbsent(TelemetrySupplier telemetrySupplier){
        if (telemetrySupplier.getMeasurement().equals(TelemetrySupplier.DEFAULT)) {
            telemetrySupplier.setMeasurement(getMeasurement());
        }
    }

    private void addArgosSignatureToSupplierIfAbsent(TelemetrySupplier telemetrySupplier, ArgosSignature argosSignature) {
        if (telemetrySupplier.getId().equals(TelemetrySupplier.DEFAULT)) {
            telemetrySupplier.setId(argosSignature.getId());
        }
        if (telemetrySupplier.getName().equals(TelemetrySupplier.DEFAULT)) {
            telemetrySupplier.setName(argosSignature.getName());
        }
        if (telemetrySupplier.getContainerId().equals(TelemetrySupplier.DEFAULT)) {
            telemetrySupplier.setContainerId(argosSignature.getContainerId());
        }
        if (telemetrySupplier.getType().equals(TelemetrySupplier.DEFAULT)) {
            telemetrySupplier.setType(argosSignature.getType());
        }
    }

    protected abstract TelemetrySupplier create();
}
