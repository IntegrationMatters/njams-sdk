package com.im.njams.sdk.communication_rework.instruction.control.processor;

public class AbstractInstructionProcessor {

    protected TestInstructionBuilder instructionBuilder;

    public AbstractInstructionProcessor(){
        instructionBuilder = new TestInstructionBuilder();
    }
}
