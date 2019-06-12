package com.im.njams.sdk.communication_rework.instruction.control.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;

import java.util.Map;
import java.util.TreeMap;

public class InstructionProcessorTestUtility {

    public static Instruction prepareInstruction(Command command) {
        Instruction instruction = new Instruction();
        Request request = new Request();
        instruction.setRequest(request);
        request.setCommand(command.commandString());
        Map<String, String> parameters = new TreeMap<>();
        request.setParameters(parameters);
        return instruction;
    }

    public static Instruction prepareGetLogLevelInstruction(){
        return prepareInstruction(Command.GET_LOG_LEVEL);
    }
}
