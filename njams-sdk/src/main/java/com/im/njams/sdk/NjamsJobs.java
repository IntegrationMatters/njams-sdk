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
package com.im.njams.sdk;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Owns the registry of currently running {@link Job}s of an {@link Njams} client.
 * Obtain via {@code njams.jobs()}. This facet is part of the runtime monitoring path.
 */
public class NjamsJobs {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsJobs.class);

    private final LifecycleState lifecycle;
    private final ConcurrentMap<String, Job> jobs = new ConcurrentHashMap<>();
    // logId of replayed job -> deep-trace flag from the replay request
    private final Map<String, Boolean> replayedLogIds = new HashMap<>();

    NjamsJobs(LifecycleState lifecycle) {
        this.lifecycle = lifecycle;
    }

    /**
     * Adds a job to the job list. If the client hasn't been started before, the
     * job can't be added to the list.
     *
     * @param job to add to this client's job list.
     */
    public void add(Job job) {
        lifecycle.requireStarted();
        synchronized (replayedLogIds) {
            final Boolean deepTrace = replayedLogIds.remove(job.getLogId());
            if (deepTrace != null) {
                ReplayHandler.markAsReplayed(job);
                if (deepTrace) {
                    job.setDeepTrace(true);
                }
            }
        }
        jobs.put(job.getJobId(), job);
    }

    /**
     * Removes a job from the job list.
     *
     * @param jobId of the Job to be removed
     */
    public void remove(String jobId) {
        jobs.remove(jobId);
        LOG.debug("Job {} removed", jobId);
    }

    /**
     * Returns the job instance for the given jobId.
     *
     * @param jobId the jobId to search for
     * @return the Job or null if not found
     */
    public Job get(String jobId) {
        return jobs.get(jobId);
    }

    /**
     * Returns a collection of all current jobs. This collection must not be
     * changed.
     *
     * @return Unmodifiable collection of jobs.
     */
    public Collection<Job> getAll() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    /**
     * SDK-197: Marks the job with the given logId as replayed, or remembers the logId so that
     * the job is marked when it is added later.
     *
     * @param logId the logId of the replayed job
     * @param deepTrace deep-trace flag from the replay request
     */
    void setReplayMarker(final String logId, boolean deepTrace) {
        if (StringUtils.isBlank(logId)) {
            return;
        }
        synchronized (replayedLogIds) {
            final Job job = getAll().stream().filter(j -> j.getLogId().equals(logId)).findAny().orElse(null);
            if (job != null) {
                // if the job is already known, set the marker
                ReplayHandler.markAsReplayed(job);
                if (deepTrace) {
                    job.setDeepTrace(true);
                }
            } else {
                // remember the log ID for when the job is added later -> consumed by add(...)
                replayedLogIds.put(logId, deepTrace);
            }
        }
    }
}
