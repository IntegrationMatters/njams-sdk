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

package com.im.njams.sdk.communication.instruction.control.processor;

import com.im.njams.sdk.api.adapter.messageformat.command.entity.Instruction;
import com.im.njams.sdk.api.communication.instruction.control.InstructionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractInstructionProcessor<T extends Instruction> implements InstructionProcessor<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractInstructionProcessor.class);

    protected static final boolean SUCCESS = true;

    protected static final boolean FAILURE = false;

    private T instruction;

    @Override
    public synchronized void processInstruction(T instruction) {

        setInstruction(instruction);

        if (prepareProcessing()) {
            process();
        }

        setInstructionResponse();

        logFinishedProcessing();
    }

    protected void setInstruction(T instruction){
        this.instruction = instruction;
    }

    protected boolean prepareProcessing(){
        return SUCCESS;
    }

    protected abstract void process();

    protected abstract void setInstructionResponse();

    protected void logFinishedProcessing() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Processed {} by {}", instruction.getRequestReader().getCommand(),
                    this.getClass().getSimpleName());
        }
    }

    public T getInstruction(){
        return instruction;
    }
}
