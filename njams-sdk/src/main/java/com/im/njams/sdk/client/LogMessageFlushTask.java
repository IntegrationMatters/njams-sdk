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
package com.im.njams.sdk.client;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.logmessage.JobImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LogMessageFlushTask flushes new content of jobs periodically into LogMessages
 *
 * @author stkniep
 */
public class LogMessageFlushTask extends TimerTask {

    private static final Logger LOG = LoggerFactory.getLogger(LogMessageFlushTask.class);

    private static final Map<String, LMFTEntry> NJAMS_INSTANCES = new HashMap<>();

    private static Timer timer = null;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Adds a new Njams instance to the LogMessageFlushTask, and start the task
     * if it is not started yet
     *
     * @param njams Njams to add
     */
    public static synchronized void start(Njams njams) {
        if (njams == null) {
            throw new NjamsSdkRuntimeException("Start: Njams is null");
        }
        if (njams.getClientPath() == null) {
            throw new NjamsSdkRuntimeException("Start: Njams clientPath is null");
        }

        if (timer == null) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new LogMessageFlushTask(), 1000, 1000);
        }

        NJAMS_INSTANCES.put(njams.getClientPath().toString(), new LMFTEntry(njams));
    }

    /**
     * Removes a given Njams instance from the LogMessageFlushTask, flushes all
     * jobs of the instance, and stops it the timer if no Njams instance is left
     * to work on
     *
     * @param njams Njams instance to remove
     */
    public static synchronized void stop(Njams njams) {
        if (njams == null) {
            throw new NjamsSdkRuntimeException("Stop: Njams is null");
        }
        if (njams.getClientPath() == null) {
            throw new NjamsSdkRuntimeException("Stop: Njams clientPath is null");
        }
        LMFTEntry entry = NJAMS_INSTANCES.remove(njams.getClientPath().toString());
        if (entry != null) {
            Njams stoppingNjams = entry.getNjams();
            stoppingNjams.getJobs().forEach(job -> ((JobImpl) job).flush());

        } else {
            LOG.warn(
                    "The LogMessageFlushTask hasn't been started before stopping for this instance: {}. Did not flush...",
                    njams);
        }
        if (NJAMS_INSTANCES.size() <= 0 && timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    /**
     * Run
     */
    @Override
    public void run() {
        try {
            synchronized (running) {
                if (running.get()) {
                    // task is already still running, skip next execution
                    LOG.debug("Task is already still running, skip next execution.",
                            LogMessageFlushTask.class.getSimpleName());
                    return;
                }
                running.set(true);
            }
            synchronized (LogMessageFlushTask.class) {
                NJAMS_INSTANCES.values().forEach(entry -> processNjams(entry));
            }
        } finally {
            running.set(false);
        }
    }

    private void processNjams(LMFTEntry entry) {
        LocalDateTime boundary = DateTimeUtility.now().minusSeconds(entry.getFlushInterval());
        entry.getNjams().getJobs().forEach(job -> processJob(entry, job, boundary));
    }

    private void processJob(LMFTEntry entry, final Job jobParam, LocalDateTime boundary) {
        JobImpl job = (JobImpl) jobParam;
        // only send updates automatically, if a change has been
        // made to the job between individual send events.
        LOG.trace("Job {}: lastPush: {}, age: {}, size: {}", job, job.getLastFlush(),
                Duration.between(job.getLastFlush(), DateTimeUtility.now()), job.getEstimatedSize());
        if ((job.getLastFlush().isBefore(boundary) || job.getEstimatedSize() > entry.getFlushSize())
                && (!job.getActivities().isEmpty() || !job.getAttributes().isEmpty() || job.getEndTime() != null)) {
            job.flush();
            LOG.debug("Flush job {}", job);
        }

    }

}
