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
package com.im.njams.sdk.communication_rework;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication_rework.instruction.boundary.InstructionProcessorFacade;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;
import com.im.njams.sdk.communication_rework.instruction.control.processor.replay.ReplayHandler;

public class CommunicationFacade {

    private InstructionProcessorFacade instructionProcessorController;

    public CommunicationFacade(){
        this.instructionProcessorController = new InstructionProcessorFacade();
    }

    public void addInstructionProcessor(InstructionProcessor instructionProcessor){
        this.instructionProcessorController.addInstructionProcessor(instructionProcessor);
    }

    public void removeInstructionProcessor(String instructionProcessorCommandName){
        this.instructionProcessorController.removeInstructionProcessor(instructionProcessorCommandName);
    }

    public void processInstruction(Instruction instruction){
        this.instructionProcessorController.processInstruction(instruction);
    }

    public void setReplayHandlerToReplayProcessor(ReplayHandler replayHandler) {
        this.instructionProcessorController.setReplayHandlerToReplayProcessor(replayHandler);
    }

    public ReplayHandler getReplayHandlerFromReplayProcessor() {
        return this.instructionProcessorController.getReplayHandlerFromReplayProcessor();
    }
//
//
//
//    public void start(){
//
//    }
//
//    public void send(CommonMessage message){
//
//    }
//
    public void stop(){
        instructionProcessorController.stop();
    }
//
}
