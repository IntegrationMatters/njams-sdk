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

import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.InstructionProcessor;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.FallbackProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * This class provides a data structure for saving one-to-one mappings from {@String lower-case-commands} to
 * {@link InstructionProcessor instructionProcessors}. Furthermore it provides a default {@link InstructionProcessor
 * instructionProcessor} that can always be used for any Instruction to process.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public class InstructionProcessorCollection {

    private static final Logger LOG = LoggerFactory.getLogger(InstructionProcessorCollection.class);

    private Map<String, InstructionProcessor> instructionProcessors = new HashMap<>();

    private InstructionProcessor fallbackProcessor = new FallbackProcessor();

    /**
     * Returns the default {@link InstructionProcessor instructionProcessor} that can be used for every
     * {@link Instruction instruction}. Even if the data structure is {@link InstructionProcessorCollection#clear()
     * cleared}, it will return a {@link InstructionProcessor instructionProcessor}.
     *
     * @return A default InstructionProcessor that can be used to process any {@link Instruction instruction}.
     */
    public InstructionProcessor getDefault() {
        return fallbackProcessor;
    }

    /**
     * Puts a mapping of a not-null {@link String command} to a not-null {@link InstructionProcessor
     * instructionProcessor}. If there already is a mapping for the given command in lower case, it will be
     * overwritten by the new one. If either of the parameters is null, nothing happens.
     *
     * @param commandToListenTo               the key of the mapping
     * @param overwritingInstructionProcessor the value of the mapping
     */
    public void putIfNotNull(String commandToListenTo, InstructionProcessor overwritingInstructionProcessor) {
        if (commandToListenTo != null && overwritingInstructionProcessor != null) {
            instructionProcessors.put(toLowerCaseOrNull(commandToListenTo), overwritingInstructionProcessor);
        } else {
            logPutHasntWorked(commandToListenTo, overwritingInstructionProcessor);
        }
    }

    private String toLowerCaseOrNull(String command) {
        String lowerCaseCommand = null;
        if (command != null) {
            lowerCaseCommand = command.toLowerCase();
        }
        return lowerCaseCommand;
    }

    private void logPutHasntWorked(String commandToListenTo, InstructionProcessor overwritingInstructionProcessor) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("Can't put {} for {} command.", overwritingInstructionProcessor, commandToListenTo);
        }
    }

    /**
     * Returns the corresponding mapped {@link InstructionProcessor instructionProcessor} for the command in lower
     * case or null, if there is no mapping.
     *
     * @param commandOfInstruction the key for the mapping
     * @return the found InstructionProcessor if there is any, otherwise null
     */
    public InstructionProcessor get(String commandOfInstruction) {
        return instructionProcessors.get(toLowerCaseOrNull(commandOfInstruction));
    }

    /**
     * Removes the mapping for the given command in lower case. If there is no mapping, it does nothing and returns
     * null.
     *
     * @param commandOfInstruction the key for the mapping
     * @return the removed instructionProcessor if there is any, otherwise null
     */
    public InstructionProcessor remove(String commandOfInstruction) {
        return instructionProcessors.remove(toLowerCaseOrNull(commandOfInstruction));
    }

    /**
     * Removes all mappings for explicitly set {@link InstructionProcessor instructionProcessors} (Not the default one).
     */
    public void clear() {
        instructionProcessors.clear();
    }
}
