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

package com.im.njams.sdk.argos;

/**
 * This defines all basic attributes needed for an Argos Component.
 *
 * This class is just a POJO and can be used to handle components.
 */
public class ArgosComponent {

    // the id of this very component
    private String id;

    // the logical name (short name) of this component
    private String name;

    // the name of the container of this component; can be a server, or JVM name, or similar
    private String containerId;

    // the measurement name
    private String measurement;

    // the technology type
    private String type;

    public ArgosComponent(String id, String name, String containerId, String measurement,  String type) {
        this.id = id;
        this.name = name;
        this.containerId = containerId;
        this.measurement = measurement;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public String getMeasurement() {
        return measurement;
    }

    public void setMeasurement(String measurement) {
        this.measurement = measurement;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object signatureToCheck) {
        boolean isEqual = true;
        if (!super.equals(signatureToCheck)) {
            try {
                ArgosComponent argosSignatureToCheck = (ArgosComponent) signatureToCheck;
                if (!(argosSignatureToCheck.getContainerId().equals(this.getContainerId()) &&
                      argosSignatureToCheck.getId().equals(this.getId()) &&
                      argosSignatureToCheck.getName().equals(this.getName()) &&
                      argosSignatureToCheck.getMeasurement().equals(this.getMeasurement()) &&
                      argosSignatureToCheck.getType().equals(this.getType()))) {
                    isEqual = false;
                }
            } catch (Throwable t) {
                isEqual = false;
            }
        }
        return isEqual;
    }
}
