package com.im.njams.sdk.communication_rework.instruction;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.communication_rework.instruction.processor.InstructionProcessor;
import com.im.njams.sdk.communication_rework.instruction.processor.NullInstructionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class InstructionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(InstructionDispatcher.class);

    private List<InstructionProcessor> instructionProcessors;

    private NullInstructionProcessor fallbackInstructionProcessor;

    public InstructionDispatcher(){
        this.instructionProcessors = new ArrayList<>();
        this.fallbackInstructionProcessor = new NullInstructionProcessor("Null");
    }

    public void addInstructionProcessor(InstructionProcessor instructionProcessor){
        instructionProcessors.add(instructionProcessor);
    }

    public void removeInstructionProcessor(InstructionProcessor instructionProcessor){
        instructionProcessors.remove(instructionProcessor);
    }

    public void dispatchInstruction(Instruction instruction){
        String command = instruction.getRequest().getCommand();
        boolean foundValidInstructionProcessor = false;
        for(InstructionProcessor instructionProcessor : instructionProcessors){
            if(instructionProcessor.getCommandToProcess().equalsIgnoreCase(command)){
                instructionProcessor.processInstruction(instruction);
                foundValidInstructionProcessor = true;
            }
        }
        if(foundValidInstructionProcessor){
            Request request = instruction.getRequest();
            Response response = instruction.getResponse();
            LOG.debug("Handled command: {} (result={}) on process: {}{}", command, getResult(response.getResultCode()),
                    request.getParameters().get(InstructionSupport.PROCESS_PATH), getActivityExtension(request));
        }
        else{
            fallbackInstructionProcessor.processInstruction(instruction);
        }
    }

    private String getResult(int errorCode) {
        return errorCode == 1 ? "error" : "ok";
    }

    private String getActivityExtension(Request request){
        String actId = request.getParameters().get(InstructionSupport.ACTIVITY_ID);
        return actId == null ? "" : "#" + actId;
    }

    public InstructionProcessor getInstructionProcessor(String instructionProcessorCommandName) {
        for(InstructionProcessor instructionProcessor : instructionProcessors){
            if(instructionProcessor.getCommandToProcess().equals(instructionProcessorCommandName)){
                return instructionProcessor;
            }
        }
        return null;
    }
}
