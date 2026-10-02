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
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.utils.StringUtils;

import java.time.LocalDateTime;

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
 * <p>
 * <b>Send cadence is SDK-controlled.</b> nJAMS server, respectively Elasticsearch, is not very good at
 * handling high-frequency updates to the same job (identified by its {@code logId}), though it handles
 * high-frequency messages across different jobs without issue. For this reason the SDK — not the
 * caller — decides when a job's log message is actually sent: typically once, at
 * {@link #end(boolean)}, and only more often when the job's accumulated data exceeds the configured
 * flush size or age threshold. There is no supported way to force an additional send from outside the
 * SDK; do not attempt to obtain a sender/transport instance and send messages directly.
 *
 * @author pnientiedt
 */
public interface Job {

    /**
     * Prefix for internal Job attributes that are by default hidden in nJAMS UI.
     */
    public static final String INTERNAL_ATTRIBUTES_PREFIX = "$njams";

    /**
     * Builds a hidden attribute name for the given plain name by prefixing with {@link #INTERNAL_ATTRIBUTES_PREFIX}
     * @param plainName The actual name.
     * @return The given name prefixed with {@link #INTERNAL_ATTRIBUTES_PREFIX}
     */
    public static String hiddenAttributeName(String plainName) {
        if (StringUtils.isBlank(plainName)) {
            throw new IllegalArgumentException("Attribute key must not be null or empty");
        }
        if (plainName.startsWith(INTERNAL_ATTRIBUTES_PREFIX)) {
            return plainName;
        }
        return INTERNAL_ATTRIBUTES_PREFIX + "_" + plainName;
    }






    /**
     * Discards this job: removes it from the SDK's active-job registry <b>without</b> sending any log message to the
     * nJAMS server.
     * <p>
     * Use this to release an abandoned job whose final outcome will never arrive (e.g. a tracking
     * session evicted on an idle timeout), so that the SDK frees the memory it holds for the job
     * instead of keeping it alive for periodic flushing and the final flush on
     * {@link Njams#stop()}. Any data recorded so far is dropped and is never reported.
     * <p>
     * After {@code discard()} the job is treated as finished: {@link #isFinished()} returns
     * {@code true}, and further operations on it behave as they would on an ended job. Discarding a
     * job that has already ended or already been discarded is a no-op. Discarding an unfinished job
     * logs a warning, because data recorded for the job is not reported to the server.
     *
     * @see #end(boolean)
     */
    public void discard();

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
     * Return the endTime
     *
     * @return the endTime
     */
    public LocalDateTime getEndTime();


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
     * Indicates whether the job is already finished or not.
     *
     * @return <b>true</b> if and only if the job is already finished, else
     * <b>false</b>
     */
    public boolean isFinished();











    /**
     * Sets the end timestamp of a job.
     *
     * @param jobEnd job end
     */
    public void setEndTime(final LocalDateTime jobEnd);




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
