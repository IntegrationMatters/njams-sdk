package com.im.njams.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.communication.ReplayRequest;
import com.im.njams.sdk.communication.ReplayResponse;

/**
 * Tests for {@link NjamsReplay#handleReplayRequest(Instruction)} covering the behaviour that must be
 * preserved (response, replay marker, non-payload parameters) and the SDK-422 behaviour of stripping
 * the large replay start-data ({@code Payload}) from the request before it is returned in the reply.
 */
public class NjamsReplayTest {

    private static final String LOG_ID = "log-123";
    private static final String LARGE_PAYLOAD = "x-large-start-data-payload";

    private NjamsFeatures features;
    private NjamsJobs jobs;
    private NjamsReplay replay;

    @Before
    public void setUp() {
        features = mock(NjamsFeatures.class);
        jobs = mock(NjamsJobs.class);
        // lifecycle is only used by setHandler(), not by handleReplayRequest()/setHandlerInternal()
        replay = new NjamsReplay(null, features, jobs);
    }

    private static Instruction replayInstruction(Map<String, String> params) {
        final Request request = new Request();
        request.setCommand(Command.REPLAY.commandString());
        request.setParameters(new HashMap<>(params));
        final Instruction instruction = new Instruction();
        instruction.setRequest(request);
        return instruction;
    }

    private static ReplayHandler successHandler() {
        return request -> {
            final ReplayResponse response = new ReplayResponse();
            response.setResultCode(0);
            response.setResultMessage("Success");
            response.setMainLogId(LOG_ID);
            return response;
        };
    }

    @Test
    public void successfulReplayKeepsResponseAndSetsMarker() {
        final Map<String, String> params = new HashMap<>();
        params.put("Process", "p1");
        params.put("Payload", LARGE_PAYLOAD);
        params.put("Test", "false");
        params.put("Deeptrace", "true");
        final Instruction instruction = replayInstruction(params);
        replay.setHandlerInternal(successHandler());

        replay.handleReplayRequest(instruction);

        assertEquals(0, instruction.getResponse().getResultCode());
        assertEquals("Success", instruction.getResponse().getResultMessage());
        assertEquals(LOG_ID, instruction.getResponseParameterByName("MainLogId"));
        verify(jobs).setReplayMarker(LOG_ID, true);
    }

    @Test
    public void successfulReplayStripsPayloadButKeepsOtherParameters() {
        final Map<String, String> params = new HashMap<>();
        params.put("Process", "p1");
        params.put("Payload", LARGE_PAYLOAD);
        params.put("Test", "false");
        params.put("Deeptrace", "true");
        final Instruction instruction = replayInstruction(params);
        replay.setHandlerInternal(successHandler());

        replay.handleReplayRequest(instruction);

        assertNull("Payload must be removed from the request",
            instruction.getRequestParameterByName("Payload"));
        assertEquals("p1", instruction.getRequestParameterByName("Process"));
        assertEquals("false", instruction.getRequestParameterByName("Test"));
        assertEquals("true", instruction.getRequestParameterByName("Deeptrace"));
    }

    @Test
    public void payloadIsStrippedCaseInsensitively() {
        final Map<String, String> params = new HashMap<>();
        params.put("Process", "p1");
        params.put("payload", LARGE_PAYLOAD);
        final Instruction instruction = replayInstruction(params);
        replay.setHandlerInternal(successHandler());

        replay.handleReplayRequest(instruction);

        assertNull("payload (any case) must be removed from the request",
            instruction.getRequestParameterByName("Payload"));
        assertFalse("no payload key in any case must remain",
            instruction.getRequest().getParameters().keySet().stream()
                .anyMatch(k -> k.equalsIgnoreCase("Payload")));
    }

    @Test
    public void testReplayStripsPayloadAndSkipsMarker() {
        final Map<String, String> params = new HashMap<>();
        params.put("Process", "p1");
        params.put("Payload", LARGE_PAYLOAD);
        params.put("Test", "true");
        final Instruction instruction = replayInstruction(params);
        replay.setHandlerInternal(successHandler());

        replay.handleReplayRequest(instruction);

        assertNull(instruction.getRequestParameterByName("Payload"));
        verify(jobs, never()).setReplayMarker(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    public void replayHandlerExceptionSetsErrorResponseAndStripsPayload() {
        final Map<String, String> params = new HashMap<>();
        params.put("Process", "p1");
        params.put("Payload", LARGE_PAYLOAD);
        final Instruction instruction = replayInstruction(params);
        replay.setHandlerInternal(request -> {
            throw new RuntimeException("boom");
        });

        replay.handleReplayRequest(instruction);

        assertEquals(2, instruction.getResponse().getResultCode());
        assertTrue(instruction.getResponse().getResultMessage().contains("boom"));
        assertNull("Payload must be stripped even when the replay fails",
            instruction.getRequestParameterByName("Payload"));
    }

    @Test
    public void noHandlerSetsErrorResponseAndStripsPayload() {
        final Map<String, String> params = new HashMap<>();
        params.put("Process", "p1");
        params.put("Payload", LARGE_PAYLOAD);
        final Instruction instruction = replayInstruction(params);
        // no handler registered

        replay.handleReplayRequest(instruction);

        assertEquals(1, instruction.getResponse().getResultCode());
        assertNull("Payload must be stripped even without a replay handler",
            instruction.getRequestParameterByName("Payload"));
    }
}
