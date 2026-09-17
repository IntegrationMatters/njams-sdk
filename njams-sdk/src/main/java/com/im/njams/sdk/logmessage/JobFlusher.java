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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.PluginDataItem;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.model.ProcessModel;

/**
 * Assembles and sends the log messages of a job: flush decision, suppression checks,
 * message creation, flush bookkeeping (counter, last-flush time, estimated size, plugin
 * data items). Methods take the owning {@link JobImpl} as parameter so proxied instances
 * (test spies) stay the receiver of self-calls like {@code owner.flush()}.
 */
final class JobFlusher {

    private static final Logger LOG = LoggerFactory.getLogger(JobFlusher.class);

    private final ProcessModel processModel;
    private final JobActivities activities;
    private final JobAttributes attributes;
    private final JobMetadata metadata;
    private final JobTracing tracing;
    private final JobRuntimeConfig runtimeConfig;
    private final JobTruncation truncation;
    private final Object lock;

    /*
     * counts how many flushes have been made. Used in LogMessage as messageNo
     */
    private final AtomicInteger flushCounter = new AtomicInteger();

    private final List<PluginDataItem> pluginDataItems = new ArrayList<>();

    private LocalDateTime lastFlush = DateTimeUtility.now();

    //1000 for headers and co
    private long estimatedSize = 1000L;

    JobFlusher(ProcessModel processModel, JobActivities activities, JobAttributes attributes, JobMetadata metadata,
            JobTracing tracing, JobRuntimeConfig runtimeConfig, JobTruncation truncation, Object lock) {
        this.processModel = processModel;
        this.activities = activities;
        this.attributes = attributes;
        this.metadata = metadata;
        this.tracing = tracing;
        this.runtimeConfig = runtimeConfig;
        this.truncation = truncation;
        this.lock = lock;
    }

    /**
     * Called by a periodic timer to flush the job if it's due. The actual flush goes through
     * {@code owner.flush()} so that proxied instances observe the call.
     */
    void timerFlush(JobImpl owner, LocalDateTime sentBefore, long flushSize) {
        if (!owner.hasStarted()) {
            LOG.trace("Skip timer flush. Job {} is not started.", owner);
            return;
        }
        // only send updates automatically, if a change has been
        // made to the job between individual send events.
        final long currentSize = getEstimatedSize();
        LOG.trace("Job {}: lastPush: {}, age: {}, size: {}", owner, lastFlush,
                Duration.between(lastFlush, DateTimeUtility.now()), currentSize);
        if ((lastFlush.isBefore(sentBefore) || currentSize > flushSize)
                && (!attributes.isEmpty() || owner.getEndTime() != null || activities.hasActivityToSend())) {
            LOG.debug("Flush by timer: {}", owner);
            owner.flush();
        }
    }

    /**
     * Flushes a logMessage to the server if all the preconditions are fulfilled.
     */
    void flush(JobImpl owner) {
        synchronized (lock) {
            if (owner.isDiscarded()) {
                // SDK-465: a discarded job is never sent. Belt-and-suspenders with the registry
                // removal in discard(): covers a timer flush that captured the reference first.
                LOG.trace("Skip flush. Job {} has been discarded.", owner);
                return;
            }
            if (!owner.hasStarted()) {
                LOG.warn("Not flushing job with logId: {}: the job has not been started"
                        + " - a job that was never started is never sent to the nJAMS server.", owner.getLogId());
                return;
            }
            boolean suppressed = mustBeSuppressed(owner);
            if (!suppressed) {
                flushCounter.incrementAndGet();
                lastFlush = DateTimeUtility.now();
                LogMessage logMessage = createLogMessage(owner);
                addToLogMessageAndCleanup(owner, logMessage);
                logMessage.setSentAt(lastFlush);
                processModel.getNjams().getSender()
                        .send(logMessage, processModel.getNjams().getClientSessionId());
                // clean up jobImpl
                pluginDataItems.clear();
                calculateEstimatedSize();
            }
        }
    }

    private boolean mustBeSuppressed(JobImpl owner) {
        synchronized (lock) {
            // Do not send if one of the conditions is true.
            if (isLogModeNone() || isLogModeExclusiveAndNotInstrumented() || isExcludedProcess()
                    || isLogLevelHigherAsJobStateAndHasNoTraces(owner)) {
                LOG.debug("Job not flushed: Engine Mode: {} // Job's log level: {}, "
                        + "configured level: {} // is excluded: {} // has traces: {}", runtimeConfig.logMode,
                        owner.getStatus(), runtimeConfig.logLevel, runtimeConfig.exclude, tracing.isTraces());
                //delete not running activities
                activities.removeNotRunning();
                calculateEstimatedSize();
                LOG.debug("mustBeSuppressed: true");
                return true;
            }
            LOG.debug("mustBeSuppressed: false");
            return false;
        }
    }

