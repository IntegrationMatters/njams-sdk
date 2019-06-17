package com.im.njams.sdk.communication_rework.instruction.control.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ExtractRule;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.RuleType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.utils.JsonUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

public class TestInstructionBuilder {

    public static final String PROCESSPATH_KEY = "processPath";

    public static final String ACTIVITYID_KEY = "activityId";

    public static final String EXTRACT_KEY = "extract";

    public static final String PROCESSPATH_VALUE = ">test>";
    public static final String ACTIVITYID_VALUE= "act_1";

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

    public TestInstructionBuilder prepareGetLogLevelInstruction() {
        return prepareInstruction(Command.GET_LOG_LEVEL);
    }

    public TestInstructionBuilder addDefaultPath(){
        return addPath(PROCESSPATH_VALUE);
    }

    public TestInstructionBuilder addPath(String path) {
        return addParameter(PROCESSPATH_KEY, path);
    }

    public TestInstructionBuilder addDefaultActivityId(){
        return addActivityId(ACTIVITYID_VALUE);
    }
    public TestInstructionBuilder addActivityId(String id) {
        return addParameter(ACTIVITYID_KEY, id);
    }

    public TestInstructionBuilder addDefaultExtract() throws JsonProcessingException {
        return addExtract("ex_1", RuleType.VALUE, "Hello", "IN");
    }

    public TestInstructionBuilder addExtract(String name, RuleType type, String rule, String inout) throws JsonProcessingException {
        Extract ex = new Extract();
        ex.setName(name);
        ExtractRule extractRule = new ExtractRule();
        extractRule.setRuleType(type);
        extractRule.setRule(rule);
        extractRule.setInout(inout);
        ex.getExtractRules().add(extractRule);
        return addParameter(EXTRACT_KEY, JsonUtils.serialize(ex));
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

    public Instruction build() {
        return instruction;
    }
}
