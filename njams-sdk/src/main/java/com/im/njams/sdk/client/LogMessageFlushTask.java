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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import com.im.njams.sdk.NjamsJobs;
import com.im.njams.sdk.NjamsMetadata;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.logmessage.JobImpl;

/**
 * LogMessageFlushTask flushes new content of jobs periodically into LogMessages
 *
 * @author stkniep
 */
public class LogMessageFlushTask extends TimerTask {

    private static final Logger LOG = LoggerFactory.getLogger(LogMessageFlushTask.class);

    private static final Map<String, LogMessageFlushTaskEntry> NJAMS_INSTANCES = new HashMap<>();

    private static Timer timer = null;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Adds a new Njams instance to the LogMessageFlushTask, and start the task
     * if it is not started yet
     *
     * @param njamsMetadata Njams metadata for one instance
     * @param njamsJobs Jobs for the njams instance to flush
     * @param settings Settings to know if when to flush
     */
    public static synchronized void start(NjamsMetadata njamsMetadata, NjamsJobs njamsJobs, Settings settings) {
        if (njamsMetadata == null) {
            throw new NjamsSdkRuntimeException("Start: Njams Metadata is null");
        }
        if (njamsMetadata.clientPath == null) {
            throw new NjamsSdkRuntimeException("Start: Njams clientPath is null");
        }

        if (timer == null) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new LogMessageFlushTask(), 1000, 1000);
        }

        NJAMS_INSTANCES.put(njamsMetadata.clientPath.toString(), new LogMessageFlushTaskEntry(njamsJobs, settings));
    }

    /**
     * Removes a given Njams instance from the LogMessageFlushTask, flushes all
     * jobs of the instance, and stops it the timer if no Njams instance is left
     * to work on
     *
     * @param njamsMetadata NjamsMetadata as identifier for the njams instance to remove
     */
    public static synchronized void stop(NjamsMetadata njamsMetadata) {
        if (njamsMetadata == null) {
            throw new NjamsSdkRuntimeException("Stop: Njams Metadata is null");
        }
        if (njamsMetadata.clientPath == null) {
            throw new NjamsSdkRuntimeException("Stop: Njams clientPath is null");
        }
        LogMessageFlushTaskEntry entry = NJAMS_INSTANCES.remove(njamsMetadata.clientPath.toString());
        if (entry != null) {
            NjamsJobs njamsJobs = entry.getNjamsJobs();
            njamsJobs.get().forEach(job -> ((JobImpl) job).flush());

        } else {
            LOG.warn(
                    "The LogMessageFlushTask hasn't been started before stopping for this instance: {}. Did not flush...",
                njamsMetadata.clientPath);
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

    private void processNjams(LogMessageFlushTaskEntry entry) {
        LocalDateTime boundary = DateTimeUtility.now().minusSeconds(entry.getFlushInterval());
        entry.getNjamsJobs().get().forEach(job -> ((JobImpl) job).timerFlush(boundary, entry.getFlushSize()));
    }

}
