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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams.Feature;
import com.im.njams.sdk.communication.AbstractReplayHandler;
import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.communication.ReplayRequest;
import com.im.njams.sdk.communication.ReplayResponse;

/**
 * Owns the {@link ReplayHandler} of an {@link Njams} client and processes replay
 * instructions from the nJAMS server. Obtain via {@code njams.replay()}.
 */
public final class NjamsReplay {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsReplay.class);

    private final NjamsFeatures features;
    private final NjamsJobs jobs;
    private ReplayHandler replayHandler = null;

    NjamsReplay(NjamsFeatures features, NjamsJobs jobs) {
        this.features = features;
        this.jobs = jobs;
    }

    /**
     * Gets the current replay handler if present.
     *
     * @return Current replay handler if present or null otherwise.
     */
    public ReplayHandler getHandler() {
        return replayHandler;
    }

    /**
     * Sets a replay handler. Registering a handler announces the replay feature to the
     * nJAMS server in the project message at start.
     *
     * @param replayHandler Replay handler to be set.
     * @see AbstractReplayHandler
     */
    public void setHandler(final ReplayHandler replayHandler) {
        this.replayHandler = replayHandler;
        if (replayHandler == null) {
            features.remove(Feature.REPLAY);
        } else {
            features.add(Feature.REPLAY);
        }
    }

    /** Processes a replay instruction received from the nJAMS server. */
    void handleReplayRequest(Instruction instruction) {
        if (replayHandler != null) {
            try {
                final ReplayRequest replayRequest = new ReplayRequest(instruction);
                final ReplayResponse replayResponse = replayHandler.replay(replayRequest);
                replayResponse.addParametersToInstruction(instruction);
                if (!replayRequest.getTest()) {
                    jobs.setReplayMarker(replayResponse.getMainLogId(), replayRequest.getDeepTrace());
                    LOG.debug("Processed replay response {}", replayResponse.getMainLogId());
                }
            } catch (final Exception ex) {
                instruction.setResponseResultCode(2);
                instruction.setResponseResultMessage("Error while executing replay: " + ex.getMessage());
                instruction.setResponseParameter("Exception", String.valueOf(ex));
            }
        } else {
            instruction.setResponseResultCode(1);
            instruction.setResponseResultMessage("No replay handler registered.");
        }
    }
}
