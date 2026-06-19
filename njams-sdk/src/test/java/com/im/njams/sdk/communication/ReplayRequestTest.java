package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.Path;

/**
 * Baseline parsing behavior of {@link ReplayRequest}, locked in before adding {@code processPath}
 * support (SDK-455).
 */
public class ReplayRequestTest {

    private static ReplayRequest request(Map<String, String> params) {
        final Request request = new Request();
        request.setCommand(Command.REPLAY.commandString());
        request.setParameters(new HashMap<>(params));
        final Instruction instruction = new Instruction();
        instruction.setRequest(request);
        return new ReplayRequest(instruction);
    }

    @Test
    public void parsesKnownParameters() {
        final Map<String, String> params = new HashMap<>();
        params.put("Process", "p1");
        params.put("Payload", "data");
        params.put("Test", "true");
        params.put("Deeptrace", "true");

        final ReplayRequest replayRequest = request(params);

        assertEquals("p1", replayRequest.getProcess());
        assertEquals("data", replayRequest.getPayload());
        assertTrue(replayRequest.getTest());
        assertTrue(replayRequest.getDeepTrace());
    }

    @Test
    public void booleanFlagsDefaultToFalseWhenAbsent() {
        final ReplayRequest replayRequest = request(new HashMap<>());

        assertFalse(replayRequest.getTest());
        assertFalse(replayRequest.getDeepTrace());
    }

    @Test
    public void allParametersAreRetained() {
        final Map<String, String> params = new HashMap<>();
        params.put("Process", "p1");
        params.put("ServerPluginVersion", "6.2.0");

        final ReplayRequest replayRequest = request(params);

        assertEquals("p1", replayRequest.getParameters().get("Process"));
        assertEquals("6.2.0", replayRequest.getParameters().get("ServerPluginVersion"));
    }

    @Test
    public void parsesProcessPathIntoPath() {
        final Map<String, String> params = new HashMap<>();
        params.put("Process", "cross-sum-hash-service");
        params.put("processPath", ">Camel>TestApp>TestContext>cross-sum-hash-service>");

        final Path path = request(params).getProcessPath();

        assertNotNull(path);
        assertEquals(">Camel>TestApp>TestContext>cross-sum-hash-service>", path.toString());
        assertEquals("cross-sum-hash-service", path.getName());
    }

    @Test
    public void processPathIsNullWhenAbsent() {
        assertNull(request(Collections.singletonMap("Process", "p1")).getProcessPath());
    }

    @Test
    public void processPathIsNullWhenBlank() {
        assertNull(request(Collections.singletonMap("processPath", "   ")).getProcessPath());
    }

    @Test
    public void processPathParameterIsCaseInsensitive() {
        final Path path = request(Collections.singletonMap("ProcessPath", ">a>b>")).getProcessPath();

        assertNotNull(path);
        assertEquals(">a>b>", path.toString());
    }
}