    private boolean isLogModeNone() {
        if (runtimeConfig.logMode == LogMode.NONE) {
            LOG.debug("isLogModeNone: true");
            return true;
        }
        return false;
    }

    private boolean isLogModeExclusiveAndNotInstrumented() {
        if (runtimeConfig.logMode == LogMode.EXCLUSIVE && !tracing.isInstrumented()) {
            LOG.debug("isLogModeExclusiveAndNotInstrumented: true");
            return true;
        }
        return false;
    }

    private boolean isExcludedProcess() {
        if (runtimeConfig.exclude) {
            LOG.debug("isExcludedProcess: true");
            return true;
        }
        return false;
    }

    private boolean isLogLevelHigherAsJobStateAndHasNoTraces(JobImpl owner) {
        boolean b = owner.hasStarted() && owner.getMaxSeverity().getValue() < runtimeConfig.logLevel.value()
                && !tracing.isTraces();

        if (LOG.isDebugEnabled()) {
            LOG.debug("hasStarted[{}] && maxSeverity[{}] < logLevel[{}] && !traces[{}] == {}", owner.hasStarted(),
                    owner.getMaxSeverity().getValue(), runtimeConfig.logLevel.value(), tracing.isTraces(), b);
        }
        return b;
    }

    /**
     * Creates the LogMessage that will be sent to the server and fills it with the
     * attributes of the job.
     */
    private LogMessage createLogMessage(JobImpl owner) {
        LOG.trace("Creating LogMessage for job with logId: {}", owner.getLogId());
        LogMessage logMessage = new LogMessage();
        logMessage.setBusinessEnd(metadata.getBusinessEnd());
        logMessage.setBusinessStart(metadata.getBusinessStart());
        logMessage.setCategory(processModel.getNjams().getCategory());
        logMessage.setCorrelationLogId(metadata.getCorrelationLogId());
        logMessage.setExternalLogId(metadata.getExternalLogId());
        logMessage.setJobEnd(owner.getEndTime());
        logMessage.setJobId(owner.getJobId());
        logMessage.setJobStart(owner.getStartTime());
        logMessage.setLogId(owner.getLogId());
        logMessage.setMachineName(processModel.getNjams().getMachine());
        logMessage.setMaxSeverity(owner.getMaxSeverity().getValue());
        logMessage.setMessageNo(flushCounter.get());
        logMessage.setObjectName(metadata.getBusinessObject());
        logMessage.setParentLogId(metadata.getParentLogId());
        logMessage.setPath(processModel.getPath().toString());
        logMessage.setProcessName(processModel.getName());
        logMessage.setStatus(owner.getStatus().getValue());
        logMessage.setServiceName(metadata.getBusinessService());
        logMessage.setClientVersion(processModel.getNjams().getClientVersion());
        logMessage.setSdkVersion(processModel.getNjams().getSdkVersion());
        logMessage.setRuntimeVersion(processModel.getNjams().getRuntimeVersion());

        pluginDataItems.forEach(i -> logMessage.addPluginDataItem(i));
        return logMessage;
    }

    private void addToLogMessageAndCleanup(JobImpl owner, LogMessage logMessage) {
        attributes.flushInto(logMessage);
        synchronized (lock) {
            // If this is the final message being sent, and truncate-on-success is selected and this job was
            // successful, truncate all activities w/o events.
            boolean finishedWithSuccess = logMessage.getJobEnd() != null && owner.getStatus() == JobStatus.SUCCESS;

            //add all to logMessage
            for (Activity activity : activities.internalValues()) {
                if (activities.shouldFlush(activity)) {
                    if (truncation.checkTruncating(activity, finishedWithSuccess)) {
                        logMessage.addActivity(activity);
                    } else {
                        logMessage.setTruncated(true);
                    }
                }
            }
            //remove finished
            activities.removeNotRunning();
        }
    }

    void calculateEstimatedSize() {
        synchronized (lock) {
            estimatedSize = 1000 + activities.internalValues().stream()
                    .mapToLong(a -> ((ActivityImpl) a).getEstimatedSize()).sum();
        }
    }

    LocalDateTime getLastFlush() {
        return lastFlush;
    }

    long getEstimatedSize() {
        synchronized (lock) {
            return estimatedSize;
        }
    }

    void addToEstimatedSize(long estimatedSize) {
        // Guarded by the same lock as every other access to estimatedSize. Parallel activities in a
        // shared job add concurrently; an unsynchronized += loses increments and undercounts the size.
        synchronized (lock) {
            this.estimatedSize += estimatedSize;
        }
    }

    void addPluginDataItem(PluginDataItem pluginDataItem) {
        pluginDataItems.add(pluginDataItem);
    }
}
