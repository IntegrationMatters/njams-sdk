package com.im.njams.sdk.communication_rework.instruction.boundary.logging;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.serializer.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseLogger implements InstructionLogger {

    private static final Logger LOG = LoggerFactory.getLogger(ResponseLogger.class);

    private JsonSerializer<Response> responseSerializer = new JsonSerializer<>();

    @Override
    public void log(Instruction instruction) {
        Request processedRequest = instruction.getRequest();
        Response responseToLog = instruction.getResponse();
        final int responseResultCode = responseToLog.getResultCode();
        if (processedRequest != null) {
            logResponseForProcessedRequest(processedRequest, responseToLog, responseResultCode);
        } else {
            logResponseForInvalidInstruction(responseToLog, responseResultCode);
        }
    }

    private void logResponseForProcessedRequest(Request processedRequest, Response responseToLog, int responseResultCode) {
        String commandOfRequest = processedRequest.getCommand();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created response for command {} with result code: {}", commandOfRequest, responseResultCode);
        }
        if (LOG.isTraceEnabled()) {
            try {
                LOG.trace("Response for command {} : \n{}", commandOfRequest, responseSerializer.serialize(responseToLog));
            } catch (Exception responseNotSerializableException) {
                LOG.error("Response couldn't be serialized successfully", responseNotSerializableException);
            }
        }
    }

    private void logResponseForInvalidInstruction(Response responseToLog, int responseResultCode) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created response with result code {} for no forwarded request.", responseResultCode);
        }
        if (LOG.isTraceEnabled()) {
            try {
                LOG.trace("Response for a not forwarded request {} : \n{}", responseSerializer.serialize(responseToLog));
            } catch (Exception responseNotSerializableException) {
                LOG.error("Response couldn't be serialized successfully", responseNotSerializableException);
            }
        }
    }
}
