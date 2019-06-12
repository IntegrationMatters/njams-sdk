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
