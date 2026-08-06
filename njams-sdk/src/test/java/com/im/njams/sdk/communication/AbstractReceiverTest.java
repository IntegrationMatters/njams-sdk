/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.ClientSettings;

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
        assertEquals("No InstructionListener for " + inst.getRequest().getCommand() + " found",
                inst.getResponse().getResultMessage());
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
        impl.reconnect(new NjamsSdkRuntimeException("Test", new Exception("Test2")));
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
        impl.reconnect(new NjamsSdkRuntimeException("Test", new Exception("Test2")));
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
        impl.reconnect(new NjamsSdkRuntimeException("Test", new Exception("Test2")));
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
        impl.reconnect(new NjamsSdkRuntimeException("Test", new Exception("Test2")));
        long afterReconnectMillis = System.currentTimeMillis();
        long diff = afterReconnectMillis - currentTimeMillis;
        assertTrue(diff >= 500L);
        System.out.println("The Thread slept ~ " + diff + "ms.");
    }

    @Test
    public void reconnectDoesNothingOnceShutdownRequested() throws Exception {
        AbstractReceiverImpl impl = new AbstractReceiverImpl();
        impl.setShouldShutdown(true); // public method added to AbstractReceiver in Step 5 below
        impl.throwManyExceptionsForTest = true; // connect() keeps failing so the loop would otherwise spin
        assertTrue(impl.isDisconnected());
        impl.reconnect(new NjamsSdkRuntimeException("Test"));
        assertTrue("must not have connected (loop must not even attempt connect() once shutdown)",
            impl.isDisconnected());
    }

    @Test
    public void cancelReconnectInterruptsABlockedReconnectThread() throws Exception {
        BlockingConnectReceiverImpl impl = new BlockingConnectReceiverImpl();
        Thread reconnector = new Thread(() -> impl.reconnect(new NjamsSdkRuntimeException("lost")));
        reconnector.setDaemon(true);
        reconnector.start();
        assertTrue("reconnect must have entered connect() and blocked",
            impl.connectEntered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        impl.cancelReconnect();
        reconnector.join(2000);
        assertFalse("reconnect thread must terminate once cancelReconnect() interrupts the blocked connect()",
            reconnector.isAlive());
    }

    @Test
    public void cancelReconnectInterruptsABlockedStartupConnect() throws Exception {
        // BlockingConnectReceiverImpl.connect() throws on interrupt (unlike SlowConnectReceiverImpl, whose
        // connect() swallows InterruptedException and still succeeds — not suitable for this test).
        BlockingConnectReceiverImpl impl = new BlockingConnectReceiverImpl();
        impl.beginConnect();
        assertTrue("startup connect must have entered connect() and blocked",
            impl.connectEntered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        impl.cancelReconnect();
        // startWithTimeout must return promptly (throwing) instead of waiting out connect()'s 10s sleep
        long before = System.currentTimeMillis();
        try {
            impl.startWithTimeout(4000L);
            fail("expected failure after the startup connect thread was interrupted");
        } catch (NjamsSdkRuntimeException ignored) {
            // expected
        }
        assertTrue("must return promptly, not wait out the full connect delay",
            System.currentTimeMillis() - before < 4000L);
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

    @Test
    public void testStartWithTimeoutDefaultDelegatesToStart() {
        // Receiver implementations that do not extend AbstractReceiver get the default
        // startWithTimeout which calls start().
        final boolean[] startCalled = {false};
        Receiver simpleReceiver = new Receiver() {
            @Override public String getName() { return "simple"; }
            @Override public void init(ClientSettings settings) {}
            @Override public void setNjams(Njams njams) {}
            @Override public void onInstruction(Instruction i) {}
            @Override public void start() { startCalled[0] = true; }
            @Override public void stop() {}
        };
        simpleReceiver.startWithTimeout(100L);
        assertTrue("default startWithTimeout must delegate to start()", startCalled[0]);
    }

    @Test
    public void testStartWithTimeout_successWithinTimeout() {
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(0, false);
        impl.startWithTimeout(200L);
        assertTrue("receiver must be connected after successful startWithTimeout", impl.isConnected());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void testStartWithTimeout_throwsOnTimeout() {
        // connect takes 2 s, timeout is 100 ms
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(2000, false);
        impl.startWithTimeout(100L);
    }

    @Test
    public void testStartWithTimeout_disconnectedAfterTimeout() {
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(2000, false);
        try {
            impl.startWithTimeout(100L);
        } catch (NjamsSdkRuntimeException ignored) {}
        assertTrue("receiver must be DISCONNECTED after timeout", impl.isDisconnected());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void testStartWithTimeout_throwsWhenConnectThrows() {
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(0, true);
        impl.startWithTimeout(500L);
    }

    @Test
    public void testStartWithTimeout_disconnectedWhenConnectThrows() {
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(0, true);
        try {
            impl.startWithTimeout(500L);
        } catch (NjamsSdkRuntimeException ignored) {}
        assertTrue("receiver must be DISCONNECTED when connect() throws", impl.isDisconnected());
    }

    @Test
    public void testStartWithTimeout_noReconnectThreadOnTimeout() throws InterruptedException {
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(2000, false);
        try {
            impl.startWithTimeout(100L);
        } catch (NjamsSdkRuntimeException ignored) {}
        // If a reconnect thread had been started it would eventually call connect()
        // and set status to CONNECTED. Wait briefly and confirm status stays DISCONNECTED.
        Thread.sleep(300);
        assertTrue("no reconnect thread must be started on timeout", impl.isDisconnected());
    }

    @Test
    public void testStartWithTimeout_cleansUpAfterLateConnect() throws InterruptedException {
        // connect takes 400 ms, timeout is 100 ms — background thread eventually connects late
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(400, false);
        try {
            impl.startWithTimeout(100L);
        } catch (NjamsSdkRuntimeException ignored) {}
        // wait for background thread to finish connecting
        Thread.sleep(600);
        assertTrue("stop() must be called to release resources acquired after timeout", impl.stopCalled);
    }

    @Test
    public void testBeginConnect_earlyStartOverlapsWithSetup() throws InterruptedException {
        // connect takes 200 ms; begin early, then wait 250 ms before calling startWithTimeout
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(200, false);
        impl.beginConnect();
        Thread.sleep(250); // connection completes during this sleep
        long before = System.currentTimeMillis();
        // timeout of 50 ms would be too short if connect hadn't already finished
        impl.startWithTimeout(50L);
        long elapsed = System.currentTimeMillis() - before;
        assertTrue("receiver must be connected", impl.isConnected());
        assertTrue("startWithTimeout must return nearly immediately when already connected", elapsed < 50);
    }

    @Test
    public void testBeginConnect_idempotent() {
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(0, false);
        impl.beginConnect();
        impl.beginConnect(); // second call must be a no-op, not start a second thread
        impl.startWithTimeout(200L);
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

        private boolean throwManyExceptionsForTest = false;

        private int throwingCounter = 0;

        public static final int THROWINGMAXCOUNTER = 10;

        public static final long RECONNECT_INTERVAL = AbstractReceiver.INIT_RECONNECT_INTERVAL;

        //This method should be tested by the real subclass of the AbstractReceiver
        @Override
        public String getName() {
            return "AbstractReceiverTest";
        }

        //This method should be tested by the real subclass of the AbstractReceiver
        @Override
        public void init(ClientSettings settings) {
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
            if (throwManyExceptionsForTest) {
                throw new NjamsSdkRuntimeException("AbstractReceiverTestException");
            }
            if (throwException) {
                throwException = false;
                throw new NjamsSdkRuntimeException("AbstractReceiverTestException");

            } else if (throwManyExceptions && throwingCounter < THROWINGMAXCOUNTER) {
                throwingCounter++;
                throw new NjamsSdkRuntimeException("AbstractReceiverTestException");
            }
            connectionStatus = ConnectionStatus.CONNECTED;
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
    private static class ExceptionInstructionListener implements InstructionListener {

        @Override
        public void onInstruction(Instruction instruction) {
            throw new NjamsSdkRuntimeException("Bad Exception", new Exception("Test2"));
        }

    }

    private class SlowConnectReceiverImpl extends AbstractReceiver {
        private final long connectDelayMs;
        private final boolean throwOnConnect;
        volatile boolean stopCalled = false;

        SlowConnectReceiverImpl(long connectDelayMs, boolean throwOnConnect) {
            this.connectDelayMs = connectDelayMs;
            this.throwOnConnect = throwOnConnect;
        }

        @Override
        public String getName() { return "SlowReceiver"; }

        @Override
        public void init(ClientSettings settings) {}

        @Override
        protected Response extendRequest(Request req) {
            return null;
        }

        @Override
        public void connect() {
            try {
                if (connectDelayMs > 0) {
                    Thread.sleep(connectDelayMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (throwOnConnect) {
                throw new NjamsSdkRuntimeException("Simulated connect failure");
            }
            connectionStatus = ConnectionStatus.CONNECTED;
        }

        @Override
        public void stop() {
            stopCalled = true;
            connectionStatus = ConnectionStatus.DISCONNECTED;
        }
    }

    private class BlockingConnectReceiverImpl extends AbstractReceiver {
        final java.util.concurrent.CountDownLatch connectEntered = new java.util.concurrent.CountDownLatch(1);

        @Override
        public String getName() { return "BlockingReceiver"; }

        @Override
        public void init(ClientSettings settings) {}

        @Override
        protected Response extendRequest(Request req) { return null; }

        @Override
        public void connect() {
            connectEntered.countDown();
            try {
                Thread.sleep(10_000); // effectively "blocks" until interrupted by cancelReconnect()
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NjamsSdkRuntimeException("interrupted");
            }
            connectionStatus = ConnectionStatus.CONNECTED;
        }

        @Override
        public void stop() {}
    }

    @Test
    public void startWithTimeoutTwoArgReturnsTrueOnSuccess() {
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(0, false);
        assertTrue(impl.startWithTimeout(200L, false));
        assertTrue(impl.isConnected());
    }

    @Test
    public void startWithTimeoutTwoArgFailFastReturnsFalseAndCancelsOnFailure() throws InterruptedException {
        SlowConnectReceiverImpl impl = new SlowConnectReceiverImpl(0, true);
        assertFalse(impl.startWithTimeout(200L, false));
        assertTrue(impl.isDisconnected());
        Thread.sleep(300);
        assertTrue("fail-fast must not leave a reconnect loop running", impl.isDisconnected());
    }

    @Test
    public void startWithTimeoutTwoArgReconnectPolicyReturnsTrueAndEntersBackgroundReconnect()
            throws InterruptedException {
        // connect() fails once, then subsequent connects succeed
        FlakyThenSucceedsReceiverImpl impl = new FlakyThenSucceedsReceiverImpl();
        assertTrue("reconnect policy: startWithTimeout must return true despite the initial failure",
            impl.startWithTimeout(200L, true));
        // poll for the background reconnect to succeed (no fixed sleep-then-assert)
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!impl.isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue("background reconnect must eventually succeed", impl.isConnected());
    }

    @Test
    public void defaultTwoArgStartWithTimeoutOnPlainReceiverIgnoresReconnectOnFailure() {
        final boolean[] startCalled = {false};
        Receiver simpleReceiver = new Receiver() {
            @Override public String getName() { return "simple"; }
            @Override public void init(ClientSettings settings) {}
            @Override public void setNjams(Njams njams) {}
            @Override public void onInstruction(Instruction i) {}
            @Override public void start() { startCalled[0] = true; }
            @Override public void stop() {}
        };
        assertTrue(simpleReceiver.startWithTimeout(100L, true));
        assertTrue(startCalled[0]);
    }

    private class FlakyThenSucceedsReceiverImpl extends AbstractReceiver {
        private final java.util.concurrent.atomic.AtomicBoolean firstAttempt =
            new java.util.concurrent.atomic.AtomicBoolean(true);

        @Override
        public String getName() { return "FlakyReceiver"; }

        @Override
        public void init(ClientSettings settings) {}

        @Override
        protected Response extendRequest(Request req) { return null; }

        @Override
        public void connect() {
            if (firstAttempt.compareAndSet(true, false)) {
                throw new NjamsSdkRuntimeException("first attempt fails");
            }
            connectionStatus = ConnectionStatus.CONNECTED;
        }

        @Override
        public void stop() {}
    }

    @Test
    public void startWithTimeoutTwoArgReconnectPolicyReturnsQuicklyDespiteMultipleRetries()
            throws InterruptedException {
        // connect() fails 3 times, then succeeds — backoff sleeps will be much longer than startup timeout
        MultiFailThenSucceedsReceiverImpl impl = new MultiFailThenSucceedsReceiverImpl(3);
        long before = System.currentTimeMillis();
        assertTrue("reconnect policy: startWithTimeout must return true quickly despite 3 failures requiring backoff",
            impl.startWithTimeout(100L, true));
        long elapsed = System.currentTimeMillis() - before;
        assertTrue("startWithTimeout must return promptly (within ~500ms) to allow caller to proceed; "
            + "elapsed=" + elapsed + "ms (large backoff sleeps should run in background, not on caller)",
            elapsed < 500);
        assertTrue("receiver must be disconnected after return (not yet reconnected)",
            impl.isDisconnected());
        // poll for the background reconnect to succeed
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!impl.isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue("background reconnect must eventually succeed despite multiple failures", impl.isConnected());
    }

    private class MultiFailThenSucceedsReceiverImpl extends AbstractReceiver {
        private final int failureCount;
        private final java.util.concurrent.atomic.AtomicInteger attemptCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

        MultiFailThenSucceedsReceiverImpl(int failureCount) {
            this.failureCount = failureCount;
        }

        @Override
        public String getName() { return "MultiFailReceiver"; }

        @Override
        public void init(ClientSettings settings) {}

        @Override
        protected Response extendRequest(Request req) { return null; }

        @Override
        public void connect() {
            int attempt = attemptCount.incrementAndGet();
            if (attempt <= failureCount) {
                throw new NjamsSdkRuntimeException("attempt " + attempt + " fails");
            }
            connectionStatus = ConnectionStatus.CONNECTED;
        }

        @Override
        public void stop() {}
    }
}
