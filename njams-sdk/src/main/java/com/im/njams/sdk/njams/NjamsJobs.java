package com.im.njams.sdk.njams;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.client.LogMessageFlushTask;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReplayHandler;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.communication.ReplayRequest;
import com.im.njams.sdk.communication.ReplayResponse;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class NjamsJobs implements InstructionListener {

    /**
     * Static value for feature replay
     *
     * @deprecated Use {@link NjamsFeatures.Feature#key()} on instance {@link NjamsFeatures.Feature#REPLAY} instead.
     */
    @Deprecated
    public static final String FEATURE_REPLAY = "replay";


    private final ConcurrentMap<String, Job> jobs = new ConcurrentHashMap<>();

    // logId of replayed job -> deep-trace flag from the replay request
    private final Map<String, Boolean> replayedLogIds = new HashMap<>();
    private final NjamsMetadata njamsMetadata;
    private final NjamsState njamsState;
    private final NjamsFeatures njamsFeatures;
    private final Settings settings;

    private ReplayHandler replayHandler = null;

    public NjamsJobs(NjamsMetadata njamsMetadata, NjamsState njamsState, NjamsFeatures njamsFeatures, Settings settings) {
        this.njamsMetadata = njamsMetadata;
        this.njamsState = njamsState;
        this.njamsFeatures = njamsFeatures;
        this.settings = settings;
    }

    /**
     * Remove a job from the joblist
     *
     * @param jobId of the Job to be removed
     */
    public void remove(String jobId) {
        jobs.remove(jobId);
    }

    /**
     * Adds a job to the joblist. If njams hasn't started before, it can't be
     * added to the list.
     *
     * @param job to add to the instances job list.
     */
    public void add(Job job) {
        if (njamsState.isStarted()) {
            synchronized (replayedLogIds) {
                final Boolean deepTrace = replayedLogIds.remove(job.getLogId());
                if (deepTrace != null) {
                    job.addAttribute(ReplayHandler.NJAMS_REPLAYED_ATTRIBUTE, "true");
                    if (deepTrace) {
                        job.setDeepTrace(true);
                    }
                }
            }
            jobs.put(job.getJobId(), job);
        } else {
            njamsState.handleStopBeforeStart();
        }
    }

    /**
     * Returns the job instance for given jobId.
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
    public Collection<Job> get() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    /**
     * SDK-197
     *
     * @param logId
     */
    private void setReplayMarker(final String logId, boolean deepTrace) {
        if (StringUtils.isBlank(logId)) {
            return;
        }
        final Job job = get().stream().filter(j -> j.getLogId().equals(logId)).findAny().orElse(null);
        if (job != null) {
            // if the job is already known, set the marker
            job.addAttribute(ReplayHandler.NJAMS_REPLAYED_ATTRIBUTE, "true");
            if (deepTrace) {
                job.setDeepTrace(true);
            }
        } else {
            // remember the log ID for when the job is added later -> consumed by addJob(...)
            synchronized (replayedLogIds) {
                replayedLogIds.put(logId, deepTrace);
            }
        }
    }

    @Override
    public void onInstruction(Instruction instruction) {
        if (Command.REPLAY.commandString().equalsIgnoreCase(instruction.getCommand())) {
            final Response response = new Response();
            if (replayHandler != null) {
                try {
                    final ReplayRequest replayRequest = new ReplayRequest(instruction);
                    final ReplayResponse replayResponse = replayHandler.replay(replayRequest);
                    replayResponse.addParametersToInstruction(instruction);
                    if (!replayRequest.getTest()) {
                        setReplayMarker(replayResponse.getMainLogId(), replayRequest.getDeepTrace());
                    }
                } catch (final Exception ex) {
                    response.setResultCode(2);
                    response.setResultMessage("Error while executing replay: " + ex.getMessage());
                    instruction.setResponse(response);
                    instruction.setResponseParameter("Exception", String.valueOf(ex));
                }
            } else {
                response.setResultCode(1);
                response.setResultMessage("Client cannot replay processes. No replay handler is present.");
                instruction.setResponse(response);
            }
        }
    }

    /**
     * Sets a replay handler.
     *
     * @param replayHandler Replay handler to be set.
     * @see AbstractReplayHandler
     */
    public void setReplayHandler(ReplayHandler replayHandler) {
        this.replayHandler = replayHandler;
        if (replayHandler == null) {
            njamsFeatures.remove(FEATURE_REPLAY);
        } else {
            njamsFeatures.add(FEATURE_REPLAY);
        }
    }

    /**
     * Gets the current replay handler if present.
     *
     * @return Current replay handler if present or null otherwise.
     */
    public ReplayHandler getReplayHandler() {
        return replayHandler;
    }

    public void start() {
        LogMessageFlushTask.start(njamsMetadata, this, settings);
    }

    public void stop() {
        LogMessageFlushTask.stop(njamsMetadata);
    }
}
