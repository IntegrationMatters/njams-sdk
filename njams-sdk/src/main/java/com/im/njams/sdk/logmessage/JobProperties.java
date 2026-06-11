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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns the internal properties of a {@link Job}. Properties are <b>client-local only</b>:
 * they are never transmitted to the nJAMS server — arbitrary key/value storage for the
 * instrumenting client. This is deliberately a different facet than {@link JobAttributes},
 * whose content is sent to the server with the job's log messages.
 * Obtain via {@code job.properties()}.
 */
public final class JobProperties {

    // internal properties, shall not go to any message
    private final Map<String, Object> properties = new LinkedHashMap<>();

    JobProperties() {
        // created by JobImpl only
    }

    /**
     * Gets a properties value.
     *
     * @param key name of the property
     * @return Properties value or <b>null</b>
     */
    public Object get(final String key) {
        return properties.get(key);
    }

    /**
     * Checks whether the job has a property with a given name.
     *
     * @param key name of the property
     * @return <b>true</b> if and only if a property with the given name exists.
     */
    public boolean has(final String key) {
        return properties.containsKey(key);
    }

    /**
     * Sets a properties value.
     *
     * @param key   name of the property
     * @param value value of the property
     */
    public void set(final String key, final Object value) {
        properties.put(key, value);
    }

    /**
     * Removes the property with a given name.
     *
     * @param key name of the property
     * @return Previous value of the property (if it existed) or else <b>null</b>.
     */
    public Object remove(final String key) {
        return properties.remove(key);
    }
}
