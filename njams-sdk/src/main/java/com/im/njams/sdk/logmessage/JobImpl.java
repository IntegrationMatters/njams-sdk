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
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.faizsiegeln.njams.messageformat.v4.logmessage.PluginDataItem;
import com.im.njams.sdk.DataMasker;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.TracepointExt;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

/**
 * This represents an instance of a process/flow etc in engine to monitor.
 *
 * @author bwand
 */
public class JobImpl implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(JobImpl.class);

    /**
     * This messages is used when payload has been discard because its size limit has been exceeded.
     */
    public static final String PAYLOAD_DISCARDED_MESSAGE = "[Discarded by client due to configured payload limits]";
    /**
     * This messages is used as suffix when payload has been truncated because its size limit has been exceeded.
     */
    public static final String PAYLOAD_TRUNCATED_SUFFIX = "... [Truncated by client due to configured payload limits]";

    /**
     * Default flush size: 5MB
     */
    public static final String DEFAULT_FLUSH_SIZE = "5242880";
    /**
     * Default flush interval: 30s
     */
    public static final String DEFAULT_FLUSH_INTERVAL = "30";

    /**
     * Maximum length for string values for size restricted fields.
     */
    public static final int MAX_VALUE_LIMIT = 2000;

    // Job attribute that marks a job as replayable; cleared to "false" when start data is dropped (SDK-420).
    private static final String RECORDED_ATTRIBUTE = "$njams_recorded";

    private final ProcessModel processModel;
    private final Njams njams;

    private final String jobId;

    private final String logId;
    /*
     * The latest status of the job, set by any event. Mutated under activitiesLock (see
     * setStatusAndSeverity and end); volatile so that readers see updates without locking.
     */
    private volatile JobStatus lastStatus = JobStatus.CREATED;

    /*
     * Maximum severity recorded. Mutated under activitiesLock (the update is a read-modify-write
     * that must be atomic); volatile so that readers see updates without locking.
     */
    private volatile JobStatus maxSeverity = JobStatus.SUCCESS;

    // guards the activity registry, the truncation state and the job status (lastStatus/maxSeverity)
    final Object activitiesLock = new Object();

    private final JobActivities activities = new JobActivities(this, activitiesLock);

    /*
     * job level attributes
     */
    private final JobAttributes attributes = new JobAttributes(this);

    private final JobFlusher flusher;

    // kept on JobImpl (not in JobActivities): frozen tests access this field directly
    boolean hasOrHadStartActivity;

    // volatile: read without locking by requireNotFinished/getStatus on any thread; written under
    // activitiesLock in end(boolean) and discard(). A reader observing finished==true also observes the
    // final lastStatus.
    private volatile boolean finished = false;

    // SDK-465: set under activitiesLock by discard(); read by the flusher to guarantee a discarded
    // job is never sent, even if a timer flush captured the job reference before discard removed it
    // from the registry. volatile so the flusher sees the update without holding a reference race.
    private volatile boolean discarded = false;

    private final JobRuntimeConfig runtimeConfig;

    private final JobTracing tracing = new JobTracing();

    // SDK-462: a job carries a single start data; the first caller claims it, later ones are ignored.
    // Lock-free because a job is the shared concurrency unit and start data may be set from any thread.
    private final AtomicBoolean startDataClaimed = new AtomicBoolean(false);

    // internal properties, shall not go to any message
    private final JobProperties properties = new JobProperties();

    private final JobMetadata metadata;

    private LocalDateTime startTime;

    private boolean startTimeExplicitlySet;

    private LocalDateTime endTime;

    private final JobErrorHandling errorHandling;
    private final JobSettings jobSettings;
    private final DataMasker dataMasker;
    // access to truncation state is synchronized on the activities lock!
    private final JobTruncation truncation;

    /**
     * Create a job with a givenModelId, a jobId and a logId
     *
     * @param processModel for job to create
     * @param jobId        of Job to create
     * @param logId        of Job to create
     */
    public JobImpl(ProcessModel processModel, String jobId, String logId) {
        this.jobId = jobId;
        this.logId = logId;
        metadata = new JobMetadata(this, logId);
        setStatusAndSeverity(JobStatus.CREATED);
        this.processModel = processModel;
        njams = processModel.getNjams();
        dataMasker = njams.configuration().dataMasking();
        // must be set before the recorded attribute is added below: attributes already apply payload limits
        jobSettings = JobSettings.of(njams.getSettings());
        errorHandling = new JobErrorHandling(this, jobSettings);
        truncation = new JobTruncation(this, jobSettings);
        runtimeConfig = new JobRuntimeConfig(processModel);
        flusher = new JobFlusher(processModel, activities, attributes, metadata, tracing, runtimeConfig,
                truncation, activitiesLock);
        if (runtimeConfig.addRecordedAttribute) {
            attributes.addInternal(RECORDED_ATTRIBUTE, "true");
        }
        //It is used as the default startTime, if no other startTime will be set.
        //If a startTime is set afterwards with setStartTime, startTimeExplicitlySet
        //will be set to true.
        startTime = DateTimeUtility.now();
        startTimeExplicitlySet = false;
    }

    /**
     * Returns the next Sequence for the next executed Activity.
     *
     * @return the next one
     */
    long getNextSequence() {
        return activities.getNextSequence();
    }

    /**
     * Called by the SDK's periodic flush task to flush this instance if it is due. SDK-internal.
     *
     * @param sentBefore Send if the last flush was before this timestamp
     * @param flushSize  Send if message size is greater than this size
     */
    void timerFlush(LocalDateTime sentBefore, long flushSize) {
        if (flusher.isFlushDue(this, sentBefore, flushSize)) {
            LOG.debug("Flush by timer: {}", this);
            flush();
        }
    }

    /**
     * Flushes a logMessage to the server if all the preconditions are fulfilled. Called by the SDK when the job ends
     * and by {@link #timerFlush(LocalDateTime, long)}. SDK-internal.
     */
    void flush() {
        flusher.flush(this);
    }

    /**
     * Checks truncating limit and indicates whether or not the given activity shall be added to the next log message.
     *
     * @param activity        The activity to test
     * @param finishedSuccess Whether this job has yet finished successfully.
     * @return <code>true</code> if the given activity shall be added, <code>false</code> if not.
     */
    boolean checkTruncating(final Activity activity, boolean finishedSuccess) {
        return truncation.checkTruncating(activity, finishedSuccess);
    }


    /**
     * Starts the job, i.e., sets status to RUNNING, job start date to now if
     * not set before, and flags the job to begin flushing.
     */
    @Override
    public void start() {
        //If this is true, the startTime hasn't been changed by setStartTime
        //before. This means that the startTime can be set to now().
        if (!startTimeExplicitlySet) {
            setStartTime(DateTimeUtility.now());
        }
        setStatusAndSeverity(JobStatus.RUNNING);
    }

    /**
     * @param normalCompletion Set to <code>true</code> if the engine reported the job to complete normally, or
     *                         <code>false</code> if the engine reported that the job execution has failed.
     */
    @Override
    public void end(boolean normalCompletion) {
        if (finished) {
            throw new NjamsSdkRuntimeException("Job already finished");
        }
        synchronized (activitiesLock) {
            // must be captured before the final status is set below, which makes hasStarted() true
            final boolean neverStarted = !hasStarted();
            if (!normalCompletion) {
                // unhandled error
                lastStatus = JobStatus.ERROR;
                errorHandling.commitActivityError();
            } else if (lastStatus == null || lastStatus.getValue() <= JobStatus.RUNNING.getValue()) {
                // if we never had a status update, we are setting SUCCESS
                lastStatus = JobStatus.SUCCESS;
            }
            //end all not ended activities
            activities.internalValues().stream()
                    .filter(a -> a.getActivityStatus() == null || a.getActivityStatus() == ActivityStatus.RUNNING)
                    .forEach(Activity::end);
            if (getEndTime() == null) {
                setEndTime(DateTimeUtility.now());
            }
            finished = true;
            processModel.getNjams().jobs().remove(getJobId());
            if (neverStarted) {
                LOG.error("Job {} has been finished before it was started"
                        + " - it will NOT be sent to the nJAMS server.", getLogId());
            } else {
                flush();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void discard() {
        if (finished) {
            // idempotent: already ended or already discarded
            return;
        }
        synchronized (activitiesLock) {
            if (finished) {
                return;
            }
            LOG.warn("Discarding job {} without sending it to the nJAMS server;"
                    + " any data recorded for this job is dropped.", getLogId());
            discarded = true;
            finished = true;
            processModel.getNjams().jobs().remove(getJobId());
        }
    }

    /**
     * Indicates whether this job has been discarded (see {@link #discard()}). Read by the flusher
     * to ensure a discarded job is never sent.
     *
     * @return <code>true</code> if and only if this job was discarded
     */
    boolean isDiscarded() {
        return discarded;
    }

    /**
     * Records that an error occurred for the given activity. Whether or not an according event is
     * generated depends on the {@value NjamsSettings#PROPERTY_LOG_ALL_ERRORS} setting, or the job's end status
     * reported by the executing engine.
     *
     * @param errorActivity The activity instance on that the given error occurred.
     * @param errorEvent    Information about the error that occurred. This information is used for
     *                      generating an according event if required.
     */
    void setActivityErrorEvent(Activity errorActivity, ErrorEvent errorEvent) {
        errorHandling.setActivityErrorEvent(errorActivity, errorEvent);
    }

    /**
     * Sets a status for this {@link Job}. It can't be set back to
     * JobStatus.CREATED. Also set the maxSeverityStatus if it is not set or
     * lower than the status. It can only be set after the job has been started.
     *
     * @param status the new job status if it is not null and not
     *               JobStatus.CREATED.
     * @deprecated See {@link Job#setStatus(JobStatus)}.
     */
    @Deprecated
    @Override
    public void setStatus(JobStatus status) {
        boolean changed = false;
        if (status != null && status != JobStatus.CREATED && hasStarted()) {
            setStatusAndSeverity(status);
            changed = true;
        } else if (!hasStarted()) {
            LOG.warn("The job must be started before the status can be changed");
        } else if (status == null || status == JobStatus.CREATED) {
            LOG.warn("The Status cannot be set to {}.", status);
        }
        if (LOG.isTraceEnabled()) {
            String loggingLogId = getLogId();
            JobStatus loggingStatus = getStatus();
            if (changed) {
                LOG.trace("Setting the status of job with logId {} to {}", loggingLogId, loggingStatus);
            } else {
                LOG.trace("The status of the job with logId {} hasn't been changed. The status is {}.", loggingLogId,
                        loggingStatus);
            }
        }
    }

    private void setStatusAndSeverity(JobStatus status) {
        // Atomic read-modify-write: parallel threads recording into the same job may escalate the
        // status concurrently; without the lock an escalation (e.g. ERROR) can be lost.
        synchronized (activitiesLock) {
            lastStatus = status;
            if (maxSeverity == null || maxSeverity.getValue() < status.getValue()) {
                maxSeverity = status;
            }
        }
    }

    /**
     * Returns the status for this {@link Job}.
     *
     * @return {@link JobStatus#CREATED} before this job has been started, then {@link JobStatus#RUNNING} until
     * the job has ended. Finally, when the job has ended, its final status is returned.
     */
    @Override
    public JobStatus getStatus() {
        return finished ? lastStatus : hasStarted() ? JobStatus.RUNNING : JobStatus.CREATED;

    }

    /**
     * Return the startTime
     *
     * @return the startTime
     */
    @Override
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /**
     * Sets the start timestamp of a job. if you don't set the job start
     * explicitly, it is set to the timestamp of the job creation. The startTime
     * cannot be set to null!
     *
     * @param jobStart start time of the job.
     */
    @Override
    public void setStartTime(final LocalDateTime jobStart) {
        if (jobStart == null) {
            LOG.warn("StartTime of the job cannot be null.");
        } else {
            startTime = jobStart;
            startTimeExplicitlySet = true;
        }
    }

    /**
     * Sets the end timestamp of a job.
     *
     * @param jobEnd job end
     */
    @Override
    public void setEndTime(final LocalDateTime jobEnd) {
        endTime = jobEnd;
    }

    /**
     * Return the endTime
     *
     * @return the endTime
     */
    @Override
    public LocalDateTime getEndTime() {
        return endTime;
    }

    /**
     * Gets the maximal severity of this job job.
     *
     * @return max severity
     */
    @Override
    public JobStatus getMaxSeverity() {
        return maxSeverity;
    }

    /**
     * Indicates whether the job is already finished or not.
     *
     * @return <b>true</b> if and only if the job is already finished (if {@link #end(boolean)}
     * was called), else
     * <b>false</b>
     */
    @Override
    public boolean isFinished() {
        return finished;
    }

    /**
     * Guard for the facet API: data changed after {@link #end(boolean)} is never sent to the nJAMS
     * server, because the final log message has already been flushed.
     */
    void requireNotFinished(String operation) {
        if (finished) {
            throw new NjamsSdkRuntimeException(
                    operation + " is not allowed once the job has ended: the final log message of the job has already"
                            + " been sent to the nJAMS server and a later change is never sent.");
        }
    }

    /**
     * @return the estimatedSize
     */
    long getEstimatedSize() {
        return flusher.getEstimatedSize();
    }

    /**
     * Add estimatedSize to the estimatedSize of the activity
     *
     * @param estimatedSize estimatedSize to add
     */
    void addToEstimatedSize(long estimatedSize) {
        flusher.addToEstimatedSize(estimatedSize);
    }

    @Override
    public boolean needsData(ActivityModel activityModel) {
        if (tracing.isDeepTrace() || activityModel.isStarter()) {
            return true;
        }
        ActivityConfiguration activityConfig = getActivityConfiguration(activityModel);
        if (activityConfig != null) {
            return activityConfig.getExtract() != null || isActiveTracepoint(activityConfig.getTracepoint());
        }
        return false;
    }

    /**
     * Returns <code>true</code> if the given tracepoint configuration is currently active.
     *
     * @param tracepoint The tracepoint to check
     * @return <code>true</code> if the given tracepoint configuration is currently active.
     */
    boolean isActiveTracepoint(TracepointExt tracepoint) {
        return runtimeConfig.isActiveTracepoint(tracepoint);
    }

    /**
     * Returns the runtime configuration for a specific {@link ActivityModel} if any.
     *
     * @param activityModel The model for that configuration shall be returned.
     * @return May be <code>null</code> if no configuration exists.
     */
    ActivityConfiguration getActivityConfiguration(ActivityModel activityModel) {
        return runtimeConfig.getActivityConfiguration(activityModel);
    }

    /**
     * Return if recording is activated for this job
     *
     * @return true if activated, false if not
     */
    boolean isRecording() {
        return runtimeConfig.recording;
    }

    /**
     * Claims this job's single start-data slot (SDK-462). The first caller gets <code>true</code>
     * and may set the start data; every later caller gets <code>false</code>. Lock-free: a job is
     * shared across threads, so the claim must be atomic.
     *
     * @return <code>true</code> only for the first caller
     */
    boolean claimStartData() {
        return startDataClaimed.compareAndSet(false, true);
    }

    /**
     * Returns whether this job's start data has already been set.
     *
     * @return <code>true</code> if start data was already set for this job
     */
    boolean isStartDataSet() {
        return startDataClaimed.get();
    }

    /**
     * Returns whether the configured payload limit should also be applied to start data (SDK-420).
     *
     * @return <code>true</code> if start data is subject to the payload limit
     */
    boolean isStartDataLimited() {
        return jobSettings.applyPayloadLimitToStartData;
    }

    /**
     * Returns whether the given (already-serialized) start data would be truncated or discarded by the
     * configured payload limit, either because the serializer already truncated it or because it still
     * exceeds the limit.
     *
     * @param payload             the serialized start data, may be <code>null</code>
     * @param serializerTruncated whether the serializer already truncated the value at the limit
     * @return <code>true</code> if applying the limit would truncate or discard the value
     */
    boolean exceedsStartDataLimit(String payload, boolean serializerTruncated) {
        if (payload == null || jobSettings.payloadLimit == null) {
            return false;
        }
        return serializerTruncated || payload.length() > jobSettings.payloadLimit.getValue();
    }

    /**
     * Marks this job as not replayable because its start data was truncated or discarded (SDK-420), by
     * clearing the recorded flag to <code>false</code> (last value wins, overriding the earlier
     * <code>true</code>). Only applied to jobs that were recordable to begin with.
     */
    void revokeRecorded() {
        if (runtimeConfig.addRecordedAttribute) {
            attributes.addInternal(RECORDED_ATTRIBUTE, "false");
        }
    }

    @Override
    public String getJobId() {
        return jobId;
    }

    @Override
    public String getLogId() {
        return logId;
    }

    @Override
    public void addPluginDataItem(
            com.faizsiegeln.njams.messageformat.v4.logmessage.interfaces.IPluginDataItem pluginDataItem) {
        flusher.addPluginDataItem((PluginDataItem) pluginDataItem);
    }

    /**
     * This method returns if the jobImpl has already been started.
     *
     * @return true, if the job has started already (RUNNING, SUCCESS, WARNING,
     * ERROR). return false, if the job hasn't been started (CREATED)
     */
    @Override
    public boolean hasStarted() {
        return lastStatus != JobStatus.CREATED;
    }

    /**
     * Provides access to the runtime activities of this job: the activity registry, lookups,
     * builders, and the start activity.
     *
     * @return the activities facet of this job, never <code>null</code>
     */
    @Override
    public JobActivities activities() {
        return activities;
    }

    /**
     * Provides access to the attributes of this job. Attributes are wire data: they are
     * transmitted to the nJAMS server with the next log message.
     *
     * @return the attributes facet of this job, never <code>null</code>
     */
    @Override
    public JobAttributes attributes() {
        return attributes;
    }

    /**
     * Provides access to the descriptive metadata of this job: correlation/parent/external
     * log ids and the business fields. The facet's setters are chainable.
     *
     * @return the metadata facet of this job, never <code>null</code>
     */
    @Override
    public JobMetadata metadata() {
        return metadata;
    }

    /**
     * Provides access to the internal properties of this job. Properties are client-local
     * only and never transmitted to the nJAMS server.
     *
     * @return the properties facet of this job, never <code>null</code>
     */
    @Override
    public JobProperties properties() {
        return properties;
    }

    /**
     * Provides access to the tracing flags of this job (deep trace, traces).
     *
     * @return the tracing facet of this job, never <code>null</code>
     */
    @Override
    public JobTracing tracing() {
        return tracing;
    }

    /**
     * Returns the {@link Njams} client instance owning this job.
     *
     * @return the owning client instance
     */
    Njams getNjams() {
        return njams;
    }

    /**
     * Masks the given value with the data masking of the client instance owning this job.
     *
     * @param value the value to mask
     * @return the masked value
     */
    String mask(String value) {
        return dataMasker.maskString(value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("JobImpl[process=").append(processModel.getName()).append("; logId=").append(getLogId())
                .append("; jobId=").append(getJobId()).append(']');
        return sb.toString();
    }

    /**
     * Returns the given input string and ensures that it is not longer than the given maximum length.
     *
     * @param fieldName Only used for logging
     * @param value     The input value that is returned but possibly truncated
     * @param maxLength Maximum length for the returned string
     * @return The given input but no longer than the given maximum length
     */
    static String limitLength(String fieldName, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            LOG.warn("Value of field '{}' exceeds max length of {} characters. Value will be truncated.", fieldName,
                    maxLength);
            return value.substring(0, maxLength - 1);
        }
        return value;
    }

    /**
     * Returns the size limit to pass to
     * {@link com.im.njams.sdk.serializer.Serializer#serialize(Object, int)} for payloads whose
     * truncation is then resolved via {@link #applyLimit(String, boolean)} using the serializer's
     * truncation flag.
     *
     * @return the configured payload limit, or {@code 0} when no payload limit is configured
     */
    int getSerializeSizeHint() {
        if (jobSettings.payloadLimit == null) {
            return 0;
        }
        final int limit = jobSettings.payloadLimit.getValue();
        return limit <= 0 ? 0 : limit;
    }

    /**
     * If limiting payload size is enabled, this method ensures that the given payload is handled
     * accordingly. Truncation is decided purely from the payload length, for fields that are not
     * produced by a size-limited serializer (event payload, stack trace, attributes, ...).
     * @param payload The payload to limit.
     * @return The given payload adjusted to the configured limits.
     */
    String limitPayload(String payload) {
        return applyLimit(payload, false);
    }

    /**
     * Applies the configured payload limit to an already-serialized payload, honouring an explicit
     * serializer truncation flag. The payload is considered truncated if the serializer already
     * truncated it, or if it still exceeds the configured limit; in that case it is truncated (with
     * the truncated-suffix) or discarded according to the configured mode.
     *
     * @param payload            The serialized payload to limit.
     * @param serializerTruncated Whether the serializer already had to truncate the value at the limit.
     * @return The payload adjusted to the configured limits.
     */
    String applyLimit(String payload, boolean serializerTruncated) {
        if (payload == null || jobSettings.payloadLimit == null) {
            return payload;
        }
        final int limit = jobSettings.payloadLimit.getValue();
        final boolean truncated = serializerTruncated || payload.length() > limit;
        if (!truncated) {
            return payload;
        }
        if (limit > 0 && jobSettings.payloadLimit.getKey()) {
            // truncate
            return payload.substring(0, Math.min(payload.length(), limit)) + PAYLOAD_TRUNCATED_SUFFIX;
        }
        // discard
        return PAYLOAD_DISCARDED_MESSAGE;
    }

}
