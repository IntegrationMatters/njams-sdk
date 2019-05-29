package com.im.njams.sdk.communication_rework.instruction.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NullInstructionProcessor extends InstructionProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(NullInstructionProcessor.class);

    public NullInstructionProcessor(String commandToProcess) {
        super(commandToProcess);
    }

    @Override
    public void processInstruction(Instruction instruction) {
        LOG.warn("Unknown command: {}", instruction.getRequest().getCommand());
    }
}
