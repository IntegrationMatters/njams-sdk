package com.im.njams.sdk.communication_rework;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication_rework.instruction.boundary.InstructionProcessorFacade;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;

import java.util.Properties;

public class CommunicationFacade {

    private InstructionProcessorFacade instructionProcessorController;

    public CommunicationFacade(Properties properties){
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
        instructionProcessorController = null;
    }
//
}
