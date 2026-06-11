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

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.utils.StringUtils;

/**
 * The activity/event truncation state machine of a job. All methods must be called while
 * holding the job's activities lock — exactly as the inlined predecessor code did.
 */
final class JobTruncation {

    private static final Logger LOG = LoggerFactory.getLogger(JobTruncation.class);

    private final JobImpl jobImpl;
    private final JobSettings jobSettings;

    private boolean isTruncatingActivities = false;
    private boolean isTruncatingEvents = false;
    // activity-instance-ID --> hasEvent(activity)
    private final Map<String, Boolean> activityIds = new HashMap<>();

    JobTruncation(JobImpl jobImpl, JobSettings jobSettings) {
        this.jobImpl = jobImpl;
        this.jobSettings = jobSettings;
    }

    /**
     * Checks truncating limit and indicates whether or not the given activity shall be added to the next log message.
     *
     * @param activity        The activity to test
     * @param finishedSuccess Whether this job has yet finished successfully.
     * @return <code>true</code> if the given activity shall be added, <code>false</code> if not.
     */
    boolean checkTruncating(final Activity activity, boolean finishedSuccess) {
        if (!jobSettings.truncateOnSuccess && jobSettings.truncateLimit >= Integer.MAX_VALUE) {
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
            if (jobSettings.truncateOnSuccess && finishedSuccess || activityIds.size() > jobSettings.truncateLimit) {
                isTruncatingActivities = true;
                activityIds.values().removeIf(b -> !b);
                LOG.debug("Start truncating activities for {}", jobImpl);
            }
        }
        if (isTruncatingActivities && hasEvent && !isTruncatingEvents) {
            activityIds.put(activity.getInstanceId(), true);
            // check limit reached again
            if (activityIds.size() > jobSettings.truncateLimit) {
                isTruncatingEvents = true;
                activityIds.clear();
                LOG.debug("Start truncating events for {}", jobImpl);
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

    static boolean hasEvent(final Activity activity) {
        return activity.getEventStatus() != null || StringUtils.isNotBlank(activity.getEventCode())
                || StringUtils.isNotBlank(activity.getEventMessage())
                || StringUtils.isNotBlank(activity.getEventPayload())
                || StringUtils.isNotBlank(activity.getStackTrace());
    }
}
