/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.logmessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This monitor synchronizes the flushing thread with the threads that are
 * working on the job and its activities.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
class FlushMonitor {

    //The readLock, used for setting and adding attributes
    private final Lock readLock;
    //The writeLock, used for flushing
    private final Lock writeLock;

    //The job where the FlushMonitor belongs to.
    private final JobImpl job;

    /**
     * This constructor creates a new FlushMonitor for the provided job.
     *
     * @param job the job to monitor.
     */
    public FlushMonitor(JobImpl job) {
        this.job = job;
        ReadWriteLock flushLocks = new ReentrantReadWriteLock(false);
        readLock = flushLocks.readLock();
        writeLock = flushLocks.writeLock();
    }

    /**
     * This method adds an attribute threadsafe to the job. It will be masked
     * aswell.
     *
     * @param key the key of the attribute
     * @param value the value of the attribute
     */
    public void addAttributeToJob(final String key, final String value) {
        readLock.lock();
        try {
            String maskedValue = this.maskData(value);
            this.addAttributeToJobSynchronized(key, maskedValue);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * This method adds the attribute to the job by calling the
     * synchronizedAddAttribute method of JobImpl.
     *
     * @param key the key of the attribute
     * @param value the value of the attribute
     */
    private void addAttributeToJobSynchronized(final String key, final String value) {
        job.synchronizedAddAttribute(key, value);
    }

    /**
     * This method adds an attribute threadsafe to the activity and its job. It
     * will be masked aswell.
     *
     * @param key the key of the attribute
     * @param value the value of the attribute
     * @param act the Activity where the attribute will be set.
     */
    public void addAttributeToActivity(final String key, final String value, final ActivityImpl act) {
        readLock.lock();
        try {
            String maskedValue = this.maskData(value);
            this.addAttributeToActivitySynchronized(key, maskedValue, act);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * This method adds the attribute to the job by calling
     * addAttributeToJobSynchronized and to the activity by calling
     * synchronizedAddAttribute of the ActivityImpl.
     *
     * @param key the key of the attribute
     * @param value the value of the attribute
     * @param act the Activity where the attribute will be set.
     */
    private void addAttributeToActivitySynchronized(final String key, final String value, ActivityImpl act) {
        act.synchronizedAddAttribute(key, value);
        this.addAttributeToJobSynchronized(key, value);
    }

    /**
     * This method sets the attributes of the activity but adds them its the
     * job. The values will be masked aswell.
     *
     * @param attributes the attributes to set to the activity and to add to the
     * job.
     * @param act the activity where the attributes will be set.
     */
    public void addAttributesToActivity(Map<String, String> attributes, final ActivityImpl act) {
        readLock.lock();
        try {
            Map<String, String> maskedAttributes = new HashMap<>();
            attributes.keySet().forEach(key -> maskedAttributes.put(key, this.maskData(attributes.get(key))));
            maskedAttributes.forEach((key, value) -> addAttributeToActivity(key, value, act));
        } finally {
            readLock.unlock();
        }

    }

    /**
     * Get the writeLock for the jobs flush().
     *
     * @return the writelock.
     */
    Lock getWriteLock() {
        return writeLock;
    }

    /**
     * This method masks the inputString
     *
     * @param value the string to mask
     * @return the masked string
     */
    private String maskData(String value) {
        return DataMasking.maskString(value);
    }
}
