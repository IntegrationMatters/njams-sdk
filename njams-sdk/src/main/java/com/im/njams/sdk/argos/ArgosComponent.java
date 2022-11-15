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

import java.util.Objects;

/**
 * This defines all basic attributes needed for an Argos Component.
 * <p>
 * This class is just a POJO and can be used to handle components.
 */
public class ArgosComponent {

    // the id of this component
    private String id;

    // the logical name (short name) of this component
    private String name;

    // the name of the container of this component; can be a server, or JVM name, or similar
    private String containerId;

    // the measurement name; for example 'jvm' for component, related to a java VM.
    private String measurement;

    // the technology type like: server, process, tibbw6, tipappnode, tibappspace etc.
    private String type;

    /**
     * Constructor for {@link ArgosComponent}
     *
     * @param id          the id of this component
     * @param name        the logical name (short name) of this component
     * @param containerId the name of the container of this component; can be a server, or JVM name, or similar
     * @param measurement the measurement name; for example 'jvm' for component, related to a java VM.
     * @param type        the technology type like: server, process, tibbw6, tipappnode, tibappspace etc.
     */
    public ArgosComponent(String id, String name, String containerId, String measurement, String type) {
        this.id = id;
        this.name = name;
        this.containerId = containerId;
        this.measurement = measurement;
        this.type = type;
    }

    /**
     * @return the unique id of this component.
     */
    public String getId() {
        return id;
    }

    /**
     * @return the logical name (short name) of this component
     */
    public String getName() {
        return name;
    }

    /**
     * @return the name of the container of this component; can be a server, or JVM name, or similar
     */
    public String getContainerId() {
        return containerId;
    }

    /**
     * @return the measurement name; for example 'jvm' for component, related to a java VM.
     */
    public String getMeasurement() {
        return measurement;
    }

    /**
     * @return the technology type like: server, process, tibbw6, tipappnode, tibappspace etc.
     */
    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object signatureToCheck) {
        if (this == signatureToCheck) {
            return true;
        }
        if (signatureToCheck == null) {
            return false;
        }
        if (getClass() != signatureToCheck.getClass()) {
            return false;
        }

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
            } catch (Exception t) {
                isEqual = false;
            }
        }
        return isEqual;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, containerId, measurement, type);
    }
}
