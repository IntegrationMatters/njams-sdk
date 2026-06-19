package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.Path;

/**
 * Baseline behavior of {@link AbstractReplayHandler}: routing to {@code executeReplay}/{@code testReplay},
 * success and error responses. Locked in before adding {@code processPath} support (SDK-455).
 */
public class AbstractReplayHandlerTest {

    private static final String LOG_ID = "log-123";

    private static ReplayRequest request(Map<String, String> params) {
        final Request request = new Request();
        request.setCommand(Command.REPLAY.commandString());
        request.setParameters(new HashMap<>(params));
        final Instruction instruction = new Instruction();
        instruction.setRequest(request);
        return new ReplayRequest(instruction);
    }

    private static ReplayRequest request(String... keyValuePairs) {
        final Map<String, String> params = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            params.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return request(params);
    }

    /** Records what the abstract template methods received. */
    private static class RecordingHandler extends AbstractReplayHandler {
        String executeName;
        String executeData;
        String testName;
        String testData;
        RuntimeException toThrow;

        @Override
        public String executeReplay(String processName, String startData) throws Exception {
            executeName = processName;
            executeData = startData;
            if (toThrow != null) {
                throw toThrow;
            }
            return LOG_ID;
        }

        @Override
        public void testReplay(String processName, String startData) throws Exception {
            testName = processName;
            testData = startData;
            if (toThrow != null) {
                throw toThrow;
            }
        }
    }

    @Test
    public void executeReplaySuccessReturnsLogIdAndSuccessResponse() {
        final RecordingHandler handler = new RecordingHandler();

        final ReplayResponse response = handler.replay(request("Process", "p1", "Payload", "data", "Test", "false"));

        assertEquals("p1", handler.executeName);
        assertEquals("data", handler.executeData);
        assertNull("testReplay must not be called for a non-test replay", handler.testName);
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());
        assertEquals(LOG_ID, response.getMainLogId());
        assertNotNull(response.getDateTime());
    }

    @Test
    public void testReplayRoutesToTestReplayAndYieldsTestMarkerLogId() {
        final RecordingHandler handler = new RecordingHandler();

        final ReplayResponse response = handler.replay(request("Process", "p1", "Payload", "data", "Test", "true"));

        assertEquals("p1", handler.testName);
        assertEquals("data", handler.testData);
        assertNull("executeReplay must not be called for a test replay", handler.executeName);
        assertEquals(0, response.getResultCode());
        assertEquals("$$test", response.getMainLogId());
    }

    @Test
    public void missingProcessYieldsErrorResponse() {
        final RecordingHandler handler = new RecordingHandler();

        final ReplayResponse response = handler.replay(request("Payload", "data"));

        assertEquals(-1, response.getResultCode());
        assertEquals("n/a", response.getMainLogId());
        assertNotNull(response.getException());
        assertNull("handler must not be invoked without a process", handler.executeName);
    }

    @Test
    public void executeReplayExceptionYieldsErrorResponse() {
        final RecordingHandler handler = new RecordingHandler();
        handler.toThrow = new RuntimeException("boom");

        final ReplayResponse response = handler.replay(request("Process", "p1", "Test", "false"));

        assertEquals(-1, response.getResultCode());
        assertEquals("boom", response.getResultMessage());
        assertEquals("n/a", response.getMainLogId());
        assertTrue(response.getException().contains("boom"));
    }

    /** A legacy handler (overriding only the name-based methods) must still receive the process name. */
    @Test
    public void legacyHandlerReceivesNameWhenProcessPathPresent() {
        final RecordingHandler handler = new RecordingHandler();

        handler.replay(request("Process", "p1", "processPath", ">x>p1>", "Test", "false"));

        assertEquals("p1", handler.executeName);
    }

    /** Falls back to the path's last segment as the name when the server sends only a path. */
    @Test
    public void legacyHandlerReceivesPathNameWhenOnlyProcessPathPresent() {
        final RecordingHandler handler = new RecordingHandler();

        handler.replay(request("processPath", ">x>p1>", "Test", "false"));

        assertEquals("p1", handler.executeName);
    }

    /** Records what the path-aware template methods received. */
    private static class PathRecordingHandler extends AbstractReplayHandler {
        Path executePath;
        String executeName;
        String executeData;
        Path testPath;
        String testName;

        @Override
        public String executeReplay(Path processPath, String processName, String startData) {
            executePath = processPath;
            executeName = processName;
            executeData = startData;
            return LOG_ID;
        }

        @Override
        public void testReplay(Path processPath, String processName, String startData) {
            testPath = processPath;
            testName = processName;
        }
    }

    @Test
    public void pathAwareHandlerReceivesResolvedProcessPathAndName() {
        final PathRecordingHandler handler = new PathRecordingHandler();

        final ReplayResponse response = handler.replay(request("Process", "cross-sum-hash-service",
            "processPath", ">Camel>TestApp>cross-sum-hash-service>", "Payload", "data", "Test", "false"));

        assertNotNull(handler.executePath);
        assertEquals(">Camel>TestApp>cross-sum-hash-service>", handler.executePath.toString());
        assertEquals("cross-sum-hash-service", handler.executeName);
        assertEquals("data", handler.executeData);
        assertEquals(0, response.getResultCode());
        assertEquals(LOG_ID, response.getMainLogId());
    }

    @Test
    public void pathAwareHandlerReceivesNullPathWhenServerSendsOnlyName() {
        final PathRecordingHandler handler = new PathRecordingHandler();

        handler.replay(request("Process", "p1", "Test", "false"));

        assertNull(handler.executePath);
        assertEquals("p1", handler.executeName);
    }

    @Test
    public void pathAwareTestReplayReceivesProcessPath() {
        final PathRecordingHandler handler = new PathRecordingHandler();

        final ReplayResponse response = handler.replay(request("processPath", ">a>b>", "Test", "true"));

        assertNotNull(handler.testPath);
        assertEquals(">a>b>", handler.testPath.toString());
        assertEquals("$$test", response.getMainLogId());
        assertNull("executeReplay must not be called for a test replay", handler.executePath);
    }
}
