/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.subagent.jvm;

import java.lang.management.GarbageCollectorMXBean;
import java.util.HashMap;
import java.util.Map;

public class GCStats {

    private long collections = 0;
    private long time = 0;
    private static Map<String, GCStats> previousReadings = new HashMap<>();

    public GCStats collectStatistics(GarbageCollectorMXBean bean) {
        GCStats previous = previousReadings.get(bean.getName());
        GCStats newStats = new GCStats();
        newStats.setCollections(bean.getCollectionCount());
        newStats.setTime(bean.getCollectionTime());
        // build delta
        this.setCollections(newStats.getCollections() - (previous != null ? previous.getCollections() : 0L));
        this.setTime(newStats.getTime() - (previous != null ? previous.getTime() : 0L));
        // store new readings
        previousReadings.put(bean.getName(), newStats);
        return this;
    }

    public long getCollections() {
        return collections;
    }

    public void setCollections(long collections) {
        this.collections = collections;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

}

