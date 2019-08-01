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

package com.im.njams.sdk.communication.receiver;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.adapter.messageformat.command.control.NjamsInstructionWrapper;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.api.communication.instruction.InstructionListener;
import com.im.njams.sdk.communication.instruction.boundary.InstructionProcessorService;
import com.im.njams.sdk.communication.instruction.control.InstructionProcessor;

public class NjamsReceiver implements InstructionListener {

    private InstructionProcessorService instructionProcessorService;

    public NjamsReceiver() {
        this.instructionProcessorService = new InstructionProcessorService();
    }

    @Override
    public void onInstruction(Instruction instruction) {
        try {
            instructionProcessorService.processInstruction(new NjamsInstructionWrapper(instruction).wrap());
        } catch (NjamsInstructionException e) {
            //Do nothing
        }
    }

    public void putInstructionProcessor(String commandToListenTo, InstructionProcessor instructionProcessor) {
        instructionProcessorService.putInstructionProcessor(commandToListenTo, instructionProcessor);
    }

    public InstructionProcessor getInstructionProcessor(String commandToListenTo) {
        return instructionProcessorService.getInstructionProcessor(commandToListenTo);
    }

    public void removeInstructionProcessor(String commandToListenTo) {
        instructionProcessorService.removeInstructionProcessor(commandToListenTo);
    }

    @Override
    public void close(){
        instructionProcessorService.stop();
    }
}
