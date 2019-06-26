package com.im.njams.sdk.communication_rework.instruction.boundary.logging;

public class InstructionLoggerFactory {

    private InstructionLogger requestLogger;

    private InstructionLogger responseLogger;

    public InstructionLogger getRequestLogger(){
        if(requestLogger == null){
            requestLogger = new RequestLogger();
        }
        return requestLogger;
    }

    public InstructionLogger getResponseLogger(){
        if(responseLogger == null){
            responseLogger = new ResponseLogger();
        }
        return responseLogger;
    }

    public void stop() {
        requestLogger = null;
        responseLogger = null;
    }
}
