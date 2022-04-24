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
package com.im.njams.sdk.client;

import com.im.njams.sdk.njams.NjamsJobs;
import com.im.njams.sdk.settings.Settings;

/**
 * Configuration Entry for the LogMessageFlushTask for every Njams instance.
 * Holds the flush size and the flush interval values for every instance.
 */
public class LogMessageFlushTaskEntry {

    /**
     * Default flush size: 5MB
     */
    public static final String DEFAULT_FLUSH_SIZE = "5242880";
    /**
     * Default flush interval: 30s
     */
    public static final String DEFAULT_FLUSH_INTERVAL = "30";

    private NjamsJobs njamsJobs;
    private Long flushSize;
    private Long flushInterval;

    /**
     * Creates the objects by using the settings values of the NjamsJobs
     * instance, or the defaults.
     *
     * @param njamsJobs Initialize this entry with this NjamsJobs
     * @param settings Settings for getting the flush size and flush interval
     */
    public LogMessageFlushTaskEntry(NjamsJobs njamsJobs, Settings settings) {
        this.njamsJobs = njamsJobs;
        this.flushSize = Long.parseLong(
            settings.getProperty(Settings.PROPERTY_FLUSH_SIZE, DEFAULT_FLUSH_SIZE));
        this.flushInterval = Long.parseLong(settings
                .getProperty(Settings.PROPERTY_FLUSH_INTERVAL, DEFAULT_FLUSH_INTERVAL));
    }

    /**
     * @return the njamsJobs
     */
    public NjamsJobs getNjamsJobs() {
        return njamsJobs;
    }

    /**
     * @param njamsJobs the njamsJobs to set
     */
    public void setNjamsJobs(NjamsJobs njamsJobs) {
        this.njamsJobs = njamsJobs;
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
