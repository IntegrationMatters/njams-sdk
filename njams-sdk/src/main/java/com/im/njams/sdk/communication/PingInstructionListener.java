package com.im.njams.sdk.communication;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.client.NjamsMetadata;
import com.im.njams.sdk.logmessage.NjamsFeatures;

import java.util.Map;
import java.util.stream.Collectors;

public class PingInstructionListener implements InstructionListener {


    private final NjamsMetadata instanceMetadata;
    private final NjamsFeatures njamsFeatures;

    public PingInstructionListener(NjamsMetadata instanceMetadata, NjamsFeatures njamsFeatures) {
        this.instanceMetadata = instanceMetadata;
        this.njamsFeatures = njamsFeatures;
    }

    /**
     * Implementation of the InstructionListener interface. Listens on
     * sendProjectMessage and Replay.
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
        params.put("clientPath", instanceMetadata.clientPath.toString());
        params.put("clientVersion", instanceMetadata.clientVersion);
        params.put("sdkVersion", instanceMetadata.sdkVersion);
        params.put("category", instanceMetadata.category);
        params.put("machine", instanceMetadata.machine);
        params.put("features", njamsFeatures.get().stream().collect(Collectors.joining(",")));
        return response;
    }
}
