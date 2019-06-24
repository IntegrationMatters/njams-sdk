package com.im.njams.sdk.communication_rework.instruction.control.processor.flush;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication_rework.instruction.control.processor.AbstractInstructionProcessor;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SendProjectMessageProcessorTest extends AbstractInstructionProcessor {

    private Njams njamsMock = mock(Njams.class);

    private SendProjectMessageProcessor sendProjectMessageProcessor;

    @Before
    public void setNewProcessor() {
        sendProjectMessageProcessor = spy(new SendProjectMessageProcessor(njamsMock));
    }

    @Test
    public void processInstruction() {
        Instruction instruction = instructionBuilder.prepareInstruction(Command.SEND_PROJECTMESSAGE).build();
        sendProjectMessageProcessor.processInstruction(instruction);

        verify(njamsMock).flushResources();
        final Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals(SendProjectMessageProcessor.SUCCESS_RESULT_MESSAGE, response.getResultMessage());
        assertNull(response.getDateTime());
        assertTrue(response.getParameters().isEmpty());
    }
}