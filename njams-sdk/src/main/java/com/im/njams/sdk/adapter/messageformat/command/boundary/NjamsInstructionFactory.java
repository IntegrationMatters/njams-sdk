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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.adapter.messageformat.command.boundary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.adapter.messageformat.command.control.NjamsInstructionWrapper;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.InstructionFactory;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.common.JsonSerializerFactory;

import java.io.IOException;

/**
 * This class creates {@link Instruction instructions} for (non-) serialized
 * {@link com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstructions}.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public class NjamsInstructionFactory implements InstructionFactory {

    private final ObjectMapper instructionParser = JsonSerializerFactory.getDefaultMapper();

    /**
     * This method creates a {@link Instruction instruction} for the
     * {@link com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction} as String.
     *
     * @param messageFormatInstructionAsJsonString the
     * {@link com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction} as Json String.
     * @return {@link Instruction Instruction} that wraps the
     * {@link com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction}.
     * @throws NjamsInstructionException the parameters is not a valid Json.
     */
    @Override
    public Instruction getInstructionOf(String messageFormatInstructionAsJsonString) throws NjamsInstructionException {
        try {
            com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction = null;
            if (messageFormatInstructionAsJsonString != null) {
                messageFormatInstruction = readJsonAsMessageFormatInstruction(messageFormatInstructionAsJsonString);
            }
            return getInstructionOf(messageFormatInstruction);
        } catch (IOException ex) {
            throw new NjamsInstructionException(ex.getMessage());
        }
    }

    private com.faizsiegeln.njams.messageformat.v4.command.Instruction readJsonAsMessageFormatInstruction(String messageFormatInstructionAsJsonString)
            throws IOException {
        return instructionParser.readValue(messageFormatInstructionAsJsonString,
                com.faizsiegeln.njams.messageformat.v4.command.Instruction.class);
    }

    /**
     * This method creates a {@link Instruction instruction} for the
     * {@link com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction}.
     *
     * @param messageFormatInstruction the {@link com.faizsiegeln.njams.messageformat.v4.command.Instruction
     * messageFormatInstruction} to wrap.
     * @return {@link Instruction Instruction} that wraps the
     * {@link com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction}.
     */
    public Instruction getInstructionOf(com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction) {
        NjamsInstructionWrapper njamsInstructionWrapper = new NjamsInstructionWrapper(messageFormatInstruction);
        return njamsInstructionWrapper.wrap();
    }
}
