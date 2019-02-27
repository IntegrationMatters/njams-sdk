/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * The Software shall be used for Good, not Evil.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * This class tests the AbstractReceiver methods.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
public class AbstractReceiverTest {

    private static final String TESTCOMMAND = "testCommand";

    //setNjams tests
    /**
     * This method tests if the setNjams method works.
     */
    @Test
    public void testSetNjams() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        Njams njams = mock(Njams.class);
        impl.setNjams(njams);
        assertEquals(njams, impl.njams);
    }

    /**
     * This method tests if the setNjams method works with a null as parameter
     */
    @Test
    public void testSetNullNjams() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.setNjams(null);
        assertEquals(null, impl.njams);
    }

    //onInstruction tests
    /**
     * This method tests if the onInstruction method works without an Njams
     * object.
     */
    @Test
    public void testOnInstructionWithNullNjams() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        Instruction inst = new Instruction();
        impl.onInstruction(inst);
        assertEquals(null, inst.getResponse());
        assertEquals(null, impl.njams);
    }

    /**
     * This method tests if the onInstruction method works with an Njams object,
     * but without an Instruction.
     */
    @Test
    public void testOnInstructionWithNullInstruction() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        Njams njams = mock(Njams.class);
        impl.setNjams(njams);
        Instruction inst = null;
        impl.onInstruction(inst);
        assertEquals(null, inst);
    }

    /**
     * This method tests if the onInstruction method works with an Njams object
     * and an Instruction, but without a request.
     */
    @Test
    public void testOnInstructionWithNullRequest() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        Njams njams = mock(Njams.class);
        impl.setNjams(njams);
        Instruction inst = new Instruction();
        impl.onInstruction(inst);
        assertNull(inst.getRequest());
        assertEquals(1, inst.getResponse().getResultCode());
        assertEquals("Instruction should have a valid request with a command", inst.getResponse().getResultMessage());
    }

    /**
     * This method tests if the onInstruction method works with an Njams object,
     * an Instruction and a request, but without a command.
     */
    @Test
    public void testOnInstructionWithNullCommand() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        Njams njams = mock(Njams.class);
        impl.setNjams(njams);
        Instruction inst = new Instruction();
        Request req = new Request();
        inst.setRequest(req);
        impl.onInstruction(inst);
        assertNull(inst.getRequest().getCommand());
        assertEquals(1, inst.getResponse().getResultCode());
        assertEquals("Instruction should have a valid request with a command", inst.getResponse().getResultMessage());
    }

    /**
     * This method tests if the onInstruction method works with an Njams object,
     * an Instruction, a request and a command, but without any
     * InstructionListeners.
     */
    @Test
    public void testOnInstructionWithEmptyInstructionListener() {
        testNoAppropriateInstructionListenerFound(new ArrayList<>());
    }

    /**
     * This method tests if the onInstruction method works with an Njams object,
     * an Instruction, a request, a command and an InstructionListener, but the
     * InstructionListener throws an exception.
     */
    @Test
    public void testOnInstructionWithAnExceptionThrowingInstructionListener() {
        List<InstructionListener> list = new ArrayList<>();
        list.add(new ExceptionInstructionListener());
        testNoAppropriateInstructionListenerFound(list);
    }

    private void testNoAppropriateInstructionListenerFound(List<InstructionListener> list) {

        Instruction inst = mockUp(list);

        assertEquals(1, inst.getResponse().getResultCode());
        assertEquals("No InstructionListener for " + inst.getRequest().getCommand() + " found", inst.getResponse().getResultMessage());
        assertEquals("true", inst.getRequestParameterByName("Extended"));
    }

    private Instruction mockUp(List<InstructionListener> list) {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        Njams njams = mock(Njams.class);
        impl.setNjams(njams);
        when(njams.getInstructionListeners()).thenReturn(list);
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(TESTCOMMAND);
        inst.setRequest(req);
        impl.onInstruction(inst);
        return inst;
    }

    /**
     * This method tests if the onInstruction method works with an Njams object,
     * an Instruction, a request, a command and an InstructionListener that
     * handles the command correctly.
     */
    @Test
    public void testOnInstructionWithTheRightInstructionListener() {
        List<InstructionListener> list = new ArrayList<>();
        list.add(new RightInstructionListener());
        testGoodResultWithInstructions(list);
    }

    /**
     * This method tests if the onInstruction method works with an Njams object,
     * an Instruction, a request, a command and two InstructionListeners that
     * could handle an the same command, both handle the instruction successive.
     * The second one (Here the RightInstructionListener) sets the Response.
     */
    @Test
    public void testOnInstructionWithFirstTheWrongThenTheRightInstructionListener() {
        List<InstructionListener> list = new ArrayList<>();
        list.add(new WrongInstructionListener());
        list.add(new RightInstructionListener());
        testGoodResultWithInstructions(list);
    }

    /**
     * This method tests if the onInstruction method works with an Njams object,
     * an Instruction, a request, a command and two InstructionListeners that
     * could handle an the same command, both handle the instruction successive.
     * The first one throws an exception, but the second one handles the command
     * correctly and sets the Response.
     */
    @Test
    public void testOnInstructionWithAnExceptionAndAResponse() {
        List<InstructionListener> list = new ArrayList<>();
        list.add(new ExceptionInstructionListener());
        list.add(new RightInstructionListener());
        testGoodResultWithInstructions(list);
    }

    /**
     * This method tests if the onInstruction method works with an Njams object,
     * an Instruction, a request, a command and two InstructionListeners that
     * could handle an the same command, both handle the instruction successive.
     * The first one creates a response, but the second one throws an exception.
     * The response of the first one doesn't change at all.
     */
    @Test
    public void testOnInstructionWithAResponseAndAnException() {
        List<InstructionListener> list = new ArrayList<>();
        list.add(new RightInstructionListener());
        list.add(new ExceptionInstructionListener());
        testGoodResultWithInstructions(list);
    }

    private void testGoodResultWithInstructions(List<InstructionListener> list) {

        Instruction inst = mockUp(list);

        assertEquals(0, inst.getResponse().getResultCode());
        assertEquals("Good", inst.getResponse().getResultMessage());
        assertEquals("true", inst.getRequestParameterByName("Extended"));
    }

    @Test
    public void testOnInstructionWithTheWrongInstructionListener() {
        List<InstructionListener> list = new ArrayList<>();
        list.add(new WrongInstructionListener());
        testBadResultWithInstructions(list);
    }

    /**
     * This method tests if the onInstruction method works with an Njams object,
     * an Instruction, a request, a command and an InstructionListener that
     * handles the command correctly.
     */
    private void testBadResultWithInstructions(List<InstructionListener> list) {
        Instruction inst = mockUp(list);

        assertEquals(1, inst.getResponse().getResultCode());
        assertEquals("Bad", inst.getResponse().getResultMessage());
        assertEquals("true", inst.getRequestParameterByName("Extended"));
    }

    /**
     * This method tests if the onInstruction method works with an Njams object,
     * an Instruction, a request, a command and two InstructionListeners that
     * could handle an the same command, both handle the instruction successive.
     * The second one (Here the WrongInstructionListener) sets the Response.
     */
    @Test
    public void testOnInstructionWithFirstTheRightThenTheWrongInstructionListener() {
        List<InstructionListener> list = new ArrayList<>();
        list.add(new RightInstructionListener());
        list.add(new WrongInstructionListener());
        testBadResultWithInstructions(list);
    }
    
    @Test
    public void testOnInstructionExtendedRequestException() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        Njams njams = mock(Njams.class);
        impl.setNjams(njams);
        when(njams.getInstructionListeners()).thenReturn(new ArrayList<>());
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(TESTCOMMAND);
        req.getParameters().put("isException", "true");
        inst.setRequest(req);
        
        impl.onInstruction(inst);

        assertEquals(2, inst.getResponse().getResultCode());
        assertEquals("Something didn't work!", inst.getResponse().getResultMessage());
        assertEquals("true", inst.getRequestParameterByName("isException"));
    }

    //reconnect tests
    /**
     * This method tests if the Reconnect works, if everything works fine.
     */
    @Test
    public void testReconnect() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        assertTrue(impl.isDisconnected());
        assertFalse(impl.isConnecting());
        assertFalse(impl.isConnected());
        impl.reconnect();
        assertTrue(impl.isConnected());
        assertFalse(impl.isDisconnected());
        assertFalse(impl.isConnecting());
    }

    /**
     * This method tests if the Reconnect does nothing, if the status is
     * CONNECTING
     */
    @Test
    public void testReconnectWhileConnecting() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.setConnectionStatus(ConnectionStatus.CONNECTING);
        assertFalse(impl.isDisconnected());
        assertTrue(impl.isConnecting());
        assertFalse(impl.isConnected());
        impl.reconnect();
        assertFalse(impl.isDisconnected());
        assertTrue(impl.isConnecting());
        assertFalse(impl.isConnected());
    }

    /**
     * This method tests if the Reconnect does nothing, if the status is
     * CONNECTED
     */
    @Test
    public void testReconnectWhileConnected() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.setConnectionStatus(ConnectionStatus.CONNECTED);
        assertFalse(impl.isDisconnected());
        assertFalse(impl.isConnecting());
        assertTrue(impl.isConnected());
        impl.reconnect();
        assertFalse(impl.isDisconnected());
        assertFalse(impl.isConnecting());
        assertTrue(impl.isConnected());
    }

    /**
     * This method tests if the thread sleep for longer than 1 second after an
     * exception was thrown.
     */
    @Test
    public void testReconnectWhenExceptionIsThrown() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.throwException = true;
        assertTrue(impl.isDisconnected());
        assertFalse(impl.isConnecting());
        assertFalse(impl.isConnected());
        long currentTimeMillis = System.currentTimeMillis();
        impl.reconnect();
        long afterReconnectMillis = System.currentTimeMillis();
        long diff = afterReconnectMillis - currentTimeMillis;
        assertTrue(diff >= 1000L);
        System.out.println("The Thread slept ~ " + diff + "ms.");
    }

    //start tests
    /**
     * This method tests if the start method established a connection normally.
     */
    @Test
    public void testStartWhileDisconnected() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        assertTrue(impl.isDisconnected());
        impl.start();
        assertTrue(impl.isConnected());
    }

    /**
     * This method tests if the start method established a connection normally
     * if the status is already connecting.
     */
    @Test
    public void testStartWhileConnecting() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.setConnectionStatus(ConnectionStatus.CONNECTING);
        assertTrue(impl.isConnecting());
        impl.start();
        assertTrue(impl.isConnected());
    }

    /**
     * This method tests if the start method established a connection normally
     * if the status is already connected.
     */
    @Test
    public void testStartWhileConnected() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.setConnectionStatus(ConnectionStatus.CONNECTED);
        assertTrue(impl.isConnected());
        impl.start();
        assertTrue(impl.isConnected());
    }

    /**
     * This method tests if the start method restarts if an
     * NjamsSdkRuntimeException is thrown.
     *
     * @throws java.lang.InterruptedException for thread
     */
    @Test
    public void testStartWithException() throws InterruptedException {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.throwException = true;
        assertTrue(impl.isDisconnected());
        impl.start();
        Thread.sleep(100);
        assertTrue(impl.isConnected());
    }

    //onException tests
    /**
     * This method tests if the onException method reconnects properly if
     * disconnected.
     *
     * @throws InterruptedException for thread
     */
    @Test
    public void testOnExceptionWhileDisconnected() throws InterruptedException {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        assertNotNull(impl.connectionStatus);
        assertTrue(impl.isDisconnected());
        impl.onException(null);
        Thread.sleep(100);
        assertTrue(impl.isConnected());
    }

    /**
     * This method tests if the onException method reconnects properly if
     * connecting. It shouldn't change anything, because stop() in
     * AbstractReceiverImpl does nothing.
     *
     * @throws InterruptedException for thread
     */
    @Test
    public void testOnExceptionWhileConnecting() throws InterruptedException {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.setConnectionStatus(ConnectionStatus.CONNECTING);
        assertNotNull(impl.connectionStatus);
        assertTrue(impl.isConnecting());
        impl.onException(null);
        Thread.sleep(100);
        assertTrue(impl.isConnecting());
    }

    /**
     * This method tests if the onException method reconnects properly if
     * connected. It should stay connected.
     *
     * @throws InterruptedException for thread
     */
    @Test
    public void testOnExceptionWhileConnected() throws InterruptedException {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.setConnectionStatus(ConnectionStatus.CONNECTED);
        assertNotNull(impl.connectionStatus);
        assertTrue(impl.isConnected());
        impl.onException(null);
        Thread.sleep(100);
        assertTrue(impl.isConnected());
    }

    @Test
    public void testOnExceptionOverflow() throws InterruptedException {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.throwManyExceptions = true;
        for (int i = 0; i < 10; i++) {
            impl.onException(null);
        }
        Thread.sleep(500);
        assertTrue(impl.verifyingCounter.get() <= 1);
    }

    //isConnected test
    /**
     * This method tests if the connectionStatus is DISCONNECTED after the
     * initialisation of the AbstractReceiverImpl.
     */
    @Test
    public void testIsDisconnectedAtInitialisation() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        assertNotNull(impl.connectionStatus);
        assertEquals(ConnectionStatus.DISCONNECTED, impl.connectionStatus);
    }

    /**
     * This method tests if method isConnected returns true iff the
     * connectionStatus is CONNECTED.
     */
    @Test
    public void testIsConnected() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.setConnectionStatus(ConnectionStatus.CONNECTED);
        assertEquals(ConnectionStatus.CONNECTED, impl.connectionStatus);
        assertTrue(impl.isConnected());
        assertFalse(impl.isConnecting());
        assertFalse(impl.isDisconnected());
    }

    /**
     * This method tests if method isConnecting returns true iff the
     * connectionStatus is CONNECTING.
     */
    @Test
    public void testIsConnecting() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.setConnectionStatus(ConnectionStatus.CONNECTING);
        assertEquals(ConnectionStatus.CONNECTING, impl.connectionStatus);
        assertFalse(impl.isConnected());
        assertTrue(impl.isConnecting());
        assertFalse(impl.isDisconnected());
    }

    /**
     * This method tests if method isDisconnected returns true iff the
     * connectionStatus is DISCONNECTED.
     */
    @Test
    public void testIsDisconnected() {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.setConnectionStatus(ConnectionStatus.DISCONNECTED);
        assertEquals(ConnectionStatus.DISCONNECTED, impl.connectionStatus);
        assertFalse(impl.isConnected());
        assertFalse(impl.isConnecting());
        assertTrue(impl.isDisconnected());
    }

    //Helper classes
    private class AbstractReceiverImpl extends AbstractReceiver {

        private boolean throwException = false;

        private boolean throwManyExceptions = false;

        private int throwingCounter = 0;

        //This method should be tested by the real subclass of the AbstractReceiver
        @Override
        public String getName() {
            return "AbstractReceiverTest";
        }

        //This method should be tested by the real subclass of the AbstractReceiver
        @Override
        public void init(Properties properties) {
        }

        @Override
        protected Response extendRequest(Request req) {
            if (req.getParameters().containsKey("isException")) {
                Response resp = new Response();
                resp.setResultCode(2);
                resp.setResultMessage("Something didn't work!");
                return resp;
            }
            req.getParameters().put("Extended", "true");

            return null;
        }

        //This method should be tested by the real subclass of the AbstractReceiver
        @Override
        public void connect() {
            if (throwException) {
                throwException = false;
                throw new NjamsSdkRuntimeException("AbstractReceiverTestException");

            } else if (throwManyExceptions) {
                while (throwingCounter < 10) {
                    throwingCounter++;
                    throw new NjamsSdkRuntimeException("AbstractReceiverTestException");
                }
                throwManyExceptions = false;
            } else {
                connectionStatus = ConnectionStatus.CONNECTED;
            }
        }

        //This method should be tested by the real subclass of the AbstractReceiver
        @Override
        public void stop() {
            //Does nothing in this class.
        }

        /**
         * This method is for testing.
         *
         * @param con the connectionstatus
         */
        private void setConnectionStatus(ConnectionStatus con) {
            connectionStatus = con;
        }
    }

    /**
     * This class is used for the onInstructionTests
     */
    private class RightInstructionListener implements InstructionListener {

        @Override
        public void onInstruction(Instruction instruction) {
            if (instruction.getRequest().getCommand().equals(TESTCOMMAND)) {
                Response res = new Response();
                res.setResultCode(0);
                res.setResultMessage("Good");
                instruction.setResponse(res);
            }
        }

    }

    /**
     * This class is used for the onInstructionTests
     */
    private class WrongInstructionListener implements InstructionListener {

        @Override
        public void onInstruction(Instruction instruction) {
            if (instruction.getRequest().getCommand().equals(TESTCOMMAND)) {
                Response res = new Response();
                res.setResultCode(1);
                res.setResultMessage("Bad");
                instruction.setResponse(res);
            }
        }
    }

    /**
     * This class is used for the onInstructionTests
     */
    private class ExceptionInstructionListener implements InstructionListener {

        @Override
        public void onInstruction(Instruction instruction) {
            throw new NjamsSdkRuntimeException("Bad Exception");
        }

    }
}
