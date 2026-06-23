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

import com.faizsiegeln.njams.messageformat.v4.logmessage.interfaces.IPluginDataItem;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.SubProcessActivityModel;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * This represents an instance of a process/flow etc in engine to monitor.
 * <p>
 * <b>Thread safety.</b> A {@code Job} is the shared unit of concurrency. Multiple threads may
 * concurrently create activities and groups in the same job (e.g. when monitoring parallel branch
 * execution under one job) and record into their <em>own</em> {@link Activity} / {@link Group}
 * instances. The job-level state that such recording updates as a side effect — status and maximum
 * severity, the instrumentation and trace flags, the estimated message size, attributes, the
 * captured activity error, and the {@linkplain #metadata() metadata} fields — is synchronized
 * internally and safe for concurrent use.
 * <p>
 * An individual {@link Activity} or {@link Group} instance, in contrast, is <b>thread-confined</b>:
 * it is expected to be accessed by a single thread. The SDK does not synchronize mutation of one
 * activity/group instance, because parallel threads are expected to work on separate instances. A
 * caller that genuinely shares a single activity or group instance across threads must synchronize
 * those calls itself. See {@link Activity} and {@link Group} for details.
 *
 * @author pnientiedt
 */
public interface Job {

    /**
     * Adds a new Activity to the Job.
     *
     * @param activity to add
     * @deprecated Use {@code job.activities().add(activity)} instead — obtain the facet via
     *             {@link #activities()} and call {@link JobActivities#add(Activity)}. Unlike this
     *             method, the replacement does NOT require the job to be started: activities
     *             created before start are sent with the first log message after the job starts.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void addActivity(final Activity activity);

    /**
     * Creates ActivityBuilder with a given ActivityModel.
     *
     * @param activityModel to create
     * @return a builder
     * @deprecated Use {@code job.activities().create(activityModel)} instead — obtain the facet
     *             via {@link #activities()} and call {@link JobActivities#create(ActivityModel)}.
     *             Unlike this method, building via the replacement does NOT require the job to be
     *             started.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public ActivityBuilder createActivity(ActivityModel activityModel);

    /**
     * Creates GroupBuilder with a given GroupModel.
     *
     * @param groupModel to create
     * @return a builder
     * @deprecated Use {@code job.activities().createGroup(groupModel)} instead — obtain the facet
     *             via {@link #activities()} and call {@link JobActivities#createGroup(GroupModel)}.
     *             Unlike this method, building via the replacement does NOT require the job to be
     *             started.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public GroupBuilder createGroup(GroupModel groupModel);

    /**
     * Creates SubProcessBuilder with a given SubProcessModel.
     *
     * @param groupModel to create
     * @return a builder
     * @deprecated Use {@code job.activities().createSubProcess(subProcessModel)} instead — obtain
     *             the facet via {@link #activities()} and call
     *             {@link JobActivities#createSubProcess(SubProcessActivityModel)}. Unlike this
     *             method, building via the replacement does NOT require the job to be started.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public SubProcessActivityBuilder createSubProcess(SubProcessActivityModel groupModel);

    /**
     * Set the logMessage status to success if the status is running. Then flush
     * it
     * @deprecated Replaced by {@link #end(boolean)}
     */
    @Deprecated
    public void end();

    /**
     * Ends processing for this job instance.
     * @param normalCompletion Whether the executing engine reported normal completion for this job,
     * or a failure (<code>false</code>).<br>
     * This value is used for calculating job status. If the engine reports a fault (<code>false</code>)
     * the job will always gets {@link JobStatus#ERROR}. Otherwise the status results from
     * the nJAMS events that have been generated during execution.
     */
    public void end(boolean normalCompletion);

    /**
     * Returns a snapshot of all activities currently recorded in this job. The returned collection
     * is independent of the job's internal state and can be iterated freely without risk of
     * {@link java.util.ConcurrentModificationException}.
     *
     * @return a snapshot of all activities at the time of the call
     * @deprecated Use {@code job.activities().getAll()} instead — obtain the facet via
     *             {@link #activities()} and call {@link JobActivities#getAll()}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public Collection<Activity> getActivities();

    /**
     * Returns a activity to a given instanceId.
     *
     * @param activityInstanceId to get
     * @return the {@link Activity}
     * @deprecated Use {@code job.activities().getByInstanceId(activityInstanceId)} instead —
     *             obtain the facet via {@link #activities()} and call
     *             {@link JobActivities#getByInstanceId(String)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public Activity getActivityByInstanceId(String activityInstanceId);

    /**
     * Returns the last added activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     * @deprecated Use {@code job.activities().getByModelId(activityModelId)} instead — obtain the
     *             facet via {@link #activities()} and call
     *             {@link JobActivities#getByModelId(String)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public Activity getActivityByModelId(String activityModelId);

    /**
     * Return the Attribute name to a given value
     *
     * @param name attribute name
     * @return attribute value
     * @deprecated Use {@code job.attributes().get(name)} instead — obtain the facet via
     *             {@link #attributes()} and call {@link JobAttributes#get(String)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public String getAttribute(final String name);

    /**
     * Return all attributes for this job
     *
     * @return unmodifiable list of attributes
     * @deprecated Use {@code job.attributes().getAll()} instead — obtain the facet via
     *             {@link #attributes()} and call {@link JobAttributes#getAll()}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public Map<String, String> getAttributes();

    /**
     * @return the businessEnd
     * @deprecated Use {@code job.metadata().getBusinessEnd()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link JobMetadata#getBusinessEnd()}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public LocalDateTime getBusinessEnd();

    /**
     * Return the businessObject
     *
     * @return the businessObject
     * @deprecated Use {@code job.metadata().getBusinessObject()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link JobMetadata#getBusinessObject()}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public String getBusinessObject();

    /**
     * Return the businessService
     *
     * @return the businessService
     * @deprecated Use {@code job.metadata().getBusinessService()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link JobMetadata#getBusinessService()}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public String getBusinessService();

    /**
     * @return the businessStart
     * @deprecated Use {@code job.metadata().getBusinessStart()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link JobMetadata#getBusinessStart()}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public LocalDateTime getBusinessStart();

    /**
     * Returns the last added and completed activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     * @deprecated Use {@code job.activities().getCompletedByModelId(activityModelId)} instead —
     *             obtain the facet via {@link #activities()} and call
     *             {@link JobActivities#getCompletedByModelId(String)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public Activity getCompletedActivityByModelId(String activityModelId);

    /**
     * Returns the correlation log id of this job.
     *
     * @return collreation log id
     * @deprecated Use {@code job.metadata().getCorrelationLogId()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link JobMetadata#getCorrelationLogId()}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public String getCorrelationLogId();

    /**
     * Return the endTime
     *
     * @return the endTime
     */
    public LocalDateTime getEndTime();

    /**
     * Return the externalLogId
     *
     * @return the externalLogId
     * @deprecated Use {@code job.metadata().getExternalLogId()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link JobMetadata#getExternalLogId()}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public String getExternalLogId();

    /**
     * Return the jobId
     *
     * @return the jobId
     */
    public String getJobId();

    /**
     * Return the logId
     *
     * @return the logId
     */
    public String getLogId();

    /**
     * Gets the maximal severity of this job job.
     *
     * @return max severity
     */
    public JobStatus getMaxSeverity();

    /**
     * Return the parentLogId
     *
     * @return the parentLogId
     * @deprecated Use {@code job.metadata().getParentLogId()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link JobMetadata#getParentLogId()}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public String getParentLogId();

    /**
     * Gets a properties value. Properties will not be send within project
     * messages.
     *
     * @param key name of the property
     * @return Properties value of <b>null</b>
     * @deprecated Use {@code job.properties().get(key)} instead — obtain the facet via
     *             {@link #properties()} and call {@link JobProperties#get(String)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public Object getProperty(final String key);

    /**
     * Returns the last added and running activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     * @deprecated Use {@code job.activities().getRunningByModelId(activityModelId)} instead —
     *             obtain the facet via {@link #activities()} and call
     *             {@link JobActivities#getRunningByModelId(String)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public Activity getRunningActivityByModelId(String activityModelId);

    /**
     * Return the start activity
     *
     * @return the start activity
     * @deprecated Use {@code job.activities().getStart()} instead — obtain the facet via
     *             {@link #activities()} and call {@link JobActivities#getStart()}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public Activity getStartActivity();

    /**
     * Return the startTime
     *
     * @return the startTime
     */
    public LocalDateTime getStartTime();

    /**
     * Gets a status for this {@link Job}
     *
     * @return the status
     */
    public JobStatus getStatus();

    /**
     * Return if the job contains a attribute for a given name
     *
     * @param name attribute name to check
     * @return true if found, false if not found
     * @deprecated Use {@code job.attributes().has(name)} instead — obtain the facet via
     *             {@link #attributes()} and call {@link JobAttributes#has(String)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public boolean hasAttribute(final String name);

    /**
     * Checks whether the activity has a property with a given name.
     *
     * @param key name of the property
     * @return <b>true</b> if and only if a property with the given name exists.
     * @deprecated Use {@code job.properties().has(key)} instead — obtain the facet via
     *             {@link #properties()} and call {@link JobProperties#has(String)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public boolean hasProperty(final String key);

    /**
     * Indicates that trace information shall be collected for all activites of
     * this job (including sub processes).
     *
     * @return <b>true</b> if and only if deep trace is enabled.
     * @deprecated Use {@code job.tracing().isDeepTrace()} instead — obtain the facet via
     *             {@link #tracing()} and call {@link JobTracing#isDeepTrace()}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public boolean isDeepTrace();

    /**
     * Marks that a job shall collect trace information for each activity
     * (including sub processes).
     *
     * @param deepTrace
     * <b>true</b> if deep trace shall be activiated.
     * @deprecated Use {@code job.tracing().setDeepTrace(deepTrace)} instead — obtain the facet via
     *             {@link #tracing()} and call {@link JobTracing#setDeepTrace(boolean)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setDeepTrace(boolean deepTrace);

    /**
     * Indicates whether the job is already finished or not.
     *
     * @return <b>true</b> if and only if the job is already finished, else
     * <b>false</b>
     */
    public boolean isFinished();

    /**
     * @return the traces
     * @deprecated Use {@code job.tracing().isTraces()} instead — obtain the facet via
     *             {@link #tracing()} and call {@link JobTracing#isTraces()}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public boolean isTraces();

    /**
     * Removes the property with a given name
     *
     * @param key name of the property
     * @return Previous value of the property (if it existed) or else
     * <b>null</b>.
     * @deprecated Use {@code job.properties().remove(key)} instead — obtain the facet via
     *             {@link #properties()} and call {@link JobProperties#remove(String)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public Object removeProperty(final String key);

    /**
     * Adds an Attribute to the job
     *
     * @param name the name of the attribute
     * @param value the value of the attribute
     * @deprecated Use {@code job.attributes().add(name, value)} instead — obtain the facet via
     *             {@link #attributes()} and call {@link JobAttributes#add(String, String)}. Unlike
     *             this method, the replacement throws an exception when called after
     *             {@link #end(boolean)}, because the final log message has already been sent and
     *             a later change is never sent.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void addAttribute(final String name, final String value);

    /**
     * Set the businessObject as String
     *
     * @param businessObject businessObject to set
     * @deprecated Use {@code job.metadata().setBusinessObject(businessObject)} instead — obtain
     *             the facet via {@link #metadata()} and call
     *             {@link JobMetadata#setBusinessObject(String)}. Unlike this method, the
     *             replacement throws an exception when called after {@link #end(boolean)},
     *             because the final log message has already been sent and a later change is
     *             never sent.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setBusinessObject(String businessObject);

    /**
     * Set the binsessObject as Path
     *
     * @param businessObject businessObject to set
     * @deprecated Use {@code job.metadata().setBusinessObject(businessObject)} instead — obtain
     *             the facet via {@link #metadata()} and call
     *             {@link JobMetadata#setBusinessObject(Path)}. Unlike this method, the replacement
     *             throws an exception when called after {@link #end(boolean)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setBusinessObject(Path businessObject);

    /**
     * Set the businessObject's start
     *
     * @param businessStart startTime to set
     * @deprecated Use {@code job.metadata().setBusinessStart(businessStart)} instead — obtain the
     *             facet via {@link #metadata()} and call
     *             {@link JobMetadata#setBusinessStart(LocalDateTime)}. Unlike this method, the
     *             replacement throws an exception when called after {@link #end(boolean)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setBusinessStart(LocalDateTime businessStart);

    /**
     * Set the businessObject's end
     *
     * @param businessEnd endTime to set
     * @deprecated Use {@code job.metadata().setBusinessEnd(businessEnd)} instead — obtain the
     *             facet via {@link #metadata()} and call
     *             {@link JobMetadata#setBusinessEnd(LocalDateTime)}. Unlike this method, the
     *             replacement throws an exception when called after {@link #end(boolean)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setBusinessEnd(LocalDateTime businessEnd);

    /**
     * Set the businessService as String
     *
     * @param businessService businessService to set
     * @deprecated Use {@code job.metadata().setBusinessService(businessService)} instead — obtain
     *             the facet via {@link #metadata()} and call
     *             {@link JobMetadata#setBusinessService(String)}. Unlike this method, the
     *             replacement throws an exception when called after {@link #end(boolean)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setBusinessService(String businessService);

    /**
     * Set the businessService as Path
     *
     * @param businessService businessService to set
     * @deprecated Use {@code job.metadata().setBusinessService(businessService)} instead — obtain
     *             the facet via {@link #metadata()} and call
     *             {@link JobMetadata#setBusinessService(Path)}. Unlike this method, the
     *             replacement throws an exception when called after {@link #end(boolean)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setBusinessService(Path businessService);

    /**
     * Sets the correlation log id of this job.
     *
     * @param correlationLogId collreation log id
     * @deprecated Use {@code job.metadata().setCorrelationLogId(correlationLogId)} instead —
     *             obtain the facet via {@link #metadata()} and call
     *             {@link JobMetadata#setCorrelationLogId(String)}. Unlike this method, the
     *             replacement throws an exception when called after {@link #end(boolean)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setCorrelationLogId(final String correlationLogId);

    /**
     * Sets the end timestamp of a job.
     *
     * @param jobEnd job end
     */
    public void setEndTime(final LocalDateTime jobEnd);

    /**
     * Set the externalLogId
     *
     * @param externalLogId texternalLogId to set
     * @deprecated Use {@code job.metadata().setExternalLogId(externalLogId)} instead — obtain the
     *             facet via {@link #metadata()} and call
     *             {@link JobMetadata#setExternalLogId(String)}. Unlike this method, the
     *             replacement throws an exception when called after {@link #end(boolean)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setExternalLogId(String externalLogId);

    /**
     * Set the parentLogId
     *
     * @param parentLogId parentLogId to set
     * @deprecated Use {@code job.metadata().setParentLogId(parentLogId)} instead — obtain the
     *             facet via {@link #metadata()} and call
     *             {@link JobMetadata#setParentLogId(String)}. Unlike this method, the replacement
     *             throws an exception when called after {@link #end(boolean)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setParentLogId(String parentLogId);

    /**
     * Sets a properties value. Properties will not be send within project
     * messages.
     *
     * @param key name of the property
     * @param value value of the property
     * @deprecated Use {@code job.properties().set(key, value)} instead — obtain the facet via
     *             {@link #properties()} and call {@link JobProperties#set(String, Object)}.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public void setProperty(final String key, final Object value);

    /**
     * Sets the start timestamp of a job. <br>
     * <b>CAUTION:</b> <br>
     * This method must not be called after the job has been started. If you
     * need to set the job start explicitly, set it before you call {@link #start()
     * }. if you don't set the job start explicitly, it is set to the timestamp
     * ob the job creation.
     *
     * @param jobStart job start
     */
    public void setStartTime(final LocalDateTime jobStart);

    /**
     * Sets a status for this {@link Job}. Also set the maxSeverityStatus if it
     * is not set or lower than the status
     * @deprecated Manually setting the job status is no longer supported; status is calculated based
     * on activities/events and the executing engine's process result.
     * @param status the status to set
     */
    @Deprecated
    public void setStatus(JobStatus status);

    /**
     * Starts the job, i.e., sets the according status, job start date if not
     * set before, and flags the job to begin flushing.
     */
    public void start();

    /**
     * Returns <code>true</code> if activities for a given activityModel require input or
     * output data, based on extract and tracepoint configuration.
     *
     * @param activityModel activityModel to check
     * @return <code>true</code> if input/output data is required.
     */
    public boolean needsData(ActivityModel activityModel);

    public void addPluginDataItem(IPluginDataItem pluginDataItem);

    /**
     * Returns whether this job has already been started.
     *
     * @return true, if the job has started already (RUNNING, SUCCESS, WARNING,
     * ERROR). false, if the job hasn't been started (CREATED)
     */
    public boolean hasStarted();

    /**
     * Provides access to the runtime activities of this job: the activity registry, lookups,
     * builders, and the start activity.
     *
     * @return the activities facet of this job, never <code>null</code>
     */
    public JobActivities activities();

    /**
     * Provides access to the attributes of this job. Attributes are wire data: they are
     * transmitted to the nJAMS server with the next log message.
     *
     * @return the attributes facet of this job, never <code>null</code>
     */
    public JobAttributes attributes();

    /**
     * Provides access to the descriptive metadata of this job: correlation/parent/external
     * log ids and the business fields. The facet's setters are chainable.
     *
     * @return the metadata facet of this job, never <code>null</code>
     */
    public JobMetadata metadata();

    /**
     * Provides access to the internal properties of this job. Properties are client-local
     * only and never transmitted to the nJAMS server.
     *
     * @return the properties facet of this job, never <code>null</code>
     */
    public JobProperties properties();

    /**
     * Provides access to the tracing flags of this job (deep trace, traces).
     *
     * @return the tracing facet of this job, never <code>null</code>
     */
    public JobTracing tracing();

}
