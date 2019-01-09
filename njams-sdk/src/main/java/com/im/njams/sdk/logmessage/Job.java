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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

import com.faizsiegeln.njams.messageformat.v4.logmessage.interfaces.IPluginDataItem;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.SubProcessActivityModel;

/**
 * This represents an instance of a process/flow etc in engine to monitor.
 *
 * @author pnientiedt
 */
public interface Job {

    /**
     * Adds a new Activity to the Job.
     *
     * @param activity to add
     */
    public void addActivity(final Activity activity);

    /**
     * Creates ActivityBuilder with a given activityModeId.
     *
     * @param activityModelId to create
     * @return a builder
     */
    public ActivityBuilder createActivity(String activityModelId);

    /**
     * Creates ActivityBuilder with a given ActivityModel.
     *
     * @param activityModel to create
     * @return a builder
     */
    public ActivityBuilder createActivity(ActivityModel activityModel);

    /**
     * Creates GroupBuilder with a given groupModelId.
     *
     * @param groupModelId to create
     * @return a builder
     */
    public GroupBuilder createGroup(String groupModelId);

    /**
     * Creates GroupBuilder with a given GroupModel.
     *
     * @param groupModel to create
     * @return a builder
     */
    public GroupBuilder createGroup(GroupModel groupModel);

    /**
     * Creates SubProcessBuilder with a given SubProcessModel.
     *
     * @param groupModel to create
     * @return a builder
     */
    public SubProcessActivityBuilder createSubProcess(SubProcessActivityModel groupModel);

    /**
     * Set the logMessage status to success if the status is running. Then flush
     * it
     */
    public void end();

    /**
     * Return all Activities
     *
     * @return all Activities
     */
    public Collection<Activity> getActivities();

    /**
     * Returns a activity to a given instanceId.
     *
     * @param activityInstanceId to get
     * @return the {@link Activity}
     */
    public Activity getActivityByInstanceId(String activityInstanceId);

    /**
     * Returns the last added activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     */
    public Activity getActivityByModelId(String activityModelId);

    /**
     * Return the Attribute name to a given value
     *
     * @param name attribute name
     * @return attribute value
     */
    public String getAttribute(final String name);

    /**
     * Return all attributes for this job
     *
     * @return unmodifiable list of attributes
     */
    public Map<String, String> getAttributes();

    /**
     * Return the businessObject
     *
     * @return the businessObject
     */
    public String getBusinessObject();

    /**
     * Return the businessService
     *
     * @return the businessService
     */
    public String getBusinessService();

    /**
     * Returns the last added and completed activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     */
    public Activity getCompletedActivityByModelId(String activityModelId);

    /**
     * Returns the correlation log id of this job.
     *
     * @return collreation log id
     */
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
     */
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
     */
    public String getParentLogId();

    /**
     * Return the ProcessModel
     *
     * @return the ProcessModel
     */
    public ProcessModel getProcessModel();

    /**
     * Gets a properties value. Properties will not be send within project
     * messages.
     *
     * @param key name of the property
     * @return Properties value of <b>null</b>
     */
    public Object getProperty(final String key);

    /**
     * Returns the last added and running activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     */
    public Activity getRunningActivityByModelId(String activityModelId);

    /**
     * Return the start activity
     *
     * @return the start activity
     */
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
     */
    public boolean hasAttribute(final String name);

    /**
     * Checks whether the activity has a property with a given name.
     *
     * @param key name of the property
     * @return <b>true</b> if and only if a property with the given name exists.
     */
    public boolean hasProperty(final String key);

    /**
     * Indicates that trace information shall be collected for all activites of
     * this job (including sub processes).
     *
     * @return <b>true</b> if and only if deep trace is enabled.
     */
    public boolean isDeepTrace();

    /**
     * Marks that a job shall collect trace information for each activity
     * (including sub processes).
     *
     * @param deepTrace
     * <b>true</b> if deep trace shall be activiated.
     */
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
     */
    public boolean isTraces();

    /**
     * Removes the property with a given name
     *
     * @param key name of the property
     * @return Previous value of the property (if it existed) or else
     * <b>null</b>.
     */
    public Object removeProperty(final String key);

    /**
     * Adds an Attribute to the job
     *
     * @param name the name of the attribute
     * @param value the value of the attribute
     */
    public void addAttribute(final String name, final String value);

    /**
     * Set the businessObject as String
     *
     * @param businessObject businessObject to set
     */
    public void setBusinessObject(String businessObject);

    /**
     * Set the binsessObject as Path
     *
     * @param businessObject businessObject to set
     */
    public void setBusinessObject(Path businessObject);
    
    /**
     * Set the businessObject's start
     * 
     * @param businessStart startTime to set
     */
    public void setBusinessStart(LocalDateTime businessStart);
    
    /**
     * Set the businessObject's end
     * 
     * @param businessEnd endTime to set
     */
    public void setBusinessEnd(LocalDateTime businessEnd);

    /**
     * Set the businessService as String
     *
     * @param businessService businessService to set
     */
    public void setBusinessService(String businessService);

    /**
     * Set the businessService as Path
     *
     * @param businessService businessService to set
     */
    public void setBusinessService(Path businessService);

    /**
     * Sets the correlation log id of this job.
     *
     * @param correlationLogId collreation log id
     */
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
     */
    public void setExternalLogId(String externalLogId);

    /**
     * Set the parentLogId
     *
     * @param parentLogId parentLogId to set
     */
    public void setParentLogId(String parentLogId);

    /**
     * Sets a properties value. Properties will not be send within project
     * messages.
     *
     * @param key name of the property
     * @param value value of the property
     */
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
     *
     * @param status the status to set
     */
    public void setStatus(JobStatus status);

    /**
     * Starts the job, i.e., sets the according status, job start date if not
     * set before, and flags the job to begin flushing.
     */
    public void start();

    /**
     * Returns true if a Activity for a given activityModelId needs input or
     * output data, based on extracts and tracepoints
     *
     * @param activityModelId modelId of the activity
     * @return true if data is needed, or false if not data is needed
     */
    public boolean needsData(String activityModelId);

    public void addPluginDataItem(IPluginDataItem pluginDataItem);

}
