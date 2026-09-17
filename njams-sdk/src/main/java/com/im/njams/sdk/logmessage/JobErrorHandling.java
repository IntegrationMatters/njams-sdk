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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.common.DateTimeUtility;

/**
 * Stores the last activity error of a job and commits it as an error event when the
 * job ends with a failure (or immediately, when logging all errors is enabled).
 */
final class JobErrorHandling {

    private static final Logger LOG = LoggerFactory.getLogger(JobErrorHandling.class);

    private final JobImpl jobImpl;
    private final JobSettings jobSettings;

    private final Object errorLock = new Object();
    private ActivityImpl errorActivity = null;
    private ErrorEvent errorEvent = null;

    JobErrorHandling(JobImpl jobImpl, JobSettings jobSettings) {
        this.jobImpl = jobImpl;
        this.jobSettings = jobSettings;
    }

    /**
     * Records that an error occurred for the given activity. Whether or not an according event is
     * generated depends on the log-all-errors setting, or the job's end status reported by the
     * executing engine.
     */
    void setActivityErrorEvent(Activity errorActivity, ErrorEvent errorEvent) {
        if (errorActivity != null && errorEvent != null) {
            if (jobSettings.allErrors) {
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
     * If the job has failed, there was an unhandled error that should have been recorded by
     * {@link #setActivityErrorEvent(Activity, ErrorEvent)}. If so, the error is now committed to
     * an according error event on this activity.
     */
    void commitActivityError() {
        if (jobSettings.allErrors) {
            // all errors have already been added to their activities.
            return;
        }
        synchronized (errorLock) {
            if (errorActivity != null) {
                LOG.debug("Committing error event to {}", errorActivity);
                updateActivityErrorEvent(errorActivity, errorEvent);
                if (jobImpl.getActivityByInstanceId(errorActivity.getInstanceId()) == null) {
                    // the activity is already sent, i.e., re-send
                    jobImpl.addActivity(errorActivity);
                }
                errorActivity = null;
                errorEvent = null;
            }
        }
    }

    /**
     * Update the event information on the given activity, based on the given error information.
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
}
