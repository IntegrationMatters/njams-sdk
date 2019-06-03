package com.im.njams.sdk.communication_rework.instruction.control.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FallbackProcessor extends InstructionProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackProcessor.class);

    public static final String FALLBACK = "Fallback";

    public FallbackProcessor(String commandToProcess) {
        super(commandToProcess);
    }

    @Override
    public void processInstruction(Instruction instruction) {
        String errorMessage;
        Request request = instruction.getRequest();
        if (request == null) {
            errorMessage = "Instruction should have a request";
        } else {
            String command = request.getCommand();
            if (command == null) {
                errorMessage = "Request should have a command";
            } else if (command.equals("")) {
                errorMessage = "Request should have a not empty command";
            } else {
                errorMessage = "Command is unknown: " + command;
            }
        }
        LOG.error(errorMessage);
        Response response = new Response();
        response.setResultCode(1);
        response.setResultMessage(errorMessage);
        instruction.setResponse(response);
    }
}
