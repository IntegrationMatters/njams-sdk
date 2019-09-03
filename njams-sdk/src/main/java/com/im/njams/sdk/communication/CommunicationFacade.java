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

package com.im.njams.sdk.communication;

import com.im.njams.sdk.api.communication.Communication;
import com.im.njams.sdk.api.communication.instruction.InstructionListener;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * This class provides functionality for receiving instructions from the server.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public class CommunicationFacade implements Communication {

    private InstructionListener instructionListener;

    /**
     * Sets the {@link InstructionListener instructionListener} that is responsible for processing the incoming
     * instructions correctly.
     *
     * @param instructionListener the instructionListener to process instructions correctly.
     */
    @Override
    public void setInstructionListener(InstructionListener instructionListener) {
        this.instructionListener = instructionListener;
    }

    /**
     * Returns the {@link InstructionListener instructionListener} that is currently responsible for processing the
     * incoming instructions.
     *
     * @return the instructionListener that processes the incoming instructions.
     */
    @Override
    public InstructionListener getInstructionListener() {
        return instructionListener;
    }

    /**
     * Stops the processing of the incoming instructions.
     */
    @Override
    public void stop() {
        try {
            instructionListener.close();
        } catch (Exception stopDidntWork) {
            throw new NjamsSdkRuntimeException("Stopping the communication didn't work", stopDidntWork);
        }
    }
}
