package com.im.njams.sdk.communication_rework.instruction.control.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.common.DateTimeUtility;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

public class TestInstructionBuilder {

    public static final String PROCESSPATH = "processPath";

    public static final String ACTIVITYID = "activityId";

    public static final String TESTPATH = ">test>";
    public static final String TESTACT = "act_1";

    private Instruction instruction;

    public TestInstructionBuilder prepareInstruction(Command command) {
        Instruction instruction = new Instruction();
        Request request = new Request();
        instruction.setRequest(request);
        request.setCommand(command.commandString());
        Map<String, String> parameters = new TreeMap<>();
        request.setParameters(parameters);
        this.instruction = instruction;
        return this;
    }

    public TestInstructionBuilder prepareGetLogLevelInstruction(){
        return prepareInstruction(Command.GET_LOG_LEVEL);
    }

    public TestInstructionBuilder addPath(String path) {
        return addParameter("processPath", path);
    }

    public TestInstructionBuilder addParameter(String name, Object value) {
        String s;
        if (value instanceof LocalDateTime) {
            s = DateTimeUtility.toString((LocalDateTime) value);
        } else {
            s = String.valueOf(value);
        }
        instruction.getRequest().getParameters().put(name, s);
        return this;
    }

    public Instruction build(){
        return instruction;
    }
}
