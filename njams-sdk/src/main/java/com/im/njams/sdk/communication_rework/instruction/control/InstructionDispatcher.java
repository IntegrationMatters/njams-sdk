/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication_rework.instruction.control;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication_rework.instruction.control.processor.FallbackProcessor;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class InstructionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(InstructionDispatcher.class);

    private Map<String, InstructionProcessor> instructionProcessors;

    private InstructionProcessor fallbackProcessor;

    public InstructionDispatcher() {
        this.instructionProcessors = new HashMap<>();
        this.fallbackProcessor = new FallbackProcessor();
    }

    public void addInstructionProcessorForDistinctCommand(InstructionProcessor instructionProcessor) {
        instructionProcessors.put(instructionProcessor.getCommandToProcess().toLowerCase(), instructionProcessor);
    }

    public InstructionProcessor getInstructionProcessorForDistinctCommand(String instructionProcessorCommandName) {
        return instructionProcessors.get(instructionProcessorCommandName.toLowerCase());
    }

    public boolean containsInstructionProcessorForDistinctCommand(String instructionProcessorCommandName){
        return instructionProcessors.containsKey(instructionProcessorCommandName.toLowerCase());
    }

    public void removeInstructionProcessorForDistinctCommand(InstructionProcessor instructionProcessor) {
        instructionProcessors.remove(instructionProcessor.getCommandToProcess().toLowerCase(), instructionProcessor);
    }

    public void removeAllInstructionProcessors() {
        instructionProcessors.clear();
    }

    public void dispatchInstruction(Instruction instruction) {
        InstructionProcessor executingProcessor = fallbackProcessor;
        String commandToProcess = "";
        if(isInstructionValid(instruction)){
            commandToProcess = instruction.getRequest().getCommand();
            if(containsInstructionProcessorForDistinctCommand(commandToProcess)){
                executingProcessor = getInstructionProcessorForDistinctCommand(commandToProcess);
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Dispatching instruction with command {} to {}", commandToProcess, executingProcessor.getClass().getSimpleName());
        }
        executingProcessor.processInstruction(instruction);
    }

    private boolean isInstructionValid(Instruction instruction){
        return instruction != null && instruction.getRequest() != null;
    }

    protected void setFallbackProcessor(InstructionProcessor fallbackProcessor){
        this.fallbackProcessor = fallbackProcessor;
    }

    protected InstructionProcessor getFallbackProcessor(){
        return fallbackProcessor;
    }

    protected Map<String, InstructionProcessor> getAllInstructionProcessorsExceptFallbackProcessor(){
        return instructionProcessors;
    }
}
