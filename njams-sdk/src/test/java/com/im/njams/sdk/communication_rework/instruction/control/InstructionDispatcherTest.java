package com.im.njams.sdk.communication_rework.instruction.control;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication_rework.instruction.control.processor.FallbackProcessor;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InstructionDispatcherTest {


    private static final Logger LOG = LoggerFactory.getLogger(InstructionDispatcherTest.class);

    private static final String EXCEPTION_THROWER = "Exception_Thrower";

    private static final String INVALID_COMMAND = "Invalid";

    private InstructionDispatcher dispatcher;

    public InstructionDispatcherTest(){
        this.dispatcher = new InstructionDispatcher();
    }

    @Before
    public void cleanUpInstructionProcessors(){
        dispatcher.removeAllInstructionProcessors();
        InstructionProcessor spiedFallbackProcessor = spy(new TestFallbackProcessor());
        dispatcher.setFallbackProcessor(spiedFallbackProcessor);
    }

    private void verifyZeroInteractionWithFallbackProcessor(){
        verifyZeroInteractions(dispatcher.getFallbackProcessor());
    }

    @Test
    public void addInstructionProcessor() {
        assertNull(dispatcher.getInstructionProcessorForDistinctCommand(EXCEPTION_THROWER));
        this.addExceptionThrowingInstructionProcessor();
        assertNotNull(dispatcher.getInstructionProcessorForDistinctCommand(EXCEPTION_THROWER));
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void addIsDistinctForUniqueCommand(){
        assertNull(dispatcher.getInstructionProcessorForDistinctCommand(EXCEPTION_THROWER));
        InstructionProcessor exceptionProcessor1 = new ExceptionThrowingInstructionProcessor();
        dispatcher.addInstructionProcessorForDistinctCommand(exceptionProcessor1);
        assertNotNull(dispatcher.getInstructionProcessorForDistinctCommand(EXCEPTION_THROWER));
        assertEquals(dispatcher.getInstructionProcessorForDistinctCommand(EXCEPTION_THROWER), exceptionProcessor1);
        InstructionProcessor exceptionProcessor2 = new ExceptionThrowingInstructionProcessor();
        assertNotEquals(exceptionProcessor1, exceptionProcessor2);
        dispatcher.addInstructionProcessorForDistinctCommand(exceptionProcessor2);
        assertNotNull(dispatcher.getInstructionProcessorForDistinctCommand(EXCEPTION_THROWER));
        assertEquals(dispatcher.getInstructionProcessorForDistinctCommand(EXCEPTION_THROWER), exceptionProcessor2);
        verifyZeroInteractionWithFallbackProcessor();
    }

    private void addExceptionThrowingInstructionProcessor(){
        dispatcher.addInstructionProcessorForDistinctCommand(new ExceptionThrowingInstructionProcessor());
    }

    @Test
    public void removeInstructionProcessor() {
        this.addExceptionThrowingInstructionProcessor();
        InstructionProcessor instructionProcessor = dispatcher.getInstructionProcessorForDistinctCommand(EXCEPTION_THROWER);
        dispatcher.removeInstructionProcessorForDistinctCommand(instructionProcessor);
        assertNull(dispatcher.getInstructionProcessorForDistinctCommand(EXCEPTION_THROWER));
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void gettingsFallbackInstructionProcessorNotPossible(){
        InstructionProcessor fallbackProcessor = dispatcher.getInstructionProcessorForDistinctCommand(FallbackProcessor.FALLBACK);
        assertNull(fallbackProcessor);
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void dispatchNullInstruction() {
        Instruction invalidInstruction = null;
        dispatcher.dispatchInstruction(invalidInstruction);
        verify(dispatcher.getFallbackProcessor()).processInstruction(invalidInstruction);
    }

    @Test
    public void dispatchNullRequest() {
        Instruction instructionMock = mock(Instruction.class);
        when(instructionMock.getRequest()).thenReturn(null);
        dispatcher.dispatchInstruction(instructionMock);
        verify(dispatcher.getFallbackProcessor()).processInstruction(instructionMock);
    }

    @Test
    public void dispatchInstructionWithoutCorrectProcessor() {
        Instruction instructionMock = mock(Instruction.class);
        Request requestMock = mock(Request.class);
        when(instructionMock.getRequest()).thenReturn(requestMock);

        when(requestMock.getCommand()).thenReturn(INVALID_COMMAND);
        dispatcher.dispatchInstruction(instructionMock);
        verify(dispatcher.getFallbackProcessor()).processInstruction(instructionMock);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void dispatchInstructionWithACorrectProcessor() {
        this.addExceptionThrowingInstructionProcessor();
        Instruction instructionMock = mock(Instruction.class);
        Request requestMock = mock(Request.class);
        when(instructionMock.getRequest()).thenReturn(requestMock);

        when(requestMock.getCommand()).thenReturn(EXCEPTION_THROWER);
        try {
            dispatcher.dispatchInstruction(instructionMock);
        }catch(NjamsSdkRuntimeException e){
            LOG.debug(e.getMessage());
            verifyZeroInteractionWithFallbackProcessor();
            throw e;
        }
    }

    @Test
    public void getInstructionProcessor() {
        InstructionProcessor testProcessor = new ExceptionThrowingInstructionProcessor();
        assertNull(dispatcher.getInstructionProcessorForDistinctCommand(EXCEPTION_THROWER));
        dispatcher.addInstructionProcessorForDistinctCommand(testProcessor);
        assertEquals(testProcessor, dispatcher.getInstructionProcessorForDistinctCommand(EXCEPTION_THROWER));
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void removeAllInstructionProcessors() {
        assertTrue(dispatcher.getAllInstructionProcessorsExceptFallbackProcessor().isEmpty());
        this.addExceptionThrowingInstructionProcessor();
        assertFalse(dispatcher.getAllInstructionProcessorsExceptFallbackProcessor().isEmpty());
        dispatcher.removeAllInstructionProcessors();
        assertTrue(dispatcher.getAllInstructionProcessorsExceptFallbackProcessor().isEmpty());
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void isFallbackProcessorAvailableAfterConstruction(){
        InstructionDispatcher testDis = new InstructionDispatcher();
        assertNotNull(testDis.getFallbackProcessor());
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void setFallbackProcessor(){
        InstructionProcessor fallbackProcessor = dispatcher.getFallbackProcessor();
        assertNotNull(fallbackProcessor);
        InstructionProcessor fallbackProcessor2  = new FallbackProcessor();
        dispatcher.setFallbackProcessor(fallbackProcessor2);
        assertEquals(fallbackProcessor2 ,dispatcher.getFallbackProcessor());
        assertNotEquals(fallbackProcessor, fallbackProcessor2);
    }

    @Test
    public void testGetAllInstructionProcessorsExceptFallbackProcessor(){
        assertTrue(dispatcher.getAllInstructionProcessorsExceptFallbackProcessor().isEmpty());
        InstructionProcessor testProcessor = new ExceptionThrowingInstructionProcessor();
        dispatcher.addInstructionProcessorForDistinctCommand(testProcessor);
        assertFalse(dispatcher.getAllInstructionProcessorsExceptFallbackProcessor().isEmpty());
        assertTrue(dispatcher.getAllInstructionProcessorsExceptFallbackProcessor().size() == 1);
        assertTrue(dispatcher.getAllInstructionProcessorsExceptFallbackProcessor().containsValue(testProcessor));
        InstructionProcessor testProcessor2 = new FallbackProcessor();
        dispatcher.addInstructionProcessorForDistinctCommand(testProcessor2);
        assertFalse(dispatcher.getAllInstructionProcessorsExceptFallbackProcessor().isEmpty());
        assertTrue(dispatcher.getAllInstructionProcessorsExceptFallbackProcessor().size() == 2);
        assertTrue(dispatcher.getAllInstructionProcessorsExceptFallbackProcessor().containsValue(testProcessor2));
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void containsInstructionProcessor(){
        assertFalse(dispatcher.containsInstructionProcessorForDistinctCommand(EXCEPTION_THROWER));
        addExceptionThrowingInstructionProcessor();
        assertTrue(dispatcher.containsInstructionProcessorForDistinctCommand(EXCEPTION_THROWER));
        assertTrue(dispatcher.containsInstructionProcessorForDistinctCommand(EXCEPTION_THROWER.toLowerCase()));
        assertTrue(dispatcher.containsInstructionProcessorForDistinctCommand(EXCEPTION_THROWER.toUpperCase()));
    }

    private class ExceptionThrowingInstructionProcessor extends InstructionProcessor{

        public ExceptionThrowingInstructionProcessor() {
            super(EXCEPTION_THROWER);
        }

        @Override
        public void processInstruction(Instruction instruction) {
            throw new NjamsSdkRuntimeException("ProcessInstruction has been thrown by: " + getCommandToProcess());
        }
    }

    private class TestFallbackProcessor extends InstructionProcessor{

        public TestFallbackProcessor() {
            super(FallbackProcessor.FALLBACK);
        }

        @Override
        public void processInstruction(Instruction instruction) {
            LOG.debug("processInstruction of spied FallbackProcessor has been invoked.");
        }
    }
}