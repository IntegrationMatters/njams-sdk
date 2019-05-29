package com.im.njams.sdk.communication_rework.instruction;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.communication_rework.instruction.processor.InstructionProcessor;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class InstructionProcessorController {

    private static final Logger LOG = LoggerFactory.getLogger(InstructionProcessorController.class);

    private InstructionDispatcher instructionDispatcher;

    public InstructionProcessorController(){
        this.instructionDispatcher = new InstructionDispatcher();
    }

    public void addInstructionProcessor(InstructionProcessor instructionProcessor) {
        this.instructionDispatcher.addInstructionProcessor(instructionProcessor);
    }

    public void removeInstructionProcessor(String instructionProcessorCommandName) {
        InstructionProcessor instructionProcessor = instructionDispatcher.getInstructionProcessor(instructionProcessorCommandName);
        if(instructionProcessor != null){
            instructionDispatcher.removeInstructionProcessor(instructionProcessor);
        }
    }

    public void processInstruction(Instruction instruction){

        //Todo: Create NotNullValidator instead
        if (instruction == null) {
            LOG.error("Instruction should not be null");
            return;
        }
        //log each instruction's request
        Request request = instruction.getRequest();
        logInstructionRequest(request);

        //dispatch instruction to correct InstructionProcessor
        instructionDispatcher.dispatchInstruction(instruction);

        //log each instruction's response
        logInstructionResponse(instruction.getResponse(), request.getCommand());
    }

    private void logInstructionRequest(Request request) {
        if(LOG.isDebugEnabled()){
            LOG.debug("Received valid request with command: {}", request.getCommand());
            if(LOG.isTraceEnabled()){
                String plugin = request.getPlugin();
                String dateTime = DateTimeUtility.toString(request.getDateTime());
                Map<String, String> parameters = request.getParameters();
                if(StringUtils.isNotBlank(plugin)){
                    LOG.trace("Plugin of the request: {}", plugin);
                }
                if(StringUtils.isNotBlank(dateTime)){
                    LOG.trace("Datetime of the request: {}", dateTime);
                }
                if(parameters != null && !parameters.isEmpty()){
                    LOG.trace(this.stringifyParameters(parameters));
                }
            }
        }
    }

    private String stringifyParameters(Map<String, String> parameters){
        StringBuilder parameterStringBuilder = new StringBuilder();
        //Start of the parameterList
        parameterStringBuilder.append("List of parameters: ").append("\n").append("{").append("\n");
        //Fill with the parameters
        parameters.forEach((parameter, value) -> parameterStringBuilder.append("\t").append(parameter).append(" : ").append(value).append("\n"));
        //End of the parameterList
        parameterStringBuilder.append("}");

        return parameterStringBuilder.toString();
    }

    private void logInstructionResponse(Response response, String commandName) {
        if(LOG.isDebugEnabled()){
            LOG.debug("Created response for {} with result code: {}", commandName, response.getResultCode());
            if(LOG.isTraceEnabled()){
                String resultMessage = response.getResultMessage();
                String dateTime = DateTimeUtility.toString(response.getDateTime());
                Map<String, String> parameters = response.getParameters();
                if(StringUtils.isNotBlank(resultMessage)){
                    LOG.trace("Result message of the response: {}", resultMessage);
                }
                if(StringUtils.isNotBlank(dateTime)){
                    LOG.trace("Datetime of the request: {}", dateTime);
                }
                if(parameters != null && !parameters.isEmpty()){
                    LOG.trace(this.stringifyParameters(parameters));
                }
            }
        }
    }
}