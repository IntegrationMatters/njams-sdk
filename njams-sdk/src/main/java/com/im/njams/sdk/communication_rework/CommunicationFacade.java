package com.im.njams.sdk.communication_rework;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication_rework.instruction.InstructionProcessorController;
import com.im.njams.sdk.communication_rework.instruction.processor.InstructionProcessor;

import java.util.Properties;

public class CommunicationFacade {

    private InstructionProcessorController instructionProcessorController;

    public CommunicationFacade(Properties properties){
        this.instructionProcessorController = new InstructionProcessorController();
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
//    public void stop(){
//
//    }
//
}
