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
package com.im.njams.sdk.logmessage;

import static java.util.Collections.unmodifiableCollection;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import java.util.concurrent.atomic.AtomicInteger;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.PluginDataItem;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.configuration.TracepointExt;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.SubProcessActivityModel;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final ProcessModel processModel;
    private final Njams njams;

    private final String jobId;

    private final String logId;
    /*
     * the latest status of the job, set by any event
     */
    private JobStatus lastStatus = JobStatus.CREATED;

    /*
     * maximum severity recorded
     */
    private JobStatus maxSeverity = JobStatus.SUCCESS;

    // instanceId -> activity
    private final Map<String, Activity> activities = Collections.synchronizedMap(new LinkedHashMap<>());

    /*
     * activity sequence counter
     */
    private final AtomicInteger sequenceCounter;

    /*
     * job level attributes
     */
    private final Map<String, String> attributes = new ConcurrentHashMap<>();
    private final Map<String, String> flushedAttributes = new ConcurrentHashMap<>();

    /*
     * Plugin data items
     */
    private final List<PluginDataItem> pluginDataItems;

    /*
     * counts how many flushes have been made. Used in LogMessage as messageNo
     */
    private final AtomicInteger flushCounter;

    private Activity startActivity;

    boolean hasOrHadStartActivity;

    private boolean deepTrace;

    private boolean finished = false;

    private LogMode logMode = LogMode.COMPLETE;
    private LogLevel logLevel = LogLevel.INFO;
    private boolean exclude = false;

    private boolean instrumented = false;
    private boolean traces;

    // internal properties, shall no go to project message
    private final Map<String, Object> properties = new LinkedHashMap<>();

    //1000 for headers and co
    private long estimatedSize = 1000L;

    private boolean recording = true;

    private String correlationLogId;

    private String parentLogId;

    private String externalLogId;

    private String businessService;

    private String businessObject;

    private LocalDateTime startTime;

    private boolean startTimeExplicitlySet;

    private LocalDateTime endTime;

    private LocalDateTime lastFlush;

    private LocalDateTime businessEnd;

    private LocalDateTime businessStart;

    private final Object errorLock = new Object();
    private ActivityImpl errorActivity = null;
    private ErrorEvent errorEvent = null;
    private final boolean allErrors;
    private final int truncateLimit;
    private final boolean truncateOnSuccess;
    private boolean isTruncatingActivities = false;
    private boolean isTruncatingEvents = false;
    // access to truncate fields is synchronized on activities!
    // activity-instance-ID --> hasEvent(activity)
    private final Map<String, Boolean> activityIds = new HashMap<>();
    // IDs of activities that have been flushed but are not complete yet; for checking timer-flush
    private Set<String> flushedActivities = ConcurrentHashMap.newKeySet();

    private Entry<Boolean, Integer> payloadLimit = null;

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
        correlationLogId = logId;
        setStatusAndSeverity(JobStatus.CREATED);
        this.processModel = processModel;
        njams = processModel.getNjams();
        sequenceCounter = new AtomicInteger();
        flushCounter = new AtomicInteger();
        lastFlush = DateTimeUtility.now();
        pluginDataItems = new ArrayList<>();
        initFromConfiguration(processModel);
        //It is used as the default startTime, if no other startTime will be set.
        //If a startTime is set afterwards with setStartTime, startTimeExplicitlySet
        //will be set to true.
        startTime = DateTimeUtility.now();
        startTimeExplicitlySet = false;
        allErrors = "true".equalsIgnoreCase(njams.getSettings().getProperty(NjamsSettings.PROPERTY_LOG_ALL_ERRORS));
        truncateOnSuccess =
            "true".equalsIgnoreCase(njams.getSettings().getProperty(NjamsSettings.PROPERTY_TRUNCATE_ON_SUCCESS));
        truncateLimit = getTruncateLimit();
        initPayloadLimit();
    }

    private void initPayloadLimit() {
        final Settings settings = njams.getSettings();
        // truncate, discard
        final String mode = settings.getProperty(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE);
        if (StringUtils.isBlank(mode)) {
            return;
        }
        try {
            final int limit = Integer.parseInt(settings.getProperty(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE));
            if (limit < 0) {
                return;
            }
            if (limit == 0 || "discard".equalsIgnoreCase(mode)) {
                payloadLimit = new AbstractMap.SimpleImmutableEntry<>(false, limit);
            } else if ("truncate".equalsIgnoreCase(mode)) {
                payloadLimit = new AbstractMap.SimpleImmutableEntry<>(true, limit);
            }
        } catch (NumberFormatException e) {
            LOG.error("Failed to parse payload limit size: {}", e.toString());
        }

    }

    private int getTruncateLimit() {
        String s = null;
        try {
            s = njams.getSettings().getProperty(NjamsSettings.PROPERTY_TRUNCATE_LIMIT);
            if (StringUtils.isBlank(s)) {
                return Integer.MAX_VALUE;
            }
            final int i = Integer.parseInt(s);
            return i > 0 ? i : Integer.MAX_VALUE;
        } catch (Exception e) {
            LOG.warn("Failed  to parse setting: {}={} - Truncating will be disabled.",
                NjamsSettings.PROPERTY_TRUNCATE_LIMIT, s);
            return Integer.MAX_VALUE;
        }
    }

    /**
     * This method initializes the processConfiguration and the
     * activityConfigurations.
     */
    private void initFromConfiguration(ProcessModel processModel) {
        Configuration configuration = processModel.getNjams().getConfiguration();
        if (configuration == null) {
            LOG.error("Unable to set LogMode, LogLevel and Exclude for {}, configuration is null",
                processModel.getPath());
            return;
        }
        logMode = configuration.getLogMode();
        LOG.debug("Set LogMode for {} to {}", processModel.getPath(), logMode);

        boolean disableStartData = "true"
            .equalsIgnoreCase(getNjams().getSettings().getPropertyWithDeprecationWarning(
                NjamsSettings.PROPERTY_DISABLE_STARTDATA, NjamsSettings.OLD_DISABLE_STARTDATA));

        recording = !disableStartData && configuration.isRecording();
        LOG.debug(
            "Set recording for {} to {} based on client settings {} and disable-start-data setting {}",
            processModel.getPath(), recording, configuration.isRecording(), disableStartData);

        ProcessConfiguration process = configuration.getProcess(processModel.getPath().toString());
        if (process != null) {
            logLevel = process.getLogLevel();
            LOG.debug("Set LogLevel for {} to {}", processModel.getPath(), logLevel);
            exclude = process.isExclude();
            LOG.debug("Set Exclude for {} to {}", processModel.getPath(), exclude);
            recording = !disableStartData && process.isRecording();
            LOG.debug(
                "Set recording for {} to {} based on process settings {} and client setting {} and disable-start-data setting {}",
                processModel.getPath(), recording, process.isRecording(),
                configuration.isRecording(), disableStartData);
        }
        if (recording) {
            addAttribute("$njams_recorded", "true");
        }
    }

    /**
     * Creates ActivityBuilder with a given ActivityModel.
     *
     * @param activityModel to create
     * @return a builder
     */
    @Override
    public ActivityBuilder createActivity(ActivityModel activityModel) {
        if (activityModel instanceof GroupModel) {
            return createGroup((GroupModel) activityModel);
        }
        if (activityModel instanceof SubProcessActivityModel) {
            return createSubProcess((SubProcessActivityModel) activityModel);
        }
        return new ActivityBuilder(this, activityModel);
    }

    /**
     * Creates GroupBuilder with a given GroupModel.
     *
     * @param groupModel to create
     * @return a builder
     */
    @Override
    public GroupBuilder createGroup(GroupModel groupModel) {
        return new GroupBuilder(this, groupModel);
    }

    /**
     * Creates SubProcessBuilder with a given SubProcessModel.
     *
     * @param groupModel to create
     * @return a builder
     */
    @Override
    public SubProcessActivityBuilder createSubProcess(SubProcessActivityModel groupModel) {
        return new SubProcessActivityBuilder(this, groupModel);
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
        synchronized (activities) {
            if (!hasStarted()) {
                throw new NjamsSdkRuntimeException(
                    "The method start() must be called before activities can be added to the job!");
            }
            activities.put(activity.getInstanceId(), activity);
            if (activity.isStarter()) {
                if (hasOrHadStartActivity) {
                    throw new NjamsSdkRuntimeException("A job must not have more than one start activity "
                        + getJobId());
                }
                startActivity = activity;
                hasOrHadStartActivity = true;
            }
        }
    }

    private boolean hasEvent(final Activity activity) {
        return activity.getEventStatus() != null || StringUtils.isNotBlank(activity.getEventCode())
            || StringUtils.isNotBlank(activity.getEventMessage())
            || StringUtils.isNotBlank(activity.getEventPayload())
            || StringUtils.isNotBlank(activity.getStackTrace());
    }

    /**
     * Returns an activity for a given instanceId.
     *
     * @param activityInstanceId to get
     * @return the {@link Activity}
     */
    @Override
    public Activity getActivityByInstanceId(String activityInstanceId) {
        return activities.get(activityInstanceId);
    }

    /**
     * Returns the last added activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     */
    @Override
    public Activity getActivityByModelId(String activityModelId) {
        synchronized (activities) {
            List<String> reverseOrderedKeys = new ArrayList<>(activities.keySet());
            ListIterator<String> iterator = reverseOrderedKeys.listIterator(reverseOrderedKeys.size());
            while (iterator.hasPrevious()) {
                Activity _activity = activities.get(iterator.previous());
                if (_activity != null && _activity.getModelId().equals(activityModelId)) {
                    return _activity;
                }
            }
            return null;
        }
    }

    /**
     * Returns the last added and running activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     */
    @Override
    public Activity getRunningActivityByModelId(String activityModelId) {
        synchronized (activities) {
            List<String> reverseOrderedKeys = new ArrayList<>(activities.keySet());
            ListIterator<String> iterator = reverseOrderedKeys.listIterator(reverseOrderedKeys.size());
            while (iterator.hasPrevious()) {
                Activity _activity = activities.get(iterator.previous());
                if (_activity.getActivityStatus() == ActivityStatus.RUNNING
                    && _activity.getModelId().equals(activityModelId)) {
                    return _activity;
                }
            }
            return null;
        }
    }

    /**
     * Returns the last added and completed activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     */
    @Override
    public Activity getCompletedActivityByModelId(String activityModelId) {
        synchronized (activities) {
            List<String> reverseOrderedKeys = new ArrayList<>(activities.keySet());
            ListIterator<String> iterator = reverseOrderedKeys.listIterator(reverseOrderedKeys.size());
            while (iterator.hasPrevious()) {
                Activity _activity = activities.get(iterator.previous());
                if (_activity.getActivityStatus().ordinal() > ActivityStatus.RUNNING.ordinal()
                    && _activity.getModelId().equals(activityModelId)) {
                    return _activity;
                }
            }
            return null;
        }
    }

    /**
     * Return the start activity, might return null if the startActivity hasn't been set or if it has already been flushed.
     *
     * @return the start activity or null
     */
    @Override
    public Activity getStartActivity() {
        return startActivity;
    }

    /**
     * Return all Activities
     *
     * @return all Activities
     */
    @Override
    public Collection<Activity> getActivities() {
        return unmodifiableCollection(activities.values());
    }

    /**
     * Returns the next Sequence for the next executed Activity.
     *
     * @return the next one
     */
    long getNextSequence() {
        return sequenceCounter.incrementAndGet();
    }

    /**
     * This method is called by a periodic timer to flush this instance if it's due.
     *
     * @param sentBefore Send if the last flush was before this timestamp
     * @param flushSize  Send if message size is greater than this size
     */
    public void timerFlush(LocalDateTime sentBefore, long flushSize) {
        if (!hasStarted()) {
            LOG.trace("Skip timer flush. Job {} is not started.", this);
            return;
        }
        // only send updates automatically, if a change has been
        // made to the job between individual send events.
        LOG.trace("Job {}: lastPush: {}, age: {}, size: {}", this, getLastFlush(),
            Duration.between(getLastFlush(), DateTimeUtility.now()), getEstimatedSize());
        if ((getLastFlush().isBefore(sentBefore) || getEstimatedSize() > flushSize)
            && (!attributes.isEmpty() || getEndTime() != null || hasUnsentActivity())) {
            LOG.debug("Flush by timer: {}", this);
            flush();
        }
    }

    private boolean hasUnsentActivity() {
        return activities.keySet().stream().anyMatch(i -> !flushedActivities.contains(i));
    }

    /**
     * This method is called by {@link #timerFlush(LocalDateTime, long)}
     * and when {@link #end(boolean)} is called. It flushes a logMessage to the
     * server if all the preconditions are fulfilled.
     */
    public void flush() {

        synchronized (activities) {
            boolean suppressed = mustBeSuppressed();
            boolean started = hasStarted();
            if (!suppressed) {
                if (!started) {
                    LOG.warn("The job with logId: {} will be flushed, but hasn't started yet.", logId);
                }
                flushCounter.incrementAndGet();
                lastFlush = DateTimeUtility.now();
                LogMessage logMessage = createLogMessage();
                addToLogMessageAndCleanup(logMessage);
                logMessage.setSentAt(lastFlush);
                processModel.getNjams().getSender().send(logMessage, njams.getClientSessionId());
                // clean up jobImpl
                pluginDataItems.clear();
                calculateEstimatedSize();
            }
        }
    }

    private boolean mustBeSuppressed() {
        synchronized (activities) {
            // Do not send if one of the conditions is true.
            if (isLogModeNone() || isLogModeExclusiveAndNotInstrumented() || isExcludedProcess()
                || isLogLevelHigherAsJobStateAndHasNoTraces()) {
                LOG.debug("Job not flushed: Engine Mode: {} // Job's log level: {}, "
                    + "configured level: {} // is excluded: {} // has traces: {}", logMode, getStatus(), logLevel,
                    exclude, traces);
                //delete not running activities
                removeNotRunningActivities();
                calculateEstimatedSize();
                LOG.debug("mustBeSuppressed: true");
                return true;
            }
            LOG.debug("mustBeSuppressed: false");
            return false;
        }
    }

    private boolean isLogModeNone() {
        if (logMode == LogMode.NONE) {
            LOG.debug("isLogModeNone: true");
            return true;
        }
        return false;
    }

    private boolean isLogModeExclusiveAndNotInstrumented() {
        if (logMode == LogMode.EXCLUSIVE && !instrumented) {
            LOG.debug("isLogModeExclusiveAndNotInstrumented: true");
            return true;
        }
        return false;
    }

    private boolean isExcludedProcess() {
        if (exclude) {
            LOG.debug("isExcludedProcess: true");
            return true;
        }
        return false;
    }

    private boolean isLogLevelHigherAsJobStateAndHasNoTraces() {
        boolean b = hasStarted() && maxSeverity.getValue() < logLevel.value() && !traces;

        if (LOG.isDebugEnabled()) {
            LOG.debug("hasStarted[{}] && maxSeverity[{}] < logLevel[{}] && !traces[{}] == {}", hasStarted(),
                maxSeverity.getValue(), logLevel.value(), traces, b);
        }
        return b;
    }

    /**
     * This method creates the LogMessage that will be send to the server and
     * fills it with the attributes of the job.
     *
     * @return the created and with the job's information filled logMessage
     */
    private LogMessage createLogMessage() {
        LOG.trace("Creating LogMessage for job with logId: {}", logId);
        LogMessage logMessage = new LogMessage();
        logMessage.setBusinessEnd(businessEnd);
        logMessage.setBusinessStart(businessStart);
        logMessage.setCategory(processModel.getNjams().getCategory());
        logMessage.setCorrelationLogId(correlationLogId);
        logMessage.setExternalLogId(externalLogId);
        logMessage.setJobEnd(endTime);
        logMessage.setJobId(jobId);
        logMessage.setJobStart(startTime);
        logMessage.setLogId(logId);
        logMessage.setMachineName(processModel.getNjams().getMachine());
        logMessage.setMaxSeverity(maxSeverity.getValue());
        logMessage.setMessageNo(flushCounter.get());
        logMessage.setObjectName(businessObject);
        logMessage.setParentLogId(parentLogId);
        logMessage.setPath(processModel.getPath().toString());
        logMessage.setProcessName(processModel.getName());
        logMessage.setStatus(getStatus().getValue());
        logMessage.setServiceName(businessService);
        logMessage.setClientVersion(njams.getClientVersion());
        logMessage.setSdkVersion(njams.getSdkVersion());
        logMessage.setRuntimeVersion(njams.getRuntimeVersion());

        pluginDataItems.forEach(i -> logMessage.addPluginDataItem(i));
        return logMessage;
    }

    private void addToLogMessageAndCleanup(LogMessage logMessage) {
        synchronized (attributes) {
            for (Entry<String, String> e : attributes.entrySet()) {
                logMessage.addAtribute(e.getKey(), e.getValue());
                flushedAttributes.put(e.getKey(), e.getValue());
                attributes.remove(e.getKey());
            }
        }
        synchronized (activities) {
            // If this is the final message being sent, and truncate-on-success is selected and this job was
            // successful, truncate all activities w/o events.
            boolean finishedWithSuccess = logMessage.getJobEnd() != null && getStatus() == JobStatus.SUCCESS;

            //add all to logMessage
            for (Activity activity : activities.values()) {
                if (shouldFlush(activity)) {
                    if (checkTruncating(activity, finishedWithSuccess)) {
                        logMessage.addActivity(activity);
                    } else {
                        logMessage.setTruncated(true);
                    }
                }
            }
            //remove finished
            removeNotRunningActivities();
        }
    }

    private boolean shouldFlush(Activity activity) {
        if (!flushedActivities.contains(activity.getInstanceId())) {
            return true;
        }
        return activity.getActivityStatus() != ActivityStatus.RUNNING;
    }

    /**
     * Checks truncating limit and indicates whether or not the given activity shall be added to the next log message.
     *
     * @param activity        The activity to test
     * @param finishedSuccess Whether this job has yet finished successfully.
     * @return <code>true</code> if the given activity shall be added, <code>false</code> if not.
     */
    boolean checkTruncating(final Activity activity, boolean finishedSuccess) {
        if (!truncateOnSuccess && truncateLimit >= Integer.MAX_VALUE) {
            // truncating is disabled
            return true;
        }
        if (isTruncatingEvents) {
            // already truncating completely
            return false;
        }
        // collect IDs
        final boolean hasEvent = hasEvent(activity);
        if (!isTruncatingActivities) {
            activityIds.put(activity.getInstanceId(), hasEvent);
            // check limit reached
            if (truncateOnSuccess && finishedSuccess || activityIds.size() > truncateLimit) {
                isTruncatingActivities = true;
                activityIds.values().removeIf(b -> !b);
                LOG.debug("Start truncating activities for {}", this);
            }
        }
        if (isTruncatingActivities && hasEvent && !isTruncatingEvents) {
            activityIds.put(activity.getInstanceId(), true);
            // check limit reached again
            if (activityIds.size() > truncateLimit) {
                isTruncatingEvents = true;
                activityIds.clear();
                LOG.debug("Start truncating events for {}", this);
            }
        }
        // result for the given activity
        if (isTruncatingEvents) {
            // full stop
            return false;
        }
        // no truncating, or truncating activities but not events
        return !isTruncatingActivities || hasEvent;
    }

    private void calculateEstimatedSize() {
        synchronized (activities) {
            estimatedSize =
                1000 + activities.values().stream().mapToLong(a -> ((ActivityImpl) a).getEstimatedSize()).sum();
        }
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
        synchronized (activities) {
            if (!normalCompletion) {
                // unhandled error
                lastStatus = JobStatus.ERROR;
                commitActivityError();
            } else if (lastStatus == null || lastStatus.getValue() <= JobStatus.RUNNING.getValue()) {
                // if we never had a status update, we are setting SUCCESS
                lastStatus = JobStatus.SUCCESS;
            }
            //end all not ended activities
            activities.values().stream()
                .filter(a -> a.getActivityStatus() == null || a.getActivityStatus() == ActivityStatus.RUNNING)
                .forEach(Activity::end);
            if (getEndTime() == null) {
                setEndTime(DateTimeUtility.now());
            }
            if (!hasStarted()) {
                LOG.warn("Job has been finished before it started.");
            }
            finished = true;
            processModel.getNjams().removeJob(getJobId());
            flush();
        }
    }

    /**
     * If the job has failed, there was an unhandled error that should have been recorded by
     * {@link #setActivityErrorEvent(Activity, ErrorEvent)}. If so, the error is now committed to an according error
     * event on this activity.
     */
    private void commitActivityError() {
        if (allErrors) {
            // all errors have already been added to their activities.
            return;
        }
        synchronized (errorLock) {
            if (errorActivity != null) {
                LOG.debug("Committing error event to {}", errorActivity);
                updateActivityErrorEvent(errorActivity, errorEvent);
                if (getActivityByInstanceId(errorActivity.getInstanceId()) == null) {
                    // the activity is already sent, i.e., re-send
                    addActivity(errorActivity);
                }
                errorActivity = null;
                errorEvent = null;
            }
        }
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
    public void setActivityErrorEvent(Activity errorActivity, ErrorEvent errorEvent) {
        if (errorActivity != null && errorEvent != null) {
            if (allErrors) {
                // add all errors directly to the activity
                LOG.debug("Adding error event to {}", errorActivity);
                updateActivityErrorEvent((ActivityImpl) errorActivity, errorEvent);
            } else {
                // store as last error until job-end; then we know whether to add or ignore it
                LOG.debug("Storing error event for {}", errorActivity);
                synchronized (errorLock) {
                    this.errorActivity = (ActivityImpl) errorActivity;
                    this.errorEvent = errorEvent;
                }
            }
        }
    }

    /**
     * Update the event information on the given activity, based on the given error information.
     *
     * @param activity
     * @param errorEvent
     */
    private void updateActivityErrorEvent(ActivityImpl activity, ErrorEvent errorEvent) {
        EventStatus status = errorEvent.getStatus() == null ? EventStatus.ERROR : errorEvent.getStatus();
        activity.setActivityStatus(status.mapToActivityStatus());
        activity.setEventStatus(status);
        if (activity.getExecution() == null) {
            activity.setExecution(
                errorEvent.getEventTime() == null ? DateTimeUtility.now() : errorEvent.getEventTime());
        }
        activity.setEventCode(errorEvent.getCode());
        activity.setEventMessage(errorEvent.getMessage());
        activity.setEventPayload(errorEvent.getPayload());
        activity.setStackTrace(errorEvent.getStacktrace());
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
        lastStatus = status;
        if (maxSeverity == null || maxSeverity.getValue() < lastStatus.getValue()) {
            maxSeverity = status;
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
        this.correlationLogId = limitLength("correlationLogId", correlationLogId, MAX_VALUE_LIMIT);
    }

    /**
     * Returns the correlation log id of this job.
     *
     * @return collreation log id
     */
    @Override
    public String getCorrelationLogId() {
        return correlationLogId;
    }

    /**
     * Set the parentLogId
     *
     * @param parentLogId parentLogId to set
     */
    @Override
    public void setParentLogId(String parentLogId) {
        this.parentLogId = limitLength("parentLogId", parentLogId, MAX_VALUE_LIMIT);
    }

    /**
     * Return the parentLogId
     *
     * @return the parentLogId
     */
    @Override
    public String getParentLogId() {
        return parentLogId;
    }

    /**
     * Set the externalLogId
     *
     * @param externalLogId texternalLogId to set
     */
    @Override
    public void setExternalLogId(String externalLogId) {
        this.externalLogId = limitLength("externalLogId", externalLogId, MAX_VALUE_LIMIT);
    }

    /**
     * Return the externalLogId
     *
     * @return the externalLogId
     */
    @Override
    public String getExternalLogId() {
        return externalLogId;
    }

    /**
     * Set the businessService as String
     *
     * @param businessService businessService to set
     */
    @Override
    public void setBusinessService(String businessService) {
        setBusinessService(new Path(businessService));
    }

    /**
     * Set the businessService as Path
     *
     * @param businessService businessService to set
     */
    @Override
    public void setBusinessService(Path businessService) {
        if (businessService != null) {
            this.businessService = limitLength("businessService", businessService.toString(), MAX_VALUE_LIMIT);
        }
    }

    /**
     * Return the businessService
     *
     * @return the businessService
     */
    @Override
    public String getBusinessService() {
        return businessService;
    }

    /**
     * Set the businessObject as String
     *
     * @param businessObject businessObject to set
     */
    @Override
    public void setBusinessObject(String businessObject) {
        setBusinessObject(new Path(businessObject));
    }

    /**
     * Set the binsessObject as Path
     *
     * @param businessObject businessObject to set
     */
    @Override
    public void setBusinessObject(Path businessObject) {
        if (businessObject != null) {
            this.businessObject = limitLength("businessObject", businessObject.toString(), MAX_VALUE_LIMIT);
        }
    }

    /**
     * Return the businessObject
     *
     * @return the businessObject
     */
    @Override
    public String getBusinessObject() {
        return businessObject;
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
     * Return the Attribute name to a given value
     *
     * @param name attribute name
     * @return attribute value
     */
    @Override
    public String getAttribute(final String name) {
        String val = attributes.get(name);
        if (val != null) {
            return val;
        }
        return flushedAttributes.get(name);
    }

    /**
     * Returns a detached copy of all attributes for this job. I.e., any modification on the returned map has no effect
     * on this job instance!
     *
     * @return list of attributes
     */
    @Override
    public Map<String, String> getAttributes() {
        final Map<String, String> attr = new TreeMap<>(flushedAttributes);
        attr.putAll(attributes);
        return attr;
    }

    /**
     * Return if the job contains a attribute for a given name
     *
     * @param name attribute name to check
     * @return true if found, false if not found
     */
    @Override
    public boolean hasAttribute(final String name) {
        return attributes.containsKey(name) || flushedAttributes.containsKey(name);
    }

    /**
     * Marks that a job shall collect trace information for each activity
     * (including sub processes).
     *
     * @param deepTrace <b>true</b> if deep trace shall be activated.
     */
    @Override
    public void setDeepTrace(boolean deepTrace) {
        this.deepTrace = deepTrace;
    }

    /**
     * Indicates that trace information shall be collected for all activites of
     * this job (including sub processes).
     *
     * @return <b>true</b> if and only if deep trace is enabled.
     */
    @Override
    public boolean isDeepTrace() {
        return deepTrace;
    }

    /**
     * Return the last push LocalDateTime
     *
     * @return the last push LocalDateTime
     */
    public LocalDateTime getLastFlush() {
        return lastFlush;
    }

    /**
     * Marks this job instance as instrumented.
     */
    public void setInstrumented() {
        instrumented = true;
    }

    /**
     * @return the traces
     */
    @Override
    public boolean isTraces() {
        return traces;
    }

    /**
     * @param traces the traces to set
     */
    public void setTraces(boolean traces) {
        this.traces = traces;
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
        return properties.containsKey(key);
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
        properties.put(key, value);
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
     * This method removes all not running activities from the activities map if
     * the activity has a parent, remove the activity from the childActivity map
     * of the parent.
     */
    private void removeNotRunningActivities() {
        int loggingSum = 0;
        synchronized (activities) {
            Iterator<Activity> iterator = activities.values().iterator();
            while (iterator.hasNext()) {
                Activity a = iterator.next();
                if (a.getActivityStatus() != ActivityStatus.RUNNING) {
                    flushedActivities.remove(a.getInstanceId());
                    loggingSum++;
                    iterator.remove();
                    GroupImpl parent = (GroupImpl) a.getParent();
                    if (parent != null) {
                        parent.removeChildActivity(a.getInstanceId());
                    }
                    if (a == startActivity) {
                        startActivity = null;
                    }
                } else {
                    flushedActivities.add(a.getInstanceId());
                }

            }
            LOG.trace("{} activities have been removed from {}. Still running: {}", loggingSum, getLogId(),
                activities.size());
        }
    }

    /**
     * @return the estimatedSize
     */
    public long getEstimatedSize() {
        return estimatedSize;
    }

    /**
     * Add estimatedSize to the estimatedSize of the activity
     *
     * @param estimatedSize estimatedSize to add
     */
    public void addToEstimatedSize(long estimatedSize) {
        this.estimatedSize += estimatedSize;
    }

    @Override
    public boolean needsData(ActivityModel activityModel) {
        if (deepTrace || activityModel.isStarter()) {
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
    public boolean isActiveTracepoint(TracepointExt tracepoint) {
        if (tracepoint != null) {
            //if tracepoint exists, check timings
            LocalDateTime now = DateTimeUtility.now();
            //timing is right, and iterations are less than configured
            return !now.isBefore(tracepoint.getStarttime()) && now.isBefore(tracepoint.getEndtime())
                && !tracepoint.iterationsExceeded();
        }
        return false;
    }

    /**
     * Returns the runtime configuration for a specific {@link ActivityModel} if any.
     *
     * @param activityModel The model for that configuration shall be returned.
     * @return May be <code>null</code> if no configuration exists.
     */
    public ActivityConfiguration getActivityConfiguration(ActivityModel activityModel) {
        if (activityModel == null) {
            return null;
        }
        ProcessModel processModel = activityModel.getProcessModel();
        if (processModel == null) {
            return null;
        }
        Configuration configuration = processModel.getNjams().getConfiguration();
        if (configuration == null) {
            return null;
        }
        ProcessConfiguration processConfig = configuration.getProcess(processModel.getPath().toString());
        if (processConfig == null) {
            return null;
        }
        return processConfig.getActivity(activityModel.getId());

    }

    /**
     * Return if recording is activated for this job
     *
     * @return true if activated, false if not
     */
    public boolean isRecording() {
        return recording;
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
        pluginDataItems.add((PluginDataItem) pluginDataItem);
    }

    /**
     * This method sets the businessStart in the ActivityImpl
     *
     * @param businessStart the businessStart to set
     */
    @Override
    public void setBusinessStart(LocalDateTime businessStart) {
        this.businessStart = businessStart;
    }

    /**
     * This method sets the businessEnd in the ActivityImpl
     *
     * @param businessEnd the businessEnd to set
     */
    @Override
    public void setBusinessEnd(LocalDateTime businessEnd) {
        this.businessEnd = businessEnd;
    }

    /**
     * This method returns if the jobImpl has already been started.
     *
     * @return true, if the job has started already (RUNNING, SUCCESS, WARNING,
     * ERROR). return false, if the job hasn't been started (CREATED)
     */
    public boolean hasStarted() {
        return lastStatus != JobStatus.CREATED;
    }

    /**
     * This method is used to put attributes in the attributes map.
     *
     * @param key   the key to set
     * @param value the value to set
     */
    @Override
    public void addAttribute(final String key, String value) {
        if (value == null) {
            return;
        }
        String limitKey = limitLength("attributeName", key, 500);
        synchronized (attributes) {
            attributes.put(limitKey, DataMasking.maskString(limitPayload(value)));
        }
    }

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
     */
    public static String limitLength(String fieldName, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            LOG.warn("Value of field '{}' exceeds max length of {} characters. Value will be truncated.", fieldName,
                maxLength);
            return value.substring(0, maxLength - 1);
        }
        return value;
    }

    /**
     * If limiting payload size is enabled, this method ensures that the given payload is handled accordingly.
     * @param payload The payload to limit.
     * @return The given payload adjusted to the configured limits.
     */
    String limitPayload(String payload) {
        if (payload == null || payloadLimit == null || payload.length() <= payloadLimit.getValue()) {
            return payload;
        }
        final int limit = payloadLimit.getValue();
        if (limit > 0 && payloadLimit.getKey()) {
            // truncate
            final String suffix = PAYLOAD_TRUNCATED_SUFFIX;
            return payload.substring(0, limit) + suffix;
        }
        // discard
        return PAYLOAD_DISCARDED_MESSAGE;
    }
}
