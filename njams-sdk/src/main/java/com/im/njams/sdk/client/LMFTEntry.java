/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.client;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;

/**
 * Configuration Entry for the LogMessageFlushTask for every Njams instance.
 * Holds the flush size and the flush interval values for every instance.
 *
 * @author pnientiedt
 */
public class LMFTEntry {

    /**
     * Default flush size: 5MB
     */
    public static final String DEFAULT_FLUSH_SIZE = "5242880";
    /**
     * Default flush interval: 30s
     */
    public static final String DEFAULT_FLUSH_INTERVAL = "30";

    private Njams njams;
    private Long flushSize;
    private Long flushInterval;

    /**
     * Creates the objects by using the settings values of the Njams
     * instance, or the defaults.
     *
     * @param njams Initialize this entry with this Njams
     */
    public LMFTEntry(Njams njams) {
        this.njams = njams;
        this.flushSize = Long.parseLong(
            njams.getSettings().getProperty(NjamsSettings.PROPERTY_FLUSH_SIZE, DEFAULT_FLUSH_SIZE));
        this.flushInterval = Long.parseLong(njams.getSettings()
            .getProperty(NjamsSettings.PROPERTY_FLUSH_INTERVAL, DEFAULT_FLUSH_INTERVAL));
    }

    /**
     * @return the njams
     */
    public Njams getNjams() {
        return njams;
    }

    /**
     * @param njams the njams to set
     */
    public void setNjams(Njams njams) {
        this.njams = njams;
    }

    /**
     * @return the flushSize
     */
    public Long getFlushSize() {
        return flushSize;
    }

    /**
     * @param flushSize the flushSize to set
     */
    public void setFlushSize(Long flushSize) {
        this.flushSize = flushSize;
    }

    /**
     * @return the flushInterval
     */
    public Long getFlushInterval() {
        return flushInterval;
    }

    /**
     * @param flushInterval the flushInterval to set
     */
    public void setFlushInterval(Long flushInterval) {
        this.flushInterval = flushInterval;
    }
}
