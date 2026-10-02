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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.SubProcessActivityModel;

/**
 * Owns the runtime activities of a {@link Job}: the activity registry, lookups, and the
 * start activity. Obtain via {@code job.activities()}. This facet is part of the runtime
 * monitoring path.
 */
public class JobActivities {

    private static final Logger LOG = LoggerFactory.getLogger(JobActivities.class);

    private final JobImpl jobImpl;
    private final Object lock;

    // instanceId -> activity; all access must be guarded by the shared activities lock
    private final Map<String, Activity> activities = new LinkedHashMap<>();

    // modelId -> the activity for that model that was registered last, so a model lookup does not scan
    // the whole registry. Same lock as above.
    private final Map<String, Activity> lastByModelId = new HashMap<>();

    /*
     * activity sequence counter
     */
    private final AtomicInteger sequenceCounter = new AtomicInteger();

    private Activity startActivity;

    // IDs of activities that have been flushed but are not complete yet; for checking timer-flush
    private final Set<String> flushedActivities = ConcurrentHashMap.newKeySet();

    JobActivities(JobImpl jobImpl, Object lock) {
        this.jobImpl = jobImpl;
        this.lock = lock;
    }

    /**
     * Adds a new Activity to the Job. Activities may be added BEFORE the job is started — they are
     * sent with the first log message after the job starts. If the activity is a start activity, but
     * not the only one in this job, a NjamsSdkRuntimeException will be thrown.
     *
     * @param activity to add to this job.
     */
    public void add(final Activity activity) {
        add(activity, false);
    }

    /**
     * Creates an ActivityBuilder for the given ActivityModel. The built activity may be added
     * before the job is started.
     *
     * @param activityModel to create an activity for
     * @return a builder
     */
    public ActivityBuilder create(ActivityModel activityModel) {
        if (activityModel instanceof GroupModel) {
            return createGroup((GroupModel) activityModel);
        }
        if (activityModel instanceof SubProcessActivityModel) {
            return createSubProcess((SubProcessActivityModel) activityModel);
        }
        final ActivityBuilder builder = new ActivityBuilder(jobImpl, activityModel);
        builder.relaxStartedRequirement();
        return builder;
    }

    /**
     * Creates a GroupBuilder for the given GroupModel. The built group may be added before the
     * job is started.
     *
     * @param groupModel to create a group for
     * @return a builder
     */
    public GroupBuilder createGroup(GroupModel groupModel) {
        final GroupBuilder builder = new GroupBuilder(jobImpl, groupModel);
        builder.relaxStartedRequirement();
        return builder;
    }

    /**
     * Creates a SubProcessActivityBuilder for the given SubProcessActivityModel. The built activity
     * may be added before the job is started.
     *
     * @param subProcessModel to create a sub-process activity for
     * @return a builder
     */
    public SubProcessActivityBuilder createSubProcess(SubProcessActivityModel subProcessModel) {
        final SubProcessActivityBuilder builder = new SubProcessActivityBuilder(jobImpl, subProcessModel);
        builder.relaxStartedRequirement();
        return builder;
    }

    /**
     * Adds the given activity to the registry.
     *
     * @param requireStarted whether adding fails with an exception while the job has not been started
     */
    void add(final Activity activity, final boolean requireStarted) {
        final JobImpl owner = jobImpl;
        synchronized (lock) {
            if (requireStarted && !owner.hasStarted()) {
                throw new NjamsSdkRuntimeException(
                        "The method start() must be called before activities can be added to the job!");
            }
            final Activity previous = activities.put(activity.getInstanceId(), activity);
            if (previous != null && previous != activity) {
                // An instance id was re-used by a different activity object: the new one takes the
                // old one's place in the registry, which the per-model lookup has to follow.
                rebuildLastByModelId();
            }
            if (previous == null) {
                // Count the per-activity base size in the running estimate as soon as the activity
                // is added, so flush-by-size reflects activity-heavy jobs between flushes. Content
                // (payload, stack trace, etc.) is already counted by the activity's own setters, so
                // only the fixed base is added here to avoid double counting. Reused activities
                // (loop iterations) re-enter add with previous != null and must not re-add it.
                owner.addToEstimatedSize(ActivityImpl.BASE_ESTIMATED_SIZE);
                lastByModelId.put(activity.getModelId(), activity);
            }
            if (activity.isStarter()) {
                // the flag lives on JobImpl: frozen tests access the field directly
                if (owner.hasOrHadStartActivity) {
                    throw new NjamsSdkRuntimeException("A job must not have more than one start activity "
                            + owner.getJobId());
                }
                startActivity = activity;
                owner.hasOrHadStartActivity = true;
            }
        }
    }

    /**
     * Returns an activity for a given instanceId.
     *
     * @param activityInstanceId to get
     * @return the {@link Activity}
     */
    public Activity getByInstanceId(String activityInstanceId) {
        synchronized (lock) {
            return activities.get(activityInstanceId);
        }
    }

    /**
     * Returns the last added activity to a given modelId.
     *
     * @param activityModelId to get
     * @return the {@link Activity}
     */
    public Activity getByModelId(String activityModelId) {
        synchronized (lock) {
            return lastByModelId.get(activityModelId);
        }
    }

    /**
     * Rebuilds the per-model lookup from the registry's insertion order. Used after activities have
     * been removed or replaced, where repairing the affected entries individually is not worth the
     * complexity. Callers must hold the activities lock.
     */
    private void rebuildLastByModelId() {
        lastByModelId.clear();
        for (Activity activity : activities.values()) {
            lastByModelId.put(activity.getModelId(), activity);
        }
    }

    /**
     * Return the start activity, might return null if the startActivity hasn't been set or if it has
     * already been flushed.
     *
     * @return the start activity or null
     */
    public Activity getStart() {
        return startActivity;
    }

    /**
     * Returns a detached copy of all current activities of this job.
     *
     * @return Copy of the current activities.
     */
    public Collection<Activity> getAll() {
        synchronized (lock) {
            return new ArrayList<>(activities.values());
        }
    }

    /**
     * Returns the next Sequence for the next executed Activity.
     */
    long getNextSequence() {
        return sequenceCounter.incrementAndGet();
    }

    /** Live view of the activity registry; callers must hold the activities lock. */
    Collection<Activity> internalValues() {
        return activities.values();
    }

    /** Whether any activity must be sent with the next flush; mirrors the flush decision. */
    boolean hasActivityToSend() {
        synchronized (lock) {
            // Mirror the per-activity decision made when assembling the message: an activity must be
            // sent if it has never been flushed, or if it was already streamed while running and has
            // since completed. Checking only "never flushed" here would leave a completed activity of
            // an otherwise-idle running job unsent until the next flush triggered by another change.
            return activities.values().stream().anyMatch(this::shouldFlush);
        }
    }

    /** Whether the given activity must be contained in the next log message. */
    boolean shouldFlush(Activity activity) {
        if (!flushedActivities.contains(activity.getInstanceId())) {
            return true;
        }
        return activity.getActivityStatus() != ActivityStatus.RUNNING;
    }

    /**
     * Removes all not running activities from the activities map. If
     * the activity has a parent, remove the activity from the childActivity map
     * of the parent.
     */
    void removeNotRunning() {
        int loggingSum = 0;
        synchronized (lock) {
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
            if (loggingSum > 0) {
                rebuildLastByModelId();
            }
            LOG.trace("{} activities have been removed from {}. Still running: {}", loggingSum, jobImpl.getLogId(),
                    activities.size());
        }
    }
}
