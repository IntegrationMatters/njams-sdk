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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.PluginDataItem;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.configuration.TracepointExt;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.SubProcessActivityModel;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.StringUtils;

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
    // activitiesLock in end() and discard(). A reader observing finished==true also observes the
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
        // must be set before initFromConfiguration: addAttribute already applies payload limits
        jobSettings = JobSettings.of(njams.getSettings());
        errorHandling = new JobErrorHandling(this, jobSettings);
        truncation = new JobTruncation(this, jobSettings);
        runtimeConfig = new JobRuntimeConfig(processModel);
        flusher = new JobFlusher(processModel, activities, attributes, metadata, tracing, runtimeConfig,
                truncation, activitiesLock);
        if (runtimeConfig.addRecordedAttribute) {
            addAttribute(RECORDED_ATTRIBUTE, "true");
        }
        //It is used as the default startTime, if no other startTime will be set.
        //If a startTime is set afterwards with setStartTime, startTimeExplicitlySet
        //will be set to true.
        startTime = DateTimeUtility.now();
        startTimeExplicitlySet = false;
    }

    /**
     * Creates ActivityBuilder with a given ActivityModel.
     *
     * @param activityModel to create
     * @return a builder
     */
    @Override
    public ActivityBuilder createActivity(ActivityModel activityModel) {
        return activities.create(activityModel, this);
    }

    /**
     * Creates GroupBuilder with a given GroupModel.
     *
     * @param groupModel to create
     * @return a builder
     */
    @Override
    public GroupBuilder createGroup(GroupModel groupModel) {
        return activities.createGroup(groupModel, this);
    }

    /**
     * Creates SubProcessBuilder with a given SubProcessModel.
     *
     * @param groupModel to create
     * @return a builder
     */
    @Override
    public SubProcessActivityBuilder createSubProcess(SubProcessActivityModel groupModel) {
        return activities.createSubProcess(groupModel, this);
    }

    /**
     * Adds a new Activity to the Job. If the job is not started or is a start
     * activity, but not the only one in this job, a NjamsSdkRuntimeException
     * will be thrown.
     *
     * @param activity to add to this job.
     */
    @Override
    public void addActivity(final Activity activity) {
        activities.add(activity, this, true);
    }

    /**
     * Returns an activity for a given instanceId.
     *
     * @param activityInstanceId to get
     * @return the {@link Activity}
     */
    @Override
    public Activity getActivityByInstanceId(String activityInstanceId) {
        return activities.getByInstanceId(activityInstanceId);
    }

    /**
     * Returns the last added activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     */
    @Override
    public Activity getActivityByModelId(String activityModelId) {
        return activities.getByModelId(activityModelId);
    }

    /**
     * Returns the last added and running activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     */
    @Override
    public Activity getRunningActivityByModelId(String activityModelId) {
        return activities.getRunningByModelId(activityModelId);
    }

    /**
     * Returns the last added and completed activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     */
    @Override
    public Activity getCompletedActivityByModelId(String activityModelId) {
        return activities.getCompletedByModelId(activityModelId);
    }

    /**
     * Return the start activity, might return null if the startActivity hasn't been set or if it has already been flushed.
     *
     * @return the start activity or null
     */
    @Override
    public Activity getStartActivity() {
        return activities.getStart();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Activity> getActivities() {
        return activities.getAll();
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
     * This method is called by a periodic timer to flush this instance if it's due.
     *
     * @param sentBefore Send if the last flush was before this timestamp
     * @param flushSize  Send if message size is greater than this size
     * @deprecated SDK-internal flush mechanics, not part of the public API; there is no
     *             replacement — periodic flushing is handled transparently by the SDK.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void timerFlush(LocalDateTime sentBefore, long flushSize) {
        flusher.timerFlush(this, sentBefore, flushSize);
    }

    /**
     * This method is called by {@link #timerFlush(LocalDateTime, long)}
     * and when {@link #end(boolean)} is called. It flushes a logMessage to the
     * server if all the preconditions are fulfilled.
     *
     * @deprecated SDK-internal flush mechanics, not part of the public API; there is no
     *             replacement — log messages are flushed transparently by the SDK.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void flush() {
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
            processModel.getNjams().removeJob(getJobId());
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
            processModel.getNjams().removeJob(getJobId());
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
     * @deprecated SDK-internal error handling, not part of the public API; there is no
     *             replacement — error events are recorded by the SDK's activity processing.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setActivityErrorEvent(Activity errorActivity, ErrorEvent errorEvent) {
        errorHandling.setActivityErrorEvent(errorActivity, errorEvent);
    }

    /**
     * Set the logMessage status to success if the status is running. Then flush
     * it. If the job has been finished before or if the job hasn't been started
     * before it is finished, an NjamsSdkRuntimeException is thrown.
     *
     * @deprecated Replaced by {@link #end(boolean)}
     */
    @Override
    @Deprecated
    public void end() {
        end(true);
    }

    /**
     * Sets a status for this {@link Job}. It can't be set back to
     * JobStatus.CREATED. Also set the maxSeverityStatus if it is not set or
     * lower than the status. It can only be set after the job has been started.
     *
     * @param status the new job status if it is not null and not
     *               JobStatus.CREATED.
     */
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
     * Sets the correlation log id of this job.
     *
     * @param correlationLogId correlation log id
     */
    @Override
    public void setCorrelationLogId(final String correlationLogId) {
        warnIfFinished("setCorrelationLogId", "metadata().setCorrelationLogId(...)");
        metadata.setCorrelationLogIdInternal(correlationLogId);
    }

    /**
     * Returns the correlation log id of this job.
     *
     * @return collreation log id
     */
    @Override
    public String getCorrelationLogId() {
        return metadata.getCorrelationLogId();
    }

    /**
     * Set the parentLogId
     *
     * @param parentLogId parentLogId to set
     */
    @Override
    public void setParentLogId(String parentLogId) {
        warnIfFinished("setParentLogId", "metadata().setParentLogId(...)");
        metadata.setParentLogIdInternal(parentLogId);
    }

    /**
     * Return the parentLogId
     *
     * @return the parentLogId
     */
    @Override
    public String getParentLogId() {
        return metadata.getParentLogId();
    }

    /**
     * Set the externalLogId
     *
     * @param externalLogId texternalLogId to set
     */
    @Override
    public void setExternalLogId(String externalLogId) {
        warnIfFinished("setExternalLogId", "metadata().setExternalLogId(...)");
        metadata.setExternalLogIdInternal(externalLogId);
    }

    /**
     * Return the externalLogId
     *
     * @return the externalLogId
     */
    @Override
    public String getExternalLogId() {
        return metadata.getExternalLogId();
    }

    /**
     * Set the businessService as String
     *
     * @param businessService businessService to set
     */
    @Override
    public void setBusinessService(String businessService) {
        setBusinessService(Path.resolve(businessService));
    }

    /**
     * Set the businessService as Path
     *
     * @param businessService businessService to set
     */
    @Override
    public void setBusinessService(Path businessService) {
        warnIfFinished("setBusinessService", "metadata().setBusinessService(...)");
        metadata.setBusinessServiceInternal(businessService);
    }

    /**
     * Return the businessService
     *
     * @return the businessService
     */
    @Override
    public String getBusinessService() {
        return metadata.getBusinessService();
    }

    /**
     * Set the businessObject as String
     *
     * @param businessObject businessObject to set
     */
    @Override
    public void setBusinessObject(String businessObject) {
        setBusinessObject(Path.resolve(businessObject));
    }

    /**
     * Set the binsessObject as Path
     *
     * @param businessObject businessObject to set
     */
    @Override
    public void setBusinessObject(Path businessObject) {
        warnIfFinished("setBusinessObject", "metadata().setBusinessObject(...)");
        metadata.setBusinessObjectInternal(businessObject);
    }

    /**
     * Return the businessObject
     *
     * @return the businessObject
     */
    @Override
    public String getBusinessObject() {
        return metadata.getBusinessObject();
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
     * @return <b>true</b> if and only if the job is already finished (if end()
     * was called), else
     * <b>false</b>
     */
    @Override
    public boolean isFinished() {
        return finished;
    }

    /**
     * Guard for the new facet API: data changed after end() is never sent to the nJAMS server,
     * because the final log message has already been flushed.
     */
    void requireNotFinished(String operation) {
        if (finished) {
            throw new NjamsSdkRuntimeException(
                    operation + " is not allowed after end(): the final log message of the job has already"
                            + " been sent to the nJAMS server and a later change is never sent.");
        }
    }

    /**
     * Lenient-legacy guard: where the new facet API rejects a call after end(), the deprecated
     * facade method only logs a warning and proceeds, so that existing client code keeps working
     * throughout the deprecation period.
     */
    private void warnIfFinished(String oldMethod, String replacement) {
        if (finished) {
            LOG.warn("{} was called after end(); the change will not be sent to the nJAMS server."
                    + " The replacement API {} rejects this call.", oldMethod, replacement);
        }
    }

    /**
     * Return the Attribute name to a given value
     *
     * @param name attribute name
     * @return attribute value
     */
    @Override
    public String getAttribute(final String name) {
        return attributes.get(name);
    }

    /**
     * Returns a detached copy of all attributes for this job. I.e., any modification on the returned map has no effect
     * on this job instance!
     *
     * @return list of attributes
     */
    @Override
    public Map<String, String> getAttributes() {
        return attributes.getAll();
    }

    /**
     * Return if the job contains a attribute for a given name
     *
     * @param name attribute name to check
     * @return true if found, false if not found
     */
    @Override
    public boolean hasAttribute(final String name) {
        return attributes.has(name);
    }

    /**
     * Marks that a job shall collect trace information for each activity
     * (including sub processes).
     *
     * @param deepTrace <b>true</b> if deep trace shall be activated.
     */
    @Override
    public void setDeepTrace(boolean deepTrace) {
        tracing.setDeepTrace(deepTrace);
    }

    /**
     * Indicates that trace information shall be collected for all activites of
     * this job (including sub processes).
     *
     * @return <b>true</b> if and only if deep trace is enabled.
     */
    @Override
    public boolean isDeepTrace() {
        return tracing.isDeepTrace();
    }

    /**
     * Return the last push LocalDateTime
     *
     * @return the last push LocalDateTime
     * @deprecated SDK-internal flush bookkeeping, not part of the public API; there is no
     *             replacement.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public LocalDateTime getLastFlush() {
        return flusher.getLastFlush();
    }

    /**
     * Marks this job instance as instrumented.
     *
     * @deprecated SDK-internal tracing mechanics, not part of the public API; there is no
     *             replacement — instrumentation is flagged by the SDK's tracing handling.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setInstrumented() {
        tracing.setInstrumented();
    }

    /**
     * @return the traces
     */
    @Override
    public boolean isTraces() {
        return tracing.isTraces();
    }

    /**
     * @param traces the traces to set
     * @deprecated SDK-internal tracing mechanics, not part of the public API; there is no
     *             replacement — the traces flag is maintained by the SDK's tracepoint handling.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setTraces(boolean traces) {
        tracing.setTraces(traces);
    }

    /**
     * Gets a properties value. Properties will not be send within project
     * messages.
     *
     * @param key name of the property
     * @return Properties value of <b>null</b>
     */
    @Override
    public Object getProperty(final String key) {
        return properties.get(key);
    }

    /**
     * Checks whether the activity has a property with a given name.
     *
     * @param key name of the property
     * @return <b>true</b> if and only if a property with the given name exists.
     */
    @Override
    public boolean hasProperty(final String key) {
        return properties.has(key);
    }

    /**
     * Sets a properties value. Properties will not be send within project
     * messages.
     *
     * @param key   name of the property
     * @param value value of the property
     */
    @Override
    public void setProperty(final String key, final Object value) {
        properties.set(key, value);
    }

    /**
     * Removes the property with a given name
     *
     * @param key name of the property
     * @return Previous value of the property (if it existed) or else
     * <b>null</b>.
     */
    @Override
    public Object removeProperty(final String key) {
        return properties.remove(key);
    }

    /**
     * @return the estimatedSize
     * @deprecated SDK-internal flush bookkeeping, not part of the public API; there is no
     *             replacement.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public long getEstimatedSize() {
        return flusher.getEstimatedSize();
    }

    /**
     * Add estimatedSize to the estimatedSize of the activity
     *
     * @param estimatedSize estimatedSize to add
     * @deprecated SDK-internal flush bookkeeping, not part of the public API; there is no
     *             replacement.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void addToEstimatedSize(long estimatedSize) {
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
     * @deprecated SDK-internal tracing mechanics, not part of the public API; there is no
     *             replacement — tracepoints are evaluated by the SDK's activity processing.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public boolean isActiveTracepoint(TracepointExt tracepoint) {
        return runtimeConfig.isActiveTracepoint(tracepoint);
    }

    /**
     * Returns the runtime configuration for a specific {@link ActivityModel} if any.
     *
     * @param activityModel The model for that configuration shall be returned.
     * @return May be <code>null</code> if no configuration exists.
     * @deprecated SDK-internal configuration lookup, not part of the public API; there is no
     *             replacement — activity configurations are resolved by the SDK.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public ActivityConfiguration getActivityConfiguration(ActivityModel activityModel) {
        return runtimeConfig.getActivityConfiguration(activityModel);
    }

    /**
     * Return if recording is activated for this job
     *
     * @return true if activated, false if not
     * @deprecated SDK-internal configuration state, not part of the public API; there is no
     *             replacement.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public boolean isRecording() {
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
            addAttribute(RECORDED_ATTRIBUTE, "false");
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
     * This method sets the businessStart in the ActivityImpl
     *
     * @param businessStart the businessStart to set
     */
    @Override
    public void setBusinessStart(LocalDateTime businessStart) {
        warnIfFinished("setBusinessStart", "metadata().setBusinessStart(...)");
        metadata.setBusinessStartInternal(businessStart);
    }

    @Override
    public LocalDateTime getBusinessStart() {
        return metadata.getBusinessStart();
    }

    /**
     * This method sets the businessEnd in the ActivityImpl
     *
     * @param businessEnd the businessEnd to set
     */
    @Override
    public void setBusinessEnd(LocalDateTime businessEnd) {
        warnIfFinished("setBusinessEnd", "metadata().setBusinessEnd(...)");
        metadata.setBusinessEndInternal(businessEnd);
    }

    @Override
    public LocalDateTime getBusinessEnd() {
        return metadata.getBusinessEnd();
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
     * This method is used to put attributes in the attributes map.
     *
     * @param key   the key to set
     * @param value the value to set
     */
    @Override
    public void addAttribute(final String key, String value) {
        warnIfFinished("addAttribute", "attributes().add(...)");
        attributes.addInternal(key, value);
    }

    /**
     * Returns the {@link Njams} client instance owning this job.
     *
     * @return the owning client instance
     * @deprecated SDK-internal back-reference, not part of the public API; there is no
     *             replacement — client code should keep its own reference to its {@link Njams}
     *             instance.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public Njams getNjams() {
        return njams;
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
     * @deprecated SDK-internal helper, not part of the public API; there is no replacement —
     *             field values are limited transparently by the SDK.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public static String limitLength(String fieldName, String value, int maxLength) {
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
