package com.im.njams.sdk.communication_rework.instruction.controller.logging;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;

public interface InstructionLogger {
    public void log(Instruction instruction);
}