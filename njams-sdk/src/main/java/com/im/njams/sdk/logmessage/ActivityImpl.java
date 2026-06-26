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

import static com.im.njams.sdk.logmessage.JobImpl.MAX_VALUE_LIMIT;
import static com.im.njams.sdk.logmessage.JobImpl.limitLength;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import javax.xml.bind.annotation.XmlTransient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.faizsiegeln.njams.messageformat.v4.logmessage.Predecessor;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.RuleType;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.serializer.SerializerResult;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.TracepointExt;
import com.im.njams.sdk.logmessage.ExtractHandler.ExtractSource;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.SubProcessActivityModel;
import com.im.njams.sdk.model.TransitionModel;
import com.im.njams.sdk.utils.StringUtils;

/**
 * This is internal implementation of the Activity. It is a extension of the
 * MesasgeFormat activity, and provided functionality for easy chaining
 * activities.
 *
 * @author pnientiedt
 * @version 4.0.6
 */
public class ActivityImpl extends com.faizsiegeln.njams.messageformat.v4.logmessage.Activity implements Activity {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityImpl.class);

    //This lock is used for synchronizing the access to the activities attributes
    private final Object attributesLock = new Object();

    // Job will be hold because it will be needed when creating the next activity via stepTo logic
    private final JobImpl job;
    private final ActivityModel activityModel;
    private final Extract extract;
    private boolean starter = false;
    private GroupImpl parent = null;
    private final boolean traceEnabled;
    // used only for calculating duration in ms
    private long startTime = System.currentTimeMillis();
    // stores excecution time until it's needed
    private LocalDateTime tmpExecution = null;
    private boolean executionRequired = false;

    // Base size estimate (in characters) attributed to every activity regardless of content.
    // Approximates the serialized structural fields of a typical activity (modelId, instanceId,
    // sequence, iteration, execution timestamp and a single predecessor), with a little headroom.
    static final long BASE_ESTIMATED_SIZE = 400L;

    private long estimatedSize = BASE_ESTIMATED_SIZE;
    private boolean ended = false;
    private boolean inputProcessecd = false;
    private boolean outputProcessed = false;

    public ActivityImpl(JobImpl job, ActivityModel model) {
        this.job = job;
        activityModel = Objects.requireNonNull(model);
        setModelId(model.getId());
        ActivityConfiguration activityConfig = job.getActivityConfiguration(activityModel);
        if (activityConfig != null) {
            extract = activityConfig.getExtract();
        } else {
            extract = null;
        }
        // SDK-159: always call to have deep-trace evaluated
        traceEnabled = initTraceFromSettings();
    }

    /**
     * Returns the job where Activity is located. Must be transient so that it
     * will not be printed out during serialization time.
     *
     * @return the {@link Job}
     */
    @XmlTransient
    public Job getJob() {
        return job;
    }

    /**
     * Add a new Predecessor by given predecessorInstanceId and
     * transitionModelId
     *
     * @param predecessorInstanceId predecessorInstanceId to add
     * @param transitionModelId transitionModelId to add
     */
    @Override
    public void addPredecessor(String predecessorInstanceId, String transitionModelId) {
        getPredecessors().add(new Predecessor(transitionModelId, predecessorInstanceId));
    }

    /**
     * Step to a new Activity with a given toActivityModel.
     *
     * @param toActivityModel * to step to
     * @return the ActivityBuilder for the new Activity
     */
    @Override
    public ActivityBuilder stepTo(ActivityModel toActivityModel) {
        if (toActivityModel instanceof GroupModel) {
            return stepToGroup((GroupModel) toActivityModel);
        }
        if (toActivityModel instanceof SubProcessActivityModel) {
            return stepToSubProcess((SubProcessActivityModel) toActivityModel);
        }
        TransitionModel transitionModel = toActivityModel.getIncomingTransitionFrom(getModelId());
        if (transitionModel == null) {
            throw new NjamsSdkRuntimeException("No transition from " + getModelId() + " to " + toActivityModel.getId()
                + " found!");
        }
        end();
        //check if a activity with the same modelId and the same iteration and parent already exists.
        final ActivityImpl toActivity = (ActivityImpl) job.getActivityByModelId(toActivityModel.getId());
        final ActivityBuilder builder;
        if (toActivity == null || !Objects.equals(toActivity.getIteration(), getIteration())
            || toActivity.getParent() != getParent()) {
            builder = new ActivityBuilder(job, toActivityModel);
        } else {
            builder = new ActivityBuilder(toActivity);
        }
        builder.stepFrom(this, transitionModel);
        return builder;
    }

    void setStarter(final boolean starter) {
        this.starter = starter;
    }

    /**
     * Return if this ActivityImpl is a starter activity
     *
     * @return true if it is a starter activity
     */
    @Override
    @XmlTransient
    public boolean isStarter() {
        return starter;
    }

    /**
     * @return the parent
     */
    @Override
    @XmlTransient
    public GroupImpl getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(GroupImpl parent) {
        this.parent = parent;
    }

    /**
     * Step to a new Group with a given toGroupModel.
     *
     * @param toGroupModel to step to
     * @return the GroupBuilder for the new Group
     */
    @Override
    public GroupBuilder stepToGroup(GroupModel toGroupModel) {
        end();
        TransitionModel transitionModel = toGroupModel.getIncomingTransitionFrom(getModelId());
        //check if a activity with the same modelId and the same iteration already exists.
        final GroupImpl toGroup = (GroupImpl) job.getActivityByModelId(toGroupModel.getId());
        final GroupBuilder builder;
        if (toGroup == null || !Objects.equals(toGroup.getIteration(), getIteration())
            || toGroup.getParent() != getParent()) {
            builder = new GroupBuilder(job, toGroupModel);
        } else {
            builder = new GroupBuilder(toGroup);
        }
        builder.stepFrom(this, transitionModel);
        return builder;
    }

    /**
     * Returns the event associated with this activity. If the activity does not
     * have an event, one will be created.
     *
     * @return The event of the activity
     */
    @Override
    public Event createEvent() {
        setExecutionIfNotSet();
        return new Event(this);
    }

    /**
     * Start this activity
     */
    public void start() {
        startTime = System.currentTimeMillis();
        if (getActivityStatus() == null) {
            setActivityStatus(ActivityStatus.RUNNING);
        }
    }

    /**
     * Process the input for this activity. Checks for tracepoints, extracts and
     * similar functionality to decide if and how this input data will be
     * handled.
     *
     * @param input input data
     */
    @Override
    public void processInput(Object input) {
        final String serializedData;
        final boolean truncated;
        final boolean needsData = needsData(extract);
        if (isTracing() || needsData) {
            final int sizeLimit = needsData ? 0 : job.getSerializeSizeHint();
            final SerializerResult result = job.getNjams().serializers().serialize(input, sizeLimit);
            serializedData = DataMasking.maskString(result == null ? null : result.value());
            truncated = result != null && result.truncated();
        } else {
            serializedData = null;
            truncated = false;
        }
        if (serializedData != null && isTracing()) {
            handleTracing(serializedData, truncated, true);
        }

        if (extract != null) {
            ExtractHandler.handleExtract(job, extract, this, ExtractSource.INPUT, serializedData);
        }

        inputProcessecd = true;
    }

    /**
     * Returns whether the given extract needs input data to be evaluated.
     * @param extract The {@link Extract} to check.
     * @return <code>true</code> only if the given extracts needs input data.
     */
    private boolean needsData(Extract extract) {
        return extract != null && extract.getExtractRules() != null && extract.getExtractRules()
            .stream().anyMatch(r -> r.getRuleType() == RuleType.XPATH || r.getRuleType() == RuleType.REGEXP
                || r.getRuleType() == RuleType.JMESPATH);
    }

    /**
     * End this activity
     */
    @Override
    public void end() {
        if (ended) {
            return;
        }
        if (getDuration() < 1) {
            setDuration(System.currentTimeMillis() - startTime);
        }
        if (getActivityStatus() == ActivityStatus.RUNNING) {
            setActivityStatus(ActivityStatus.SUCCESS);
        }
        if (this instanceof GroupImpl) {
            ((GroupImpl) this).getChildActivities().stream()
                .filter(a -> a.getActivityStatus() == ActivityStatus.RUNNING).forEach(Activity::end);
        }
        //process input and output if not done yet, for extract rules which do not need data
        if (!inputProcessecd) {
            processInput(null);
        }
        if (!outputProcessed) {
            processOutput(null);
        }
        ended = true;
    }

    /**
     * Process the output for this activity. Checks for tracepoints, extracts
     * and similar functionality to decide if and how this output data will be
     * handled.
     *
     * @param output output data
     */
    @Override
    public void processOutput(Object output) {
        final String serializedData;
        final boolean truncated;
        final boolean needsData = needsData(extract);
        if (isTracing() || needsData) {
            final int sizeLimit = needsData ? 0 : job.getSerializeSizeHint();
            final SerializerResult result = job.getNjams().serializers().serialize(output, sizeLimit);
            serializedData = DataMasking.maskString(result == null ? null : result.value());
            truncated = result != null && result.truncated();
        } else {
            serializedData = null;
            truncated = false;
        }
        if (serializedData != null && isTracing()) {
            handleTracing(serializedData, truncated, false);

        }
        if (extract != null) {
            ExtractHandler.handleExtract(job, extract, this, ExtractSource.OUTPUT, serializedData);
        }

        outputProcessed = true;
    }

    /**
     * Stores the serialized trace data on this activity, applying the configured payload limit
     * using the serializer's truncation flag.
     *
     * @param data                the already-masked serialized data
     * @param serializerTruncated whether the serializer truncated the data at the size limit
     * @param input               <code>true</code> for input, <code>false</code> for output
     */
    private void handleTracing(String data, boolean serializerTruncated, boolean input) {
        final String stored = job.applyLimit(data, serializerTruncated);
        if (input) {
            super.setInput(stored);
        } else {
            super.setOutput(stored);
        }
        addToEstimatedSize(data.length());
        setExecutionIfNotSet();
        job.setTraces(true);
    }

    /**
     * Returns whether or not tracing is currently enabled for this activity. As a side effect, the deep-trace flag
     * is on this  activity's tracepoint is evaluated and sets the according flag on the job if found.
     * @return <code>true</code> if tracing is currently enabled for this activity.
     */
    private boolean initTraceFromSettings() {
        // first check job's deepTrace setting
        if (job.isDeepTrace()) {
            return true;
        }
        // then check the activity's tracepoint, if any
        ActivityConfiguration activityConfig = job.getActivityConfiguration(activityModel);
        if (activityConfig == null) {
            return false;
        }
        TracepointExt tracepoint = activityConfig.getTracepoint();
        if (job.isActiveTracepoint(tracepoint)) {
            tracepoint.increaseCurrentIterations();
            //activate deeptrace if needed
            if (Boolean.TRUE.equals(tracepoint.isDeeptrace())) {
                job.setDeepTrace(true);
            }
            return true;
        }
        return false;
    }

    private boolean isTracing() {
        // SDK-210 Check deeptrace on each call
        return traceEnabled || job.isDeepTrace();

    }

    /**
     * Step to a new SubProcess with a given toSubProcessModel.
     *
     * @param toSubProcessModel to step to
     * @return the SubProcessBuilder for the new Group
     */
    @Override
    public SubProcessActivityBuilder stepToSubProcess(SubProcessActivityModel toSubProcessModel) {
        end();
        TransitionModel transitionModel = toSubProcessModel.getIncomingTransitionFrom(getModelId());
        //check if a activity with the same modelId and the same iteration already exists.
        final SubProcessActivityImpl toSubProcess =
            (SubProcessActivityImpl) job.getActivityByModelId(toSubProcessModel.getId());
        final SubProcessActivityBuilder builder;
        if (toSubProcess == null || !Objects.equals(toSubProcess.getIteration(), getIteration())
            || toSubProcess.getParent() != getParent()) {
            builder = new SubProcessActivityBuilder(job, toSubProcessModel);
        } else {
            builder = new SubProcessActivityBuilder(toSubProcess);
        }
        builder.stepFrom(this, transitionModel);
        if (toSubProcessModel.getSubProcess() != null) {
            builder.setSubProcess(toSubProcessModel.getSubProcess());
        }
        return builder;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{model = " + getModelId() + ", instance = " + getInstanceId() + '}';
    }

    /**
     * Sets the EventStatus for this job. An EventStatus can be 0 for INFO 1 for
     * SUCCESS 2 for WARNING 3 for ERROR or null for no eventStatus. Anything
     * else will change nothing. The status of the corresponding job to this
     * activity will be set to SUCCESS, WARNING or ERROR likewise. For INFO only
     * the eventStatus will be set, but the job status will stay the same.
     *
     * @param eventStatus eventStatus to set.
     */
    @Override
    public void setEventStatus(Integer eventStatus) {
        if (eventStatus == null) {
            super.setEventStatus(null);
            return;
        }
        try {
            //This will cause a NjamsSdkRuntimeException, if the eventStatus is not valid.
            setEventStatus(EventStatus.byValue(eventStatus));
        } catch (NjamsSdkRuntimeException e) {
            LOG.error("{} for job with logId: {}. Using old status: {}", e.getMessage(), job.getLogId(),
                super.getEventStatus());
        }

    }

    /**
     * Sets the EventStatus for this job. The status of the corresponding job to this
     * activity will be set to SUCCESS, WARNING or ERROR likewise. For INFO only
     * the eventStatus will be set, but the job status will stay the same.
     *
     * @param status eventStatus to set.
     */
    @Override
    public void setEventStatus(EventStatus status) {
        if (status == null) {
            super.setEventStatus(null);
            return;
        }
        try {
            setExecutionIfNotSet();
            int eventStatus = status.getValue();
            super.setEventStatus(eventStatus);
            if (1 <= eventStatus && eventStatus <= 3) {
                JobStatus possibleStatus = JobStatus.byValue(eventStatus);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("This status was set to job {} : {}", job.getLogId(), possibleStatus);
                }
                job.setStatus(possibleStatus);
                job.setInstrumented();
            }
        } catch (NjamsSdkRuntimeException e) {
            LOG.error("{} for job with logId: {}. Using old status: {}", e.getMessage(), job.getLogId(),
                super.getEventStatus());
        }
    }

    @Override
    public void setActivityError(ErrorEvent errorEvent) {
        setActivityStatus(ActivityStatus.ERROR);
        job.setActivityErrorEvent(this, errorEvent);
    }

    @Override
    public void setExecution(LocalDateTime execution) {
        if (executionRequired && execution != null) {
            // we need it, so use the given value
            super.setExecution(execution);
            tmpExecution = null;
        } else {
            // store in case we need it
            tmpExecution = execution;
        }
    }

    private void setExecutionIfNotSet() {
        if (getExecution() == null) {
            executionRequired = true;
            if (tmpExecution != null) {
                super.setExecution(tmpExecution);
                tmpExecution = null;
            } else {
                super.setExecution(startTime > 0 ? DateTimeUtility.fromMillis(startTime) : DateTimeUtility.now());
            }
        }
    }

    /**
     * @return the estimatedSize
     */
    @XmlTransient
    public long getEstimatedSize() {
        return estimatedSize;
    }

    /**
     * Adds to this activity's estimated size and, in turn, to the owning job's estimated size.
     *
     * @param estimatedSize estimated size to add
     */
    public void addToEstimatedSize(long estimatedSize) {
        this.estimatedSize += estimatedSize;
        job.addToEstimatedSize(estimatedSize);
    }

    /**
     * Process the startData. Checks if recording is activated for this job, and
     * decide if startdata will be needed.
     *
     * @param startData startData to process
     */
    @Override
    public void processStartData(Object startData) {
        if (!job.isRecording()) {
            return;
        }
        // SDK-462: a job carries a single start data; skip serialization if it is already set.
        if (job.isStartDataSet()) {
            LOG.warn("Start data was already set for job {}; ignoring this processStartData call "
                + "(the first start data wins).", job.getLogId());
            return;
        }
        final String serialized;
        final boolean truncated;
        if (job.isStartDataLimited()) {
            // SDK-420: serialize with the configured size limit so the truncation flag is reliable
            final SerializerResult result =
                job.getNjams().serializers().serialize(startData, job.getSerializeSizeHint());
            serialized = result == null ? null : result.value();
            truncated = result != null && result.truncated();
        } else {
            serialized = job.getNjams().serializers().serialize(startData);
            truncated = false;
        }
        storeStartData(serialized, truncated);
    }

    /**
     * This method masks the input and calls its super method.
     *
     * @param input the input to mask and set to the Activity.
     */
    @Override
    public void setInput(String input) {
        super.setInput(DataMasking.maskString(job.limitPayload(input)));
    }

    /**
     * This method masks the output and calls its super method.
     *
     * @param output the output to mask and set to the Activity.
     */
    @Override
    public void setOutput(String output) {
        super.setOutput(DataMasking.maskString(job.limitPayload(output)));
    }

    /**
     * This method masks the eventMessage and calls its super method.
     *
     * @param message the eventMessage to mask and set to the Activity.
     */
    @Override
    public void setEventMessage(String message) {
        setExecutionIfNotSet();
        final String limited = DataMasking.maskString(limitLength("eventMessage", message, MAX_VALUE_LIMIT));
        super.setEventMessage(limited);
        if (StringUtils.isNotBlank(message)) {
            final int size = limited == null ? 0 : limited.length();
            addToEstimatedSize(size);
            job.setInstrumented();
        }
    }

    /**
     * This method masks the eventCode and calls its super method.
     *
     * @param code the eventCode to mask and set to the Activity.
     */
    @Override
    public void setEventCode(String code) {
        setExecutionIfNotSet();
        final String limited = DataMasking.maskString(limitLength("eventCode", code, MAX_VALUE_LIMIT));
        super.setEventCode(limited);
        if (StringUtils.isNotBlank(code)) {
            final int size = limited == null ? 0 : limited.length();
            addToEstimatedSize(size);
            job.setInstrumented();
        }
    }

    /**
     * This method masks the eventPayload and calls its super method. After
     * that, it adds the size of the masked payload to the estimatedSize of this
     * and of the job.
     *
     * @param eventPayload the eventPayload to mask and set to the Activity.
     */
    @Override
    public void setEventPayload(final String eventPayload) {
        setExecutionIfNotSet();
        final String limited = DataMasking.maskString(job.limitPayload(eventPayload));
        super.setEventPayload(limited);
        // checking for the original value here for setting flags correctly
        if (StringUtils.isNotBlank(eventPayload)) {
            final int payloadSize = limited == null ? 0 : limited.length();
            addToEstimatedSize(payloadSize);
            job.setInstrumented();
        }
    }

    /**
     * This method masks the stackTrace and calls its super method. After that,
     * it adds the size of the masked stackTrace to the estimatedSize of this
     * and of the job.
     *
     * @param stackTrace the stackTrace to mask and set to the Activity.
     */
    @Override
    public void setStackTrace(final String stackTrace) {
        setExecutionIfNotSet();
        final String limited = DataMasking.maskString(job.limitPayload(stackTrace));
        super.setStackTrace(limited);
        // checking for the original value here for setting flags correctly
        if (StringUtils.isNotBlank(stackTrace)) {
            int stackTraceSize = limited == null ? 0 : limited.length();
            addToEstimatedSize(stackTraceSize);
            job.setInstrumented();
        }
    }

    /**
     * This method masks the startData and calls its super method. After that,
     * it adds the size of the masked startData to the estimatedSize of this and
     * of the job.
     *
     * <p>Start data belongs to the job: only the first start data set for a job is kept (SDK-462).
     * A later call (on this or any other activity of the same job) is ignored and logged, so a job
     * transmits at most one start data. A <code>null</code> value does not consume the slot.</p>
     *
     * @param startData the startData to mask and set to the Activity.
     */
    @Override
    public void setStartData(String startData) {
        // no serializer truncation flag for an already-serialized string: limiting (if enabled) is length-based
        storeStartData(startData, false);
    }

    /**
     * Stores the (single) start data for the owning job, claiming the job's start-data slot (SDK-462) and,
     * when start-data limiting is enabled (SDK-420), applying the configured payload limit and clearing the
     * job's recorded flag if the start data was actually truncated or discarded.
     *
     * @param serialized          the serialized start data, may be <code>null</code>
     * @param serializerTruncated whether the serializer already truncated the value at the configured limit
     */
    private void storeStartData(String serialized, boolean serializerTruncated) {
        final String masked = DataMasking.maskString(serialized);
        if (masked != null && !job.claimStartData()) {
            LOG.warn("Start data was already set for job {}; ignoring this call (the first start data wins).",
                job.getLogId());
            return;
        }
        final String stored;
        if (job.isStartDataLimited()) {
            if (job.exceedsStartDataLimit(masked, serializerTruncated)) {
                // truncated or discarded -> this job can no longer be replayed
                job.revokeRecorded();
            }
            stored = job.applyLimit(masked, serializerTruncated);
        } else {
            stored = masked;
        }
        super.setStartData(stored);
        if (stored != null) {
            addToEstimatedSize(stored.length());
        }
    }

    /**
     * This method masks the attributes map and calls its super method.
     * Furthermore it adds the masked attributes to the jobs attributes as well.
     *
     * @param attributes the attributes to mask and set to the Activity.
     */
    @Override
    public void setAttributes(Map<String, String> attributes) {
        if (attributes == null) {
            return;
        }
        synchronized (attributesLock) {
            attributes.entrySet().forEach(e -> addAttribute(e.getKey(), e.getValue()));
        }
    }

    /**
     * This method masks the attributes value and calls its super method.
     * Furthermore it adds the masked attribute to the jobs attributes aswell.
     *
     * @param key the key for the activities attribute map
     * @param value the value to mask and set to the Activity.
     */
    @Override
    public void addAttribute(String key, String value) {
        if (value == null) {
            return;
        }
        String limitKey = limitLength("attributeName", key, 500);
        String maskedValue = DataMasking.maskString(job.limitPayload(value));
        synchronized (attributesLock) {
            job.addAttribute(limitKey, maskedValue);
            super.addAttribute(limitKey, maskedValue);
        }
    }

    /**
     * This method returns the attributes of this activity as an unmodifiable
     * map. Use setAttributes or addAttribute to modify the Map.
     *
     * @return Unmodifiable map of attributes of this activity.
     */
    @Override
    public Map<String, String> getAttributes() {
        synchronized (attributesLock) {
            return Collections.unmodifiableMap(super.getAttributes());
        }
    }

    @XmlTransient
    ActivityModel getActivityModel() {
        return activityModel;
    }

}
