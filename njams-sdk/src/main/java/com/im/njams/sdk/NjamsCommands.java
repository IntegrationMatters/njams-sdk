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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams.Feature;
import com.im.njams.sdk.communication.InstructionListener;

/**
 * Owns the {@link InstructionListener} registry of an {@link Njams} client and handles
 * the SDK built-in server commands (send-project-message, ping, replay, get-request-handler).
 * Obtain via {@code njams.commands()}.
 */
public class NjamsCommands {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsCommands.class);

    private final NjamsModel model;
    private final NjamsReplay replay;
    private final NjamsMetadata metadata;
    private final NjamsFeatures features;
    private final List<InstructionListener> instructionListeners = new ArrayList<>();

    NjamsCommands(NjamsModel model, NjamsReplay replay, NjamsMetadata metadata, NjamsFeatures features) {
        this.model = model;
        this.replay = replay;
        this.metadata = metadata;
        this.features = features;
    }

    /**
     * Returns the registered instruction listeners.
     *
     * @return copy of the current listener list
     */
    public List<InstructionListener> list() {
        return new ArrayList<>(instructionListeners);
    }

    /**
     * Adds a new InstructionListener which will be called if a new Instruction
     * will be received.
     *
     * @param listener the new listener to be called
     */
    public void add(InstructionListener listener) {
        instructionListeners.add(listener);
    }

    /**
     * Removes an InstructionListener from the receiver dispatch.
     *
     * @param listener the listener to remove
     */
    public void remove(InstructionListener listener) {
        instructionListeners.remove(listener);
    }

    /** Removes all listeners; called by Njams.stop(). */
    void clear() {
        instructionListeners.clear();
    }

    /**
     * Handles the SDK built-in commands: sendProjectMessage, ping, replay and getRequestHandler.
     *
     * @param instruction The instruction which should be handled
     */
    void dispatch(Instruction instruction) {
        final Command command = Command.getFromInstruction(instruction);
        if (command == null) {
            LOG.error("Received unsupported command {}", instruction.getCommand());
            instruction.setResponseResultCode(1);
            instruction.setResponseResultMessage("Unsupported command: " + instruction.getCommand());
            return;
        }
        switch (command) {
        case SEND_PROJECTMESSAGE:
            LOG.debug("Send ProjectMessage requested by nJAMS server.");
            model.send();
            instruction.setResponseResultCode(0);
            instruction.setResponseResultMessage("ProjectMessage sent");
            break;
        case PING:
            instruction.setResponse(createPingResponse());
            break;
        case REPLAY:
            replay.handleReplayRequest(instruction);
            break;
        case GET_REQUEST_HANDLER:
            instruction.setResponseResultCode(0);
            instruction.setResponseParameter("clientId", metadata.getClientSessionId());
            break;
        default:
            // skip all others
            break;

        }
    }

    private Response createPingResponse() {
        final Response response = new Response();
        response.setResultCode(0);
        response.setResultMessage("Pong");
        final Map<String, String> params = response.getParameters();
        params.put("clientPath", metadata.getClientPath().toString());
        params.put("clientVersion", metadata.getClientVersion());
        params.put("clientId", metadata.getClientSessionId());
        params.put("sdkVersion", metadata.getSdkVersion());
        params.put("runtimeVersion", metadata.getRuntimeVersion());
        params.put("category", metadata.getCategory());
        params.put("machine", metadata.getMachine());
        params.put("features", features.list().stream().map(Feature::key).collect(Collectors.joining(",")));
        return response;
    }
}
