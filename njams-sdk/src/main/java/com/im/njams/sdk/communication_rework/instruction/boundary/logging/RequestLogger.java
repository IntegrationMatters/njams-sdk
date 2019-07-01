package com.im.njams.sdk.communication_rework.instruction.boundary.logging;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.serializer.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class RequestLogger implements InstructionLogger {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLogger.class);

    private JsonSerializer<Object> requestSerializer = new JsonSerializer<>();

    @Override
    public void log(Instruction instruction) {
        Request requestToLog = instruction.getRequest();
        if (LOG.isDebugEnabled() && requestToLog != null) {
            LOG.debug("Received request with command: {}", requestToLog.getCommand());
        }
        if (LOG.isTraceEnabled()) {
            try {
                LOG.trace("Request: \n{}", requestSerializer.serialize(requestToLog));
            } catch (Exception requestNotSerializableException) {
                LOG.error("Request couldn't be serialized successfully", requestNotSerializableException);
            }
        }
    }
}
