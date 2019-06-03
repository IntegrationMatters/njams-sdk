package com.im.njams.sdk.communication_rework.instruction.control.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication_rework.instruction.control.InstructionSupport;
import com.im.njams.sdk.communication_rework.instruction.entity.Configuration;

public abstract class ConfigurationProcessor extends InstructionProcessor {

    protected InstructionSupport instructionSupport;

    protected Configuration configuration;

    public ConfigurationProcessor(Configuration configuration, String commandToProcess) {
        super(commandToProcess);
        this.configuration = configuration;
        this.instructionSupport = new InstructionSupport();
    }

    @Override
    public final void processInstruction(Instruction instruction) {
        instructionSupport.setInstruction(instruction);
        this.processInstruction(instructionSupport);
    }

    protected void saveConfiguration(InstructionSupport instructionSupport) {
        try {
            configuration.save();
        } catch (final Exception e) {
            instructionSupport.error("Unable to save configuration", e);
        }
    }

    protected abstract void processInstruction(InstructionSupport instructionSupport);
}
