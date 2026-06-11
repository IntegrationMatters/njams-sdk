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
package com.im.njams.sdk.logmessage;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;

/**
 * Owns the attributes of a {@link Job}. Attributes are <b>wire data</b>: they are
 * transmitted to the nJAMS server with the next log message of the job. This is
 * deliberately a different facet than {@link JobProperties}, which is client-local
 * key/value storage that is never transmitted. Obtain via {@code job.attributes()}.
 */
public final class JobAttributes {

    private final JobImpl jobImpl;

    private final Map<String, String> attributes = new ConcurrentHashMap<>();
    private final Map<String, String> flushedAttributes = new ConcurrentHashMap<>();

    JobAttributes(JobImpl jobImpl) {
        this.jobImpl = jobImpl;
    }

    /**
     * Adds an attribute to this job. The value is masked according to the data-masking
     * configuration and limited to the configured payload limits before it is stored.
     *
     * @param key   the key to set
     * @param value the value to set; <code>null</code> values are ignored
     */
    public void add(final String key, String value) {
        if (value == null) {
            return;
        }
        String limitKey = JobImpl.limitLength("attributeName", key, 500);
        String maskedValue = DataMasking.maskString(jobImpl.limitPayload(value));
        synchronized (attributes) {
            attributes.put(limitKey, maskedValue);
        }
        final int size = (limitKey == null ? 0 : limitKey.length())
                + (maskedValue == null ? 0 : maskedValue.length());
        jobImpl.addToEstimatedSize(size);
    }

    /**
     * Returns the attribute value for the given name.
     *
     * @param name attribute name
     * @return attribute value, or <code>null</code> if not present
     */
    public String get(final String name) {
        String val = attributes.get(name);
        if (val != null) {
            return val;
        }
        return flushedAttributes.get(name);
    }

    /**
     * Returns a detached copy of all attributes for this job. I.e., any modification on the
     * returned map has no effect on this job instance!
     *
     * @return map of all attributes
     */
    public Map<String, String> getAll() {
        final Map<String, String> attr = new TreeMap<>(flushedAttributes);
        attr.putAll(attributes);
        return attr;
    }

    /**
     * Returns whether the job contains an attribute for the given name.
     *
     * @param name attribute name to check
     * @return true if found, false if not found
     */
    public boolean has(final String name) {
        return attributes.containsKey(name) || flushedAttributes.containsKey(name);
    }

    /** Moves all pending attributes into the given log message; called while flushing. */
    void flushInto(LogMessage logMessage) {
        synchronized (attributes) {
            for (Entry<String, String> e : attributes.entrySet()) {
                logMessage.addAtribute(e.getKey(), e.getValue());
                flushedAttributes.put(e.getKey(), e.getValue());
                attributes.remove(e.getKey());
            }
        }
    }

    /** Whether there are attributes pending for the next flush. */
    boolean isEmpty() {
        return attributes.isEmpty();
    }
}
