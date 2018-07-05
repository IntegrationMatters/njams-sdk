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

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.configuration.TracepointExt;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import org.slf4j.LoggerFactory;

/**
 * Task for iterating over every Njams instance and check for outdated
 * tracepoints
 *
 * @author pnientiedt
 */
public class CleanTracepointsTask extends TimerTask {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CleanTracepointsTask.class);

    private static Map<String, Njams> njamsInstances = new HashMap<>();

    private static Timer timer = null;

    /**
     * Start the CleanTracepointsTask if it is not started yet, and add the
     * given Njams instance to the task.
     *
     * @param njams to add
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
            timer.scheduleAtFixedRate(new CleanTracepointsTask(), 1000, 1000);
        }

        njamsInstances.put(njams.getClientPath().toString(), njams);
    }

    /**
     * Removes the given Njams instance from the CleanTracepointsTask, and stops
     * it if not Njams instance is left.
     *
     * @param njams to remove
     */
    public static synchronized void stop(Njams njams) {
        if (njams == null) {
            throw new NjamsSdkRuntimeException("Stop: Njams is null");
        }
        if (njams.getClientPath() == null) {
            throw new NjamsSdkRuntimeException("Stop: Njams clientPath is null");
        }
        njamsInstances.remove(njams.getClientPath().toString());
        if (njamsInstances.size() <= 0) {
            timer.cancel();
            timer = null;
        }
    }

    /**
     * If started, runs every seconds, and checks if something has to be done
     * for every Njams instance.
     */
    @Override
    public void run() {
        try {
            LocalDateTime now = DateTimeUtility.now();
            njamsInstances.values().forEach(njams -> checkNjams(njams, now));
        } catch (Exception e) {
            LOG.error("Error in {}", this.getClass().getName(), e);
        }
    }

    private void checkNjams(Njams njams, LocalDateTime now) {
        Configuration configuration = njams.getConfiguration();
        configuration.getProcesses().values().forEach(process -> checkProcess(configuration, process, now));
    }

    private void checkProcess(Configuration configuration, ProcessConfiguration process, LocalDateTime now) {
        //use itertor here, because we want to possibly modify the map itself
        Iterator<Entry<String, ActivityConfiguration>> it = process.getActivities().entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, ActivityConfiguration> ae = it.next();
            if (checkActivity(configuration, process, ae, now)) {
                it.remove();
                configuration.save();
            }

        }
    }

    private boolean checkActivity(Configuration configuration, ProcessConfiguration process, Entry<String, ActivityConfiguration> ae, LocalDateTime now) {
        ActivityConfiguration activity = ae.getValue();
        TracepointExt tracepoint = activity.getTracepoint();
        if (tracepoint != null && (tracepoint.getEndtime().isBefore(now) || tracepoint.iterationsExceeded())) {
            try {
                activity.setTracepoint(null);
                configuration.save();
                //TODO: send notification to server that the tracepoint has been deleted
                if (activity.isEmpty()) {
                    return true;
                }
            } catch (Exception e) {
                LOG.error("Error deleting tracepoint", e);
            }
        }
        return false;
    }

}
