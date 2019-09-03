/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.receiver.instruction.entity;

import com.im.njams.sdk.communication.receiver.instruction.control.InstructionProcessor;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.FallbackProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Todo: Write Doc
 */
public class InstructionProcessorCollection {

    private static final Logger LOG = LoggerFactory.getLogger(InstructionProcessorCollection.class);

    protected Map<String, InstructionProcessor> instructionProcessors = new HashMap<>();

    private InstructionProcessor fallbackProcessor = new FallbackProcessor();

    public InstructionProcessor getDefault() {
        return fallbackProcessor;
    }

    public void setDefaultIfNotNull(InstructionProcessor defaultProcessor) {
        if (defaultProcessor != null) {
            setDefault(defaultProcessor);
        } else {
            logDefaultProcessorHasntChanged();
        }
    }

    private void setDefault(InstructionProcessor newInstructionProcessor) {
        this.fallbackProcessor = newInstructionProcessor;
    }

    private void logDefaultProcessorHasntChanged() {
        if (LOG.isWarnEnabled()) {
            LOG.warn("Can't disable the default processors by setting it to null. The {} is still the fallback " +
                     "processors.", fallbackProcessor.getClass().getSimpleName());
        }
    }

    public void putIfNotNull(String commandToListenTo, InstructionProcessor overwritingInstructionProcessor) {
        if (commandToListenTo != null && overwritingInstructionProcessor != null) {
            put(commandToListenTo, overwritingInstructionProcessor);
        } else {
            logPutHasntWorked(commandToListenTo, overwritingInstructionProcessor);
        }
    }

    private void logPutHasntWorked(String commandToListenTo, InstructionProcessor overwritingInstructionProcessor) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("Can't put {} for {} command.", overwritingInstructionProcessor, commandToListenTo);
        }
    }

    private void put(String commandToListenTo, InstructionProcessor overwritingInstructionProcessor) {
        instructionProcessors.put(commandToListenTo, overwritingInstructionProcessor);
    }

    public InstructionProcessor get(String commandOfInstruction) {
        return instructionProcessors.get(commandOfInstruction);
    }

    public InstructionProcessor remove(String commandOfInstruction) {
        return instructionProcessors.remove(commandOfInstruction);
    }

    public void clear() {
        instructionProcessors.clear();
    }
}
