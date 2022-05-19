/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication.instructionListener;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.njams.NjamsFeatures;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Listener for the PING instruction of the server.
 */
public class PingInstructionListener implements InstructionListener {

    private final NjamsMetadata instanceMetadata;
    private final NjamsFeatures njamsFeatures;

    public PingInstructionListener(NjamsMetadata instanceMetadata, NjamsFeatures njamsFeatures) {
        this.instanceMetadata = instanceMetadata;
        this.njamsFeatures = njamsFeatures;
    }

    /**
     * Implementation of the InstructionListener interface. Listens on the ping instruction
     *
     * @param instruction The instruction which should be handled
     */
    @Override
    public void onInstruction(Instruction instruction) {
        if (Command.PING.commandString().equalsIgnoreCase(instruction.getCommand())) {
            instruction.setResponse(createPingResponse());
        }
    }

    private Response createPingResponse() {
        final Response response = new Response();
        response.setResultCode(0);
        response.setResultMessage("Pong");
        final Map<String, String> params = response.getParameters();
        params.put("clientPath", instanceMetadata.getClientPath().toString());
        params.put("clientVersion", instanceMetadata.getClientVersion());
        params.put("sdkVersion", instanceMetadata.getSdkVersion());
        params.put("category", instanceMetadata.getCategory());
        params.put("machine", instanceMetadata.getMachine());
        params.put("features", njamsFeatures.get().stream().collect(Collectors.joining(",")));
        return response;
    }
}
