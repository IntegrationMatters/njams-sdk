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
package com.im.njams.sdk;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.Receiver;
import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.communication.ReplayRequest;
import com.im.njams.sdk.communication.ReplayResponse;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.layout.CommonBfsModelLayouter;
import com.im.njams.sdk.serializer.Serializer;
import com.im.njams.sdk.settings.Settings;

/**
 * @author stkniep
 */
public class NjamsTest {

    //
    private Njams instance;

    @Before
    public void createNewInstance() {
        instance = new Njams(Path.of(), "", "", TestReceiver.getSettings());
    }

    @Test
    public void testSerializer() {
        System.out.println("addSerializer");
        final Serializer<List> expResult =
                (l, sizeLimit) -> new com.im.njams.sdk.serializer.SerializerResult("list", false);

        instance.addSerializer(ArrayList.class,
                (a, sizeLimit) -> new com.im.njams.sdk.serializer.SerializerResult(a.getClass().getSimpleName(), false));
        instance.addSerializer(List.class, expResult);

        String serialized;

        // found ArrayList serializer
        serialized = instance.serialize(new ArrayList<>());
        assertNotNull(serialized);
        assertEquals("ArrayList", serialized);

        // found default string serializer
        serialized = instance.serialize(new HashMap<>());
        assertNotNull(serialized);
        assertEquals("{}", serialized);

        // found list serializer
        serialized = instance.serialize(new LinkedList<>());
        assertNotNull(serialized);
        assertEquals("list", serialized);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void testAddJobWithoutStart() {
        ProcessModel model = new ProcessModel(Path.of("PROCESSES"), instance);
        //This should throw an NjamsSdkRuntimeException
        Job job = model.createJob();
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void testStopBeforeStart() {
        //This should throw an NjamsSdkRuntimeException
        instance.stop();
    }

    @Test
    public void testStartStopStart() {
        instance.start();
        instance.stop();
        instance.start();
    }

    /**
     * Minimal {@code AbstractReceiver & ShareableReceiver} whose {@link #removeNjams(Njams)} return value is
     * fixed at construction time, and whose {@link #cancelReconnect()} is overridden (instead of touching real
     * thread fields) so tests can observe whether it was invoked at all.
     */
    private static class FakeGatedShareableReceiver extends AbstractReceiver implements ShareableReceiver<Object> {
        private final boolean reallyStop;
        boolean removeNjamsCalled = false;
        boolean cancelReconnectCalled = false;

        FakeGatedShareableReceiver(boolean reallyStop) {
            this.reallyStop = reallyStop;
        }

        @Override
        public String getName() {
            return "fake-gated-shareable";
        }

        @Override
        public void connect() {
            connectionStatus = ConnectionStatus.CONNECTED;
        }

        @Override
        public void stop() {
            connectionStatus = ConnectionStatus.DISCONNECTED;
        }

        @Override
        public boolean removeNjams(Njams njams) {
            removeNjamsCalled = true;
            return reallyStop;
        }

        @Override
        public Path getReceiverPath(Object requestMessage, Instruction instruction) {
            return null;
        }

        @Override
        public String getClientId(Object requestMessage, Instruction instruction) {
            return null;
        }

        @Override
        public void sendReply(Object requestMessage, Instruction reply, String clientId) {
            // not used in this test
        }

        @Override
        public void cancelReconnect() {
            cancelReconnectCalled = true;
        }
    }

    private void invokeStopReceiverAfterStartupFailure(Receiver failedReceiver) throws Exception {
        Method m = Njams.class.getDeclaredMethod("stopReceiverAfterStartupFailure", Receiver.class);
        m.setAccessible(true);
        m.invoke(instance, failedReceiver);
    }

    /**
     * Regression test for a final-review fix-wave finding: {@code stopReceiverAfterStartupFailure} must consult
     * {@code removeNjams(Njams)} FIRST and only cancel a reconnect once that determined this really was the
     * last registered user — exactly mirroring {@code Njams.stop()}'s own gating. An earlier version of the fix
     * called {@code cancelReconnect()} unconditionally before {@code removeNjams(...)}, which would kill a
     * shared receiver's in-progress reconnect out from under a <em>different</em>, still-registered {@code
     * Njams} instance merely because this instance's own startup failed.
     */
    @Test
    public void stopReceiverAfterStartupFailureDoesNotCancelReconnectWhileASharedReceiverStillHasOtherUsers()
            throws Exception {
        FakeGatedShareableReceiver fake = new FakeGatedShareableReceiver(false);
        invokeStopReceiverAfterStartupFailure(fake);

        assertTrue("removeNjams() must have been consulted", fake.removeNjamsCalled);
        assertFalse("must not cancel a reconnect a still-registered sharing instance depends on",
            fake.cancelReconnectCalled);
    }

    @Test
    public void stopReceiverAfterStartupFailureCancelsReconnectOnceRemoveNjamsReportsReallyStopped()
            throws Exception {
        FakeGatedShareableReceiver fake = new FakeGatedShareableReceiver(true);
        invokeStopReceiverAfterStartupFailure(fake);

        assertTrue("removeNjams() must have been consulted", fake.removeNjamsCalled);
        assertTrue("must cancel the reconnect once removeNjams() reports this was the last user",
            fake.cancelReconnectCalled);
    }

    @Test
    public void testBeginConnectBeforeStartDoesNotBreakStart() {
        // The connection is pre-started at construction time (beginConnect()); start() must still complete
        // normally regardless — the receiver's own connect outcome no longer gates start() at all.
        Receiver okReceiver = new Receiver() {
            @Override public String getName() { return "OkReceiver"; }
            @Override public void init(ClientSettings settings) {}
            @Override public void setNjams(Njams njams) {}
            @Override public void onInstruction(Instruction i) {}
            @Override public void start() {}
            @Override public void stop() {}
        };
        TestReceiver.setReceiverMock(okReceiver);
        try {
            boolean result = instance.start();
            assertTrue("start() must succeed regardless of the receiver's own connect outcome", result);
            assertTrue(instance.isStarted());
        } finally {
            if (instance.isStarted()) {
                instance.stop();
            }
            TestReceiver.setReceiverMock(null);
        }
    }

    /**
     * Regression test (SDK-375 Part 4 review finding): a sender-construction failure (as opposed to a mere
     * connection failure) must still make {@code start()} return {@code false} cleanly, never throw. Before Part
     * 4, {@code startReceiver(boolean)} caught this exact exception (raised from its own {@code getSender()} call)
     * and returned {@code false}, short-circuiting {@code start()} before it ever reached a second,
     * unguarded {@code getSender()} call. Forces {@link com.im.njams.sdk.communication.NjamsSender#init()}'s
     * thread-pool validation to throw by configuring {@code maxSenderThreads < minSenderThreads}.
     */
    @Test
    public void testStartReturnsFalseWhenSenderConstructionFails() {
        Settings s = TestReceiver.getSettings();
        s.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "5");
        s.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "1");
        Njams njams = new Njams(Path.of("test", "senderConstructionFails"), "1.0", "test", s);
        try {
            boolean result = njams.start();
            assertFalse("start() must return false, not throw, when sender construction fails", result);
            assertFalse(njams.isStarted());
        } finally {
            if (njams.isStarted()) {
                njams.stop();
            }
        }
    }

