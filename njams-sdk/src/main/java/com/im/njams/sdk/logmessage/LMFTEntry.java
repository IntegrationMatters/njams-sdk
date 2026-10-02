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
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.logmessage;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.NjamsSender;

/**
 * Configuration Entry for the LogMessageFlushTask for every Njams instance.
 * Holds the flush size and the flush interval values, and the sender that the instance's log messages are sent
 * through.
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
    private final NjamsSender sender;
    private Long flushSize;
    private Long flushInterval;

    /**
     * Creates the objects by using the settings values of the Njams
     * instance, or the defaults.
     *
     * @param njams Initialize this entry with this Njams
     * @param sender the sender that the log messages of the given instance are sent through
     */
    public LMFTEntry(Njams njams, NjamsSender sender) {
        this.njams = njams;
        this.sender = sender;
        flushSize = njams.getSettings().getLong(
                NjamsSettings.PROPERTY_FLUSH_SIZE, Long.parseLong(DEFAULT_FLUSH_SIZE));
        flushInterval = njams.getSettings().getLong(
                NjamsSettings.PROPERTY_FLUSH_INTERVAL, Long.parseLong(DEFAULT_FLUSH_INTERVAL));
    }

    /**
     * @return the njams
     */
    public Njams getNjams() {
        return njams;
    }

    /**
     * @return the sender that the log messages of the instance are sent through
     */
    public NjamsSender getSender() {
        return sender;
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
