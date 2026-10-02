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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.NjamsSender;

/**
 * LogMessageFlushTask flushes new content of jobs periodically into LogMessages
 *
 * @author stkniep
 */
public class LogMessageFlushTask extends TimerTask {

    private static final Logger LOG = LoggerFactory.getLogger(LogMessageFlushTask.class);

    private static final Map<Path, LMFTEntry> NJAMS_INSTANCES = new ConcurrentHashMap<>();

    private static Timer timer = null;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Adds a new Njams instance to the LogMessageFlushTask, and start the task
     * if it is not started yet
     *
     * @param njams Njams to add
     * @param sender the sender that the log messages of the given instance are sent through
     */
    public static synchronized void start(Njams njams, NjamsSender sender) {
        if (njams == null) {
            throw new NjamsSdkRuntimeException("Start: Njams is null");
        }
        if (njams.metadata().getClientPath() == null) {
            throw new NjamsSdkRuntimeException("Start: Njams clientPath is null");
        }

        if (timer == null) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new LogMessageFlushTask(), 1000, 1000);
        }

        NJAMS_INSTANCES.put(njams.metadata().getClientPath(), new LMFTEntry(njams, sender));
    }

    /**
     * Removes a given Njams instance from the LogMessageFlushTask, flushes all
     * jobs of the instance through the sender it was started with, and stops the
     * timer if no Njams instance is left to work on
     *
     * @param njams Njams instance to remove
     */
    public static synchronized void stop(Njams njams) {
        if (njams == null) {
            throw new NjamsSdkRuntimeException("Stop: Njams is null");
        }
        if (njams.metadata().getClientPath() == null) {
            throw new NjamsSdkRuntimeException("Stop: Njams clientPath is null");
        }
        // The entry is removed only after the final flush, because the flush looks up the sender in the registry.
        LMFTEntry entry = NJAMS_INSTANCES.get(njams.metadata().getClientPath());
        if (entry != null) {
            try {
                Njams stoppingNjams = entry.getNjams();
                stoppingNjams.jobs().getAll().forEach(job -> ((JobImpl) job).flush());
            } finally {
                NJAMS_INSTANCES.remove(njams.metadata().getClientPath());
            }
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
     * Returns the sender that the given instance was started with. Lock-free, so it can be used on the flush
     * path.
     *
     * @param njams the instance
     * @return the sender, or {@code null} if the instance is not started (or already stopped) in this task
     */
    static NjamsSender senderOf(Njams njams) {
        final LMFTEntry entry = NJAMS_INSTANCES.get(njams.metadata().getClientPath());
        return entry == null ? null : entry.getSender();
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
        entry.getNjams().jobs().getAll().forEach(job -> ((JobImpl) job).timerFlush(boundary, entry.getFlushSize()));
    }

}