    @Test
    public void testOnCorrectSendProjectMessageInstruction() {
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.SEND_PROJECTMESSAGE.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        instance.onInstruction(inst);
        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 0);
    }

    @Test
    public void testOnNoReplyHandlerFoundReplayMessageInstruction() {
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.REPLAY.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        instance.onInstruction(inst);

        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 1);
    }

    @Test
    public void testOnCorrectReplayMessageInstruction() {
        ReplayHandler replayHandler = (ReplayRequest request) -> {
            ReplayResponse resp = new ReplayResponse();
            resp.setResultCode(0);
            resp.setResultMessage("TestWorked");
            return resp;
        };
        instance.setReplayHandler(replayHandler);
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.REPLAY.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        instance.onInstruction(inst);

        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 0);
        assertEquals("TestWorked", resp.getResultMessage());
    }

    @Test
    public void testOnThrownExceptionReplayMessageInstruction() {
        Instruction inst = new Instruction();
        ReplayHandler replayHandler = (ReplayRequest request) -> {
            throw new RuntimeException("TestException");
        };
        instance.setReplayHandler(replayHandler);
        Request req = new Request();
        req.setCommand(Command.REPLAY.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        instance.onInstruction(inst);

        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 2);
        assertEquals("Error while executing replay: TestException", resp.getResultMessage());
        assertEquals("java.lang.RuntimeException: TestException", inst.getResponseParameterByName("Exception"));
    }

    @Test
    public void testHasNoProcessModel() {
        assertFalse(instance.hasProcessModel(new com.im.njams.sdk.common.Path("PROCESSES")));
    }

    @Test
    public void testNoProcessModelForNullPath() {
        assertFalse(instance.hasProcessModel(null));
    }

    @Test
    public void testHasProcessModel() {
        instance.createProcess(new com.im.njams.sdk.common.Path("PROCESSES"));
        assertTrue(instance.hasProcessModel(new com.im.njams.sdk.common.Path("PROCESSES")));
    }

    @Test
    public void setDataMaskingViaSettings() {
        DataMasking.removePatterns();

        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_ENABLED, "true");
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX + "MaskAll", ".*");
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);

        Njams njams = new Njams(Path.of("TestPath"), "1.0.0", "SDK", settings);
        njams.start();

        assertEquals("*****", DataMasking.maskString("Hello"));
    }

    @Test
    public void disableDataMaskingViaSettings() {
        DataMasking.removePatterns();
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_ENABLED, "false");
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX, ".*");
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);

        Njams njams = new Njams(Path.of("TestPath"), "1.0.0", "SDK", settings);
        njams.start();

        assertEquals("Hello", DataMasking.maskString("Hello"));
    }

    @Test
    public void disableDataMaskingDisablesAllDataMasking() {
        DataMasking.removePatterns();

        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_ENABLED, "false");
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX, ".*");
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);

        Njams njams = new Njams(Path.of("TestPath"), "1.0.0", "SDK", settings);

        List<String> dataMaskingStrings = new ArrayList<>();
        dataMaskingStrings.add("Hello");
        njams.getConfiguration().setDataMasking(dataMaskingStrings);
        njams.start();

        assertEquals("Hello", DataMasking.maskString("Hello"));
    }

    @Test
    public void defaultLayouter_isCommonBfsModelLayouter() {
        Settings settings = TestSender.getSettings();
        Njams njams = new Njams(Path.of("TEST"), "1.0", "TEST", settings);
        assertTrue("Default layouter must be CommonBfsModelLayouter",
            njams.getProcessModelLayouter() instanceof CommonBfsModelLayouter);
    }

    @Test
    public void enableDataMaskingWithoutRegex() {
        DataMasking.removePatterns();
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_ENABLED, "true");
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);

        Njams njams = new Njams(Path.of("TestPath"), "1.0.0", "SDK", settings);

        njams.start();

        assertEquals("Hello", DataMasking.maskString("Hello"));
    }

    @Test
    public void serializeWithSizeLimitForwardsLimitToRegisteredSerializer() {
        final int[] capturedLimit = {-1};
        instance.addSerializer(String.class, (value, sizeLimit) -> {
            capturedLimit[0] = sizeLimit;
            return new com.im.njams.sdk.serializer.SerializerResult(value, false);
        });

        String result = instance.serialize("hello", 7);
        assertEquals("hello", result);
        assertEquals(7, capturedLimit[0]);
    }

    @Test
    public void serializeWithoutSizeLimitStillUsesMaxValue() {
        final int[] capturedLimit = {-1};
        instance.addSerializer(String.class, (value, sizeLimit) -> {
            capturedLimit[0] = sizeLimit;
            return new com.im.njams.sdk.serializer.SerializerResult(value, false);
        });

        instance.serialize("hello");
        assertEquals(Integer.MAX_VALUE, capturedLimit[0]);
    }

    @Test
    public void sendProjectMessage_propagatesGlobalVariables() throws InterruptedException {
        Njams njams = new Njams(Path.of("TEST"), "1.0", "TEST", TestSender.getSettings());
        try {
            Map<String, String> vars = new HashMap<>();
            vars.put("Connections/Queue", "queue-value");
            njams.addGlobalVariables(vars);
            njams.start();

            // Set the capturing mock only after start(), so we capture our explicit message, not the startup one.
            CapturingSender capturing = new CapturingSender();
            TestSender.setSenderMock(capturing);
            njams.sendProjectMessage();

            ProjectMessage sent = capturing.awaitProjectMessage();
            assertNotNull("A project message must have been sent", sent);
            assertEquals("queue-value", sent.getGlobalVariables().get("Connections/Queue"));
        } finally {
            if (njams.isStarted()) {
                njams.stop();
            }
            TestSender.setSenderMock(null);
        }
    }

    @Test
    public void setGlobalVariablesPattern_acceptsValidPatternAndIsReturnedByGetter() {
        String pattern = "(?<full>%%(?<name>[^%]+)%%)";
        instance.setGlobalVariablesPattern(pattern);
        assertEquals(pattern, instance.getGlobalVariablesPattern());
    }

    @Test
    public void setGlobalVariablesPattern_acceptsPatternWithOptionalDefaultGroup() {
        String pattern = "(?<full>\\{\\{\\??(?<name>(?:(?:sys|env):)?[^}:]+)(?::(?<default>[^}]+))?\\}\\})";
        instance.setGlobalVariablesPattern(pattern);
        assertEquals(pattern, instance.getGlobalVariablesPattern());
    }

    @Test
    public void setGlobalVariablesPattern_nullClearsThePattern() {
        instance.setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)");
        instance.setGlobalVariablesPattern(null);
        assertNull(instance.getGlobalVariablesPattern());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setGlobalVariablesPattern_rejectsInvalidRegex() {
        // Unbalanced group -> not a compilable regex.
        instance.setGlobalVariablesPattern("(?<full>(?<name>[^%]+");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setGlobalVariablesPattern_rejectsMissingNameGroup() {
        instance.setGlobalVariablesPattern("(?<full>%%[^%]+%%)");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setGlobalVariablesPattern_rejectsMissingFullGroup() {
        instance.setGlobalVariablesPattern("%%(?<name>[^%]+)%%");
    }

    @Test
    public void sendProjectMessage_propagatesGlobalVariablesPattern() throws InterruptedException {
        String pattern = "(?<full>%%(?<name>[^%]+)%%)";
        Njams njams = new Njams(Path.of("TEST"), "1.0", "TEST", TestSender.getSettings());
        try {
            njams.setGlobalVariablesPattern(pattern);
            njams.start();

            CapturingSender capturing = new CapturingSender();
            TestSender.setSenderMock(capturing);
            njams.sendProjectMessage();

            ProjectMessage sent = capturing.awaitProjectMessage();
            assertNotNull("A project message must have been sent", sent);
            assertEquals(pattern, sent.getGlobalVariablesPattern());
        } finally {
            if (njams.isStarted()) {
                njams.stop();
            }
            TestSender.setSenderMock(null);
        }
    }

    /**
     * Captures the last {@link ProjectMessage} passed to the sender, bypassing the connection-loss dispatch loop.
     * Sending happens asynchronously on the sender thread pool, so callers wait via {@link #awaitProjectMessage()}.
     */
    private static final class CapturingSender extends AbstractSender {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile ProjectMessage lastProjectMessage;

        @Override
        public String getName() {
            return "CAPTURING";
        }

        @Override
        public void send(CommonMessage msg, String clientSessionId) {
            if (msg instanceof ProjectMessage) {
                lastProjectMessage = (ProjectMessage) msg;
                latch.countDown();
            }
        }

        ProjectMessage awaitProjectMessage() throws InterruptedException {
            latch.await(5, TimeUnit.SECONDS);
            return lastProjectMessage;
        }

        @Override
        protected void send(LogMessage msg, String clientSessionId) {
        }

        @Override
        protected void send(ProjectMessage msg, String clientSessionId) {
        }

        @Override
        protected void send(TraceMessage msg, String clientSessionId) {
        }
    }
}
