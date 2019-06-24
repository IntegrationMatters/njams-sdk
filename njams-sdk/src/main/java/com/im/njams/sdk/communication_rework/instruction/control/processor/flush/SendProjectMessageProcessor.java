package com.im.njams.sdk.communication_rework.instruction.control.processor.flush;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendProjectMessageProcessor extends InstructionProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SendProjectMessageProcessor.class);

    protected static final String SUCCESS_RESULT_MESSAGE = "Successfully send ProjectMessage via NjamsClient";

    public static final String SEND_PROJECTMESSAGE = Command.SEND_PROJECTMESSAGE.commandString();

    private Njams njams;

    public SendProjectMessageProcessor(Njams njams) {
        super(SEND_PROJECTMESSAGE);
        this.njams = njams;
    }

    @Override
    public void processInstruction(Instruction instruction) {
        njams.flushResources();
        Response response = new Response();
        response.setResultCode(0);
        response.setResultMessage(SUCCESS_RESULT_MESSAGE);
        instruction.setResponse(response);
        LOG.debug("Sent ProjectMessage requested via Instruction via Njams");
    }
}
