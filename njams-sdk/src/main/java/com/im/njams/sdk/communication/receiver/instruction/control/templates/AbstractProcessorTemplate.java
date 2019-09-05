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

package com.im.njams.sdk.communication.receiver.instruction.control.templates;

import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.InstructionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This abstract class provides a template for processing instructions in general.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public abstract class AbstractProcessorTemplate implements InstructionProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProcessorTemplate.class);

    private Instruction instruction;

    /**
     * First the {@link Instruction instruction} is saved for further processing. Secondly the instruction will be
     * processed ({@link AbstractProcessorTemplate#process()}) and lastly the processing will be logged
     * ({@link AbstractProcessorTemplate#logProcessing()}).
     *
     * @param instruction the instruction to process
     */
    @Override
    public final synchronized void processInstruction(Instruction instruction) {

        this.instruction = instruction;

        process();

        logProcessing();
    }

    /**
     * Processes the instruction and writes a response accordingly.
     */
    protected abstract void process();

    private void logProcessing() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Processed {} by {}", instruction.getRequestReader().getCommand(),
                    this.getClass().getSimpleName());
        }
    }

    /**
     * Returns the instruction to process by this InstructionProcessor.
     *
     * @return the instruction to process.
     */
    public Instruction getInstruction() {
        return instruction;
    }

    /**
     * Returns the instructions requestReader.
     *
     * @return the requestReader to the corresponding instruction.
     */
    public Instruction.RequestReader getRequestReader() {
        return getInstruction().getRequestReader();
    }

    /**
     * Returns the instructions responseWriter.
     *
     * @return the responseWriter to the corresponding instruction.
     */
    public Instruction.ResponseWriter getResponseWriter() {
        return getInstruction().getResponseWriter();
    }
}
