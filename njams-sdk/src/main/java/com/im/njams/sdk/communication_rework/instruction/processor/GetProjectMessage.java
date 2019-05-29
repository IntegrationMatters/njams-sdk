package com.im.njams.sdk.communication_rework.instruction.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetProjectMessage extends InstructionProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(GetProjectMessage.class);

    public static final String SEND_PROJECTMESSAGE = Command.SEND_PROJECTMESSAGE.commandString();

    private Njams njams;

    public GetProjectMessage(Njams njams, String commandToProcess) {
        super(commandToProcess);
        this.njams = njams;
    }

    @Override
    public void processInstruction(Instruction instruction) {
        if (instruction.getRequest().getCommand().equals(Command.SEND_PROJECTMESSAGE.commandString())) {
            njams.flushResources();
            Response response = new Response();
            response.setResultCode(0);
            response.setResultMessage("Successfully send ProjectMessage via NjamsClient");
            instruction.setResponse(response);
            LOG.debug("Sent ProjectMessage requested via Instruction via Njams");
        }
    }
}
