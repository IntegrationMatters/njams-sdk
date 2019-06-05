package com.im.njams.sdk.communication_rework.instruction.control;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;
import com.im.njams.sdk.communication_rework.instruction.control.processor.FallbackProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class InstructionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(InstructionDispatcher.class);

    private List<InstructionProcessor> instructionProcessors;

    private FallbackProcessor fallbackProcessor;

    public InstructionDispatcher() {
        this.instructionProcessors = new ArrayList<>();
        this.fallbackProcessor = new FallbackProcessor(FallbackProcessor.FALLBACK);
    }

    public void addInstructionProcessor(InstructionProcessor instructionProcessor) {
        instructionProcessors.add(instructionProcessor);
    }

    public void removeInstructionProcessor(InstructionProcessor instructionProcessor) {
        instructionProcessors.remove(instructionProcessor);
    }

    public void dispatchInstruction(Instruction instruction) {
        InstructionProcessor executingProcessor = fallbackProcessor;
        String commandToProcess = "";
        if(isInstructionValid(instruction)){
            commandToProcess = instruction.getRequest().getCommand();
            for (InstructionProcessor instructionProcessor : instructionProcessors) {
                if (instructionProcessor.getCommandToProcess().equalsIgnoreCase(commandToProcess)) {
                    executingProcessor = instructionProcessor;
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Dispatching instruction with command {} to {}", commandToProcess, executingProcessor.getClass().getSimpleName());
        }
        executingProcessor.processInstruction(instruction);
    }

    private boolean isInstructionValid(Instruction instruction){
        return instruction.getRequest() != null;
    }

    public InstructionProcessor getInstructionProcessor(String instructionProcessorCommandName) {
        for (InstructionProcessor instructionProcessor : instructionProcessors) {
            if (instructionProcessor.getCommandToProcess().equals(instructionProcessorCommandName)) {
                return instructionProcessor;
            }
        }
        return null;
    }

    public void stop() {
        instructionProcessors.clear();
        instructionProcessors = null;
        fallbackProcessor = null;
    }
}
