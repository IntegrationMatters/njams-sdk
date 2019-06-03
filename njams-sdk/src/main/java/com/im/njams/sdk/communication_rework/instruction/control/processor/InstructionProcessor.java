package com.im.njams.sdk.communication_rework.instruction.control.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;

public abstract class InstructionProcessor {

    private String commandToProcess;

    public InstructionProcessor(String commandToProcess) {
        this.commandToProcess = commandToProcess;
    }

    public String getCommandToProcess() {
        return commandToProcess;
    }

    public abstract void processInstruction(Instruction instruction);
}
