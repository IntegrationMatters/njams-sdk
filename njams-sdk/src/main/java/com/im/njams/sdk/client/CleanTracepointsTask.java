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

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.im.njams.sdk.NjamsMetadata;
import com.im.njams.sdk.communication.Sender;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.tracemessage.Activity;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.configuration.TracepointExt;

/**
 * Task for iterating over every Njams instance and check for outdated
 * tracepoints
 *
 * @author pnientiedt
 */
public class CleanTracepointsTask extends TimerTask {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CleanTracepointsTask.class);

    private static Map<String, CleanTracepointsTaskEntry> cleanTracePointsTaskEntries = new ConcurrentHashMap<>();

    private static Timer timer = null;

    static final int DELAY = 1000;

    static final int INTERVAL = 1000;

    /**
     * Start the CleanTracepointsTask if it is not started yet, and add the
     * given Njams instance to the task.
     *
     * @param instanceMetadata to distinguish between the different nJAMS instances
     * @param configuration to see were the tracepoints are
     * @param sender the sender to send the tracemessage with
     */
    public static synchronized void start(NjamsMetadata instanceMetadata, Configuration configuration, Sender sender) {
        if (instanceMetadata == null) {
            throw new NjamsSdkRuntimeException("Start: Njams is null");
        }
        if (instanceMetadata.clientPath == null) {
            throw new NjamsSdkRuntimeException("Start: Njams clientPath is null");
        }
        if (timer == null) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new CleanTracepointsTask(), DELAY, INTERVAL);
        }

        cleanTracePointsTaskEntries.put(instanceMetadata.clientPath.toString(), new CleanTracepointsTaskEntry(instanceMetadata, configuration, sender));
    }

    /**
     * Returns all active Njams instances, for testing purpose.
     *
     * @return list of njams instances
     */
    static List<CleanTracepointsTaskEntry> getCleanTracePointsTaskEntries() {
        return cleanTracePointsTaskEntries.values().stream().collect(Collectors.toList());
    }

    /**
     * Returns the Timer for testing purpose.
     *
     * @return timer
     */
    static Timer getTimer() {
        return timer;
    }

    /**
     * Removes the given Njams instance from the CleanTracepointsTask, and stops
     * it if not Njams instance is left.
     *
     * @param njamsMetadata to remove the correct instance of njams
     */
    public static synchronized void stop(NjamsMetadata njamsMetadata) {
        if (njamsMetadata == null) {
            throw new NjamsSdkRuntimeException("Stop: Njams is null");
        }
        if (njamsMetadata.clientPath == null) {
            throw new NjamsSdkRuntimeException("Stop: Njams clientPath is null");
        }
        cleanTracePointsTaskEntries.remove(njamsMetadata.clientPath.toString());
        if (cleanTracePointsTaskEntries.size() <= 0 && timer != null) {
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
            cleanTracePointsTaskEntries.values().forEach(cleanTracepointsTaskEntry -> checkNjams(cleanTracepointsTaskEntry, now));
        } catch (Exception e) {
            LOG.error("Error in {}", this.getClass().getName(), e);
        }
    }

    private void checkNjams(CleanTracepointsTaskEntry cleanTracepointsTaskEntry, LocalDateTime now) {
        checkNjams(cleanTracepointsTaskEntry.instanceMetadata, cleanTracepointsTaskEntry.configuration, cleanTracepointsTaskEntry.sender, now);
    }

    private void checkNjams(NjamsMetadata instanceMetadata, Configuration configuration, Sender sender, LocalDateTime now) {
        TraceMessageBuilder tmBuilder = new TraceMessageBuilder(instanceMetadata);
        configuration.getProcesses().entrySet()
                .forEach(processEntry -> checkProcess(configuration, processEntry, now, tmBuilder));
        TraceMessage msg = tmBuilder.build();
        if (msg != null) {
            sender.send(msg);
        }
    }

    private void checkProcess(Configuration configuration, Entry<String, ProcessConfiguration> processEntry,
            LocalDateTime now, TraceMessageBuilder tmBuilder) {
        //use itertor here, because we want to possibly modify the map itself
        Iterator<Entry<String, ActivityConfiguration>> it =
                processEntry.getValue().getActivities().entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, ActivityConfiguration> ae = it.next();
            if (checkActivity(configuration, processEntry, ae, now, tmBuilder)) {
                it.remove();
                configuration.save();
            }
        }
    }

    private boolean checkActivity(Configuration configuration, Entry<String, ProcessConfiguration> processEntry,
            Entry<String, ActivityConfiguration> ae, LocalDateTime now, TraceMessageBuilder tmBuilder) {
        ActivityConfiguration activity = ae.getValue();
        TracepointExt tracepoint = activity.getTracepoint();
        if (tracepoint != null && (tracepoint.getEndtime().isBefore(now) || tracepoint.iterationsExceeded())) {
            try {
                fillTraceMessageBuilder(processEntry, ae, tmBuilder);
                activity.setTracepoint(null);
                configuration.save();
                if (activity.isEmpty()) {
                    return true;
                }
            } catch (Exception e) {
                LOG.error("Error deleting tracepoint", e);
            }
        }
        return false;
    }

    private void fillTraceMessageBuilder(Entry<String, ProcessConfiguration> processEntry,
            Entry<String, ActivityConfiguration> ae, TraceMessageBuilder tmBuilder) {
        Activity act = new Activity();
        act.setActivityId(ae.getKey());
        act.setTracepoint(ae.getValue().getTracepoint());

        tmBuilder.addActivity(processEntry.getKey(), act);
    }

}
