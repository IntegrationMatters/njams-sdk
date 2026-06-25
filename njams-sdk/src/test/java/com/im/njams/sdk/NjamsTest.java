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
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.Receiver;
import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.communication.ReplayRequest;
import com.im.njams.sdk.communication.ReplayResponse;
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

    @Test
    public void testStartReturnsFalseWhenReceiverTimesOut() {
        Receiver hangingReceiver = new Receiver() {
            @Override public String getName() { return "HangingReceiver"; }
            @Override public void init(ClientSettings settings) {}
            @Override public void setNjams(Njams njams) {}
            @Override public void onInstruction(Instruction i) {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void startWithTimeout(long timeoutMs) {
                throw new NjamsSdkRuntimeException("Simulated startup timeout");
            }
        };
        TestReceiver.setReceiverMock(hangingReceiver);
        try {
            boolean result = instance.start();
            assertFalse("start() must return false when receiver times out", result);
            assertFalse("SDK must not be started after receiver timeout", instance.isStarted());
        } finally {
            TestReceiver.setReceiverMock(null);
        }
    }

    @Test
    public void testStartReturnsFalseWhenReceiverThrows() {
        Receiver failingReceiver = new Receiver() {
            @Override public String getName() { return "FailingReceiver"; }
            @Override public void init(ClientSettings settings) {}
            @Override public void setNjams(Njams njams) {}
            @Override public void onInstruction(Instruction i) {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void startWithTimeout(long timeoutMs) {
                throw new NjamsSdkRuntimeException("Simulated connect error");
            }
        };
        TestReceiver.setReceiverMock(failingReceiver);
        try {
            boolean result = instance.start();
            assertFalse("start() must return false when receiver throws on connect", result);
            assertFalse("SDK must not be started when receiver connect fails", instance.isStarted());
        } finally {
            TestReceiver.setReceiverMock(null);
        }
    }

    @Test
    public void testBeginConnectBeforeStartDoesNotBreakStart() {
        // The connection is pre-started at construction time; start() must still complete normally
        // and must drive the connection through startWithTimeout (not the plain start()).
        final boolean[] startWithTimeoutCalled = {false};
        Receiver okReceiver = new Receiver() {
            @Override public String getName() { return "OkReceiver"; }
            @Override public void init(ClientSettings settings) {}
            @Override public void setNjams(Njams njams) {}
            @Override public void onInstruction(Instruction i) {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void startWithTimeout(long timeoutMs) {
                startWithTimeoutCalled[0] = true;
            }
        };
        TestReceiver.setReceiverMock(okReceiver);
        try {
            boolean result = instance.start();
            assertTrue("start() must succeed when the receiver connects", result);
            assertTrue("startReceiver() must use startWithTimeout", startWithTimeoutCalled[0]);
            assertTrue(instance.isStarted());
        } finally {
            if (instance.isStarted()) {
                instance.stop();
            }
            TestReceiver.setReceiverMock(null);
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
