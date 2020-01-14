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

package com.im.njams.sdk.argos.jvm;

import java.util.HashMap;
import java.util.Map;

import com.im.njams.sdk.argos.ArgosComponent;
import com.im.njams.sdk.argos.ArgosMetric;

/**
 * This is a concrete implementation of an @see {@link ArgosMetric} for JMV statistics
 * <p>
 * It is used in @see {@link JVMCollector}
 */
public class JVMMetric extends ArgosMetric {

    private long heapCommitted = 0;
    private long heapInit = 0;
    private long heapMax = 0;
    private long heapUsed = 0;
    private long heapFree = 0;
    private long offCommitted = 0;
    private long offInit = 0;
    private long offMax = 0;
    private long offUsed = 0;
    private long offFree = 0;
    private long threadCount = 0;
    private int pid = 0;
    private Map<String, GCStats> gc = new HashMap<>();
    private OsProcessStats processStats;

    public JVMMetric(ArgosComponent component) {
        super(component.getId(), component.getName(), component.getContainerId(), component.getMeasurement(), component
                .getType());
    }

    public long getHeapCommitted() {
        return heapCommitted;
    }

    public void setHeapCommitted(long heapCommitted) {
        this.heapCommitted = heapCommitted;
    }

    public long getHeapInit() {
        return heapInit;
    }

    public void setHeapInit(long heapInit) {
        this.heapInit = heapInit;
    }

    public long getHeapMax() {
        return heapMax;
    }

    public void setHeapMax(long heapMax) {
        this.heapMax = heapMax;
    }

    public long getHeapUsed() {
        return heapUsed;
    }

    public void setHeapUsed(long heapUsed) {
        this.heapUsed = heapUsed;
    }

    public long getOffCommitted() {
        return offCommitted;
    }

    public void setOffCommitted(long offCommitted) {
        this.offCommitted = offCommitted;
    }

    public long getOffInit() {
        return offInit;
    }

    public void setOffInit(long offInit) {
        this.offInit = offInit;
    }

    public long getOffMax() {
        return offMax;
    }

    public void setOffMax(long offMax) {
        this.offMax = offMax;
    }

    public long getOffUsed() {
        return offUsed;
    }

    public void setOffUsed(long offUsed) {
        this.offUsed = offUsed;
    }

    public Map<String, GCStats> getGc() {
        return gc;
    }

    public void setGc(Map<String, GCStats> gc) {
        this.gc = gc;
    }

    public long getHeapFree() {
        return heapFree;
    }

    public void setHeapFree(long heapFree) {
        this.heapFree = heapFree;
    }

    public long getOffFree() {
        return offFree;
    }

    public void setOffFree(long offFree) {
        this.offFree = offFree;
    }

    public long getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(long threadCount) {
        this.threadCount = threadCount;
    }

    public OsProcessStats getProcessStats() {
        return processStats;
    }

    public void setProcessStats(OsProcessStats processStats) {
        this.processStats = processStats;
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

}
