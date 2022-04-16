package com.im.njams.sdk;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.PingInstructionListener;
import com.im.njams.sdk.logmessage.NjamsFeatures;
import com.im.njams.sdk.metadata.NjamsMetadata;
import com.im.njams.sdk.metadata.NjamsMetadataFactory;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class PingInstructionListenerTest {

    @Test
    public void ping() {
        final NjamsMetadata testMetadata = createTestMetadata();
        final NjamsFeatures testFeatures = createTestFeatures();

        Response resp = pingToInstanceWith(testMetadata, testFeatures);

        assertPongIsFilledCorrectly(resp);
    }

    private NjamsMetadata createTestMetadata() {
        final Path clientPath = new Path("Test", "Path", "for", "ping");
        final String defaultClientVersion = "1.0.0";
        final String category = "SDK";
        final NjamsMetadata testMetadata = NjamsMetadataFactory.createMetadataWith(clientPath, defaultClientVersion,
            category);
        return testMetadata;
    }

    private NjamsFeatures createTestFeatures() {
        final NjamsFeatures njamsFeatures = new NjamsFeatures();
        njamsFeatures.add(NjamsFeatures.Feature.EXPRESSION_TEST);
        njamsFeatures.add(NjamsFeatures.Feature.REPLAY);
        njamsFeatures.add(NjamsFeatures.Feature.PING);
        njamsFeatures.add(NjamsFeatures.Feature.INJECTION);
        return njamsFeatures;
    }

    private Response pingToInstanceWith(NjamsMetadata testMetadata, NjamsFeatures testFeatures) {
        PingInstructionListener pingInstructionListener = new PingInstructionListener(testMetadata, testFeatures);

        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.PING.commandString());
        inst.setRequest(req);
        pingInstructionListener.onInstruction(inst);
        return inst.getResponse();
    }

    private void assertPongIsFilledCorrectly(Response resp) {
        assertTrue(resp.getResultCode() == 0);
        assertEquals("Pong", resp.getResultMessage());

        final Map<String, String> responseParameters = resp.getParameters();
        assertEquals(">Test>Path>for>ping>", responseParameters.get("clientPath"));
        assertEquals("1.0.0", responseParameters.get("clientVersion"));
        assertNotNull(responseParameters.get("sdkVersion"));
        assertEquals("SDK", responseParameters.get("category"));
        assertEquals("expressionTest,ping,replay,injection", responseParameters.get("features"));

        assertNotNull(responseParameters.get("machine"));
    }
}