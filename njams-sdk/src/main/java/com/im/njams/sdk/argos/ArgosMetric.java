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

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for Argos Metrics. Contains the infos about related {@link ArgosComponent}
 * <p>
 * Extend this class and use it in your @see {@link ArgosCollector}
 * Provide all attributes you want to send to Argos.
 */
public abstract class ArgosMetric {

    // the id of the related component
    private String id;

    // the logical name (short name) of the related component
    private String name;

    // the name of the container of the related component
    private String containerid;

    // the measurement name of the related component
    private String measurement;

    // the technology type of the related component
    private String type;

    // optional map of tags for this metric.
    private Map<String, Object> tags = new HashMap<>();

    /**
     * Constructor for {@link ArgosMetric}
     *
     * @param argosComponent the related component
     */
    public ArgosMetric(ArgosComponent argosComponent) {
        this.id = argosComponent.getId();
        this.name = argosComponent.getName();
        this.containerid = argosComponent.getContainerId();
        this.measurement = argosComponent.getMeasurement();
        this.type = argosComponent.getType();
    }

    /**
     * Constructor for {@link ArgosMetric}
     *
     * @param id          the id of the related component
     * @param name        the logical name (short name) of the related component
     * @param containerid the name of the container of the related component
     * @param measurement the measurement name of the related component
     * @param type        the technology type of the related component
     */
    public ArgosMetric(String id, String name, String containerid, String measurement, String type) {
        this.id = id;
        this.name = name;
        this.containerid = containerid;
        this.measurement = measurement;
        this.type = type;
    }

    /**
     * @return the id of the related component
     */
    public String getId() {
        return id;
    }

    /**
     * @return the logical name (short name) of the related component
     */
    public String getName() {
        return name;
    }

    /**
     * @return the name of the container of the related component
     */
    public String getContainerId() {
        return containerid;
    }

    /**
     * @return the measurement name of the related component
     */
    public String getMeasurement() {
        return measurement;
    }

    /**
     * @return the technology type of the related component
     */
    public String getType() {
        return type;
    }

    /**
     * @return a map of tags
     */
    public Map<String, Object> getTags() {
        return tags;
    }

    /**
     * Replace all tags with tag map
     *
     * @param tags the tag map
     */
    public void setTags(Map<String, Object> tags) {
        this.tags = tags;
    }

    /**
     * Add a single tag to the map of tags
     *
     * @param key   of the tag
     * @param value of the tag
     */
    public void addTag(String key, Object value) {
        tags.put(key, value);
    }
}
