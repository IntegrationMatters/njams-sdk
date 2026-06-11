package com.im.njams.sdk;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.TestReceiver;

/**
 * Tests the new facet API of SDK-359: accessor identity, phase guards, behavioral parity
 * with the legacy API (mirror tests suffixed _viaFacet), and the lenient behavior of the
 * deprecated legacy methods.
 */
public class NjamsFacetApiTest {

    private Njams njams;

    @Before
    public void setUp() {
        njams = new Njams(Path.of("SDK4", "TEST"), "4.1.1", "sdk4", TestReceiver.getSettings());
    }

    @After
    public void tearDown() {
        if (njams.isStarted()) {
            njams.stop();
        }
    }

    @Test
    public void accessorsReturnTheSameInstanceEveryTime() {
        assertSame(njams.metadata(), njams.metadata());
        assertSame(njams.jobs(), njams.jobs());
        assertSame(njams.processes(), njams.processes());
        assertSame(njams.features(), njams.features());
        assertSame(njams.serializers(), njams.serializers());
        assertSame(njams.replay(), njams.replay());
        assertSame(njams.commands(), njams.commands());
        assertSame(njams.argos(), njams.argos());
        assertSame(njams.configuration(), njams.configuration());
    }

    // --- metadata guards ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newAddGlobalVariablesThrowsAfterStart() {
        njams.start();
        Map<String, String> vars = new HashMap<>();
        vars.put("late", "x");
        njams.metadata().addGlobalVariables(vars);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetGlobalVariablesPatternThrowsAfterStart() {
        njams.start();
        njams.metadata().setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetRuntimeVersionThrowsAfterStart() {
        njams.start();
        njams.metadata().setRuntimeVersion("late");
    }

    @Test
    public void newMetadataMutatorsWorkBeforeStart() {
        njams.metadata().setRuntimeVersion("rt");
        Map<String, String> vars = new HashMap<>();
        vars.put("a", "1");
        njams.metadata().addGlobalVariables(vars);
        njams.metadata().setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)");
        assertEquals("rt", njams.metadata().getRuntimeVersion());
        assertEquals("1", njams.metadata().getGlobalVariables().get("a"));
    }

    @Test
    public void metadataMutatorsAreChainable() {
        Map<String, String> vars = new HashMap<>();
        vars.put("a", "1");
        NjamsMetadata result = njams.metadata()
            .setRuntimeVersion("rt")
            .addGlobalVariables(vars)
            .setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)");
        assertSame(njams.metadata(), result);
        assertEquals("rt", njams.metadata().getRuntimeVersion());
        assertEquals("1", njams.metadata().getGlobalVariables().get("a"));
    }

    @Test
    public void deprecatedMetadataMutatorsStayLenientAfterStart() {
        njams.start();
        njams.setRuntimeVersion("late"); // WARN, no throw
        Map<String, String> vars = new HashMap<>();
        vars.put("late", "x");
        njams.addGlobalVariables(vars); // WARN, no throw
        assertEquals("late", njams.getRuntimeVersion());
        assertEquals("x", njams.getGlobalVariables().get("late"));
    }

    // --- metadata parity mirrors ---

    @Test
    public void categoryIsUppercased_viaFacet() {
        assertEquals("SDK4", njams.metadata().getCategory());
    }

    @Test
    public void clientPathIsReturned_viaFacet() {
        assertEquals(Path.of("SDK4", "TEST"), njams.metadata().getClientPath());
    }

    @Test
    public void clientVersionComesFromConstructorWhenNoVersionFile_viaFacet() {
        assertEquals("4.1.1", njams.metadata().getClientVersion());
    }

    @Test
    public void sdkVersionIsNeverNull_viaFacet() {
        assertNotNull(njams.metadata().getSdkVersion());
    }

    @Test
    public void machineIsNeverNull_viaFacet() {
        assertNotNull(njams.metadata().getMachine());
    }

    @Test
    public void runtimeVersionIsSettable_viaFacet() {
        assertNull(njams.metadata().getRuntimeVersion());
        njams.metadata().setRuntimeVersion("rt-1");
        assertEquals("rt-1", njams.metadata().getRuntimeVersion());
    }

    @Test
    public void addGlobalVariablesMergesIntoExisting_viaFacet() {
        Map<String, String> first = new HashMap<>();
        first.put("a", "1");
        njams.metadata().addGlobalVariables(first);
        Map<String, String> second = new HashMap<>();
        second.put("b", "2");
        second.put("a", "overwritten");
        njams.metadata().addGlobalVariables(second);
        assertEquals("overwritten", njams.metadata().getGlobalVariables().get("a"));
        assertEquals("2", njams.metadata().getGlobalVariables().get("b"));
    }

    @Test
    public void clientSessionIdMatchesBothLegacyGetters_viaFacet() {
        // pins the intentional unification of getClientSessionId()/getCommunicationSessionId()
        assertNotNull(njams.metadata().getClientSessionId());
        assertEquals(njams.metadata().getClientSessionId(), njams.getClientSessionId());
        assertEquals(njams.metadata().getClientSessionId(), njams.getCommunicationSessionId());
    }

    @Test
    public void setGlobalVariablesPattern_acceptsValidPatternAndIsReturnedByGetter_viaFacet() {
        String pattern = "(?<full>%%(?<name>[^%]+)%%)";
        njams.metadata().setGlobalVariablesPattern(pattern);
        assertEquals(pattern, njams.metadata().getGlobalVariablesPattern());
    }

    @Test
    public void setGlobalVariablesPattern_acceptsPatternWithOptionalDefaultGroup_viaFacet() {
        String pattern = "(?<full>\\{\\{\\??(?<name>(?:(?:sys|env):)?[^}:]+)(?::(?<default>[^}]+))?\\}\\})";
        njams.metadata().setGlobalVariablesPattern(pattern);
        assertEquals(pattern, njams.metadata().getGlobalVariablesPattern());
    }

    @Test
    public void setGlobalVariablesPattern_nullClearsThePattern_viaFacet() {
        njams.metadata().setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)");
        njams.metadata().setGlobalVariablesPattern(null);
        assertNull(njams.metadata().getGlobalVariablesPattern());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setGlobalVariablesPattern_rejectsInvalidRegex_viaFacet() {
        njams.metadata().setGlobalVariablesPattern("(?<full>(?<name>[^%]+");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setGlobalVariablesPattern_rejectsMissingNameGroup_viaFacet() {
        njams.metadata().setGlobalVariablesPattern("(?<full>%%[^%]+%%)");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setGlobalVariablesPattern_rejectsMissingFullGroup_viaFacet() {
        njams.metadata().setGlobalVariablesPattern("%%(?<name>[^%]+)%%");
    }

    // --- features guards ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newFeatureAddThrowsAfterStart() {
        njams.start();
        njams.features().add(Njams.Feature.INJECTION);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newFeatureRemoveThrowsAfterStart() {
        njams.features().add(Njams.Feature.INJECTION);
        njams.start();
        njams.features().remove(Njams.Feature.INJECTION);
    }

    @Test
    public void deprecatedFeatureAddStaysLenientAfterStart() {
        njams.start();
        njams.addFeature(Njams.Feature.INJECTION); // WARN, no throw
        assertTrue(njams.hasFeature(Njams.Feature.INJECTION));
    }

    // --- features parity mirrors ---

    @Test
    public void inherentFeaturesArePresentByDefault_viaFacet() {
        assertTrue(njams.features().has(Njams.Feature.EXPRESSION_TEST));
        assertTrue(njams.features().has(Njams.Feature.PING));
        assertTrue(njams.features().has(Njams.Feature.COMMANDS_SPLIT));
    }

    @Test
    public void addFeatureIsIdempotent_viaFacet() {
        njams.features().add(Njams.Feature.INJECTION);
        njams.features().add(Njams.Feature.INJECTION);
        assertEquals(1, njams.features().list().stream()
            .filter(f -> f == Njams.Feature.INJECTION).count());
    }

    @Test
    public void removeFeatureRemoves_viaFacet() {
        njams.features().add(Njams.Feature.INJECTION);
        njams.features().remove(Njams.Feature.INJECTION);
        assertFalse(njams.features().has(Njams.Feature.INJECTION));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void removingInherentFeatureThrows_viaFacet() {
        njams.features().remove(Njams.Feature.PING);
    }

    @Test
    public void getFeaturesReturnsACopy_viaFacet() {
        njams.features().list().clear();
        assertTrue(njams.features().has(Njams.Feature.PING));
    }

    @Test
    public void containerModeIsOnByDefaultAndSettableBeforeStart_viaFacet() {
        assertTrue(njams.features().isContainerMode());
        njams.features().setContainerMode(false);
        assertFalse(njams.features().isContainerMode());
        assertFalse(njams.features().has(Njams.Feature.CONTAINER_MODE));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setContainerModeAfterStartThrows_viaFacet() {
        njams.start();
        njams.features().setContainerMode(false);
    }

    // --- replay guards + parity ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newReplaySetHandlerThrowsAfterStart() {
        njams.start();
        njams.replay().setHandler(request -> new com.im.njams.sdk.communication.ReplayResponse());
    }

    @Test
    public void newReplaySetHandlerWorksBeforeStart() {
        njams.replay().setHandler(request -> new com.im.njams.sdk.communication.ReplayResponse());
        assertNotNull(njams.replay().getHandler());
        assertTrue(njams.features().has(Njams.Feature.REPLAY));
    }

    @Test
    public void setReplayHandlerTogglesReplayFeature_viaFacet() {
        assertFalse(njams.features().has(Njams.Feature.REPLAY));
        njams.replay().setHandler(request -> new com.im.njams.sdk.communication.ReplayResponse());
        assertTrue(njams.features().has(Njams.Feature.REPLAY));
        assertNotNull(njams.replay().getHandler());
        njams.replay().setHandler(null);
        assertFalse(njams.features().has(Njams.Feature.REPLAY));
        assertNull(njams.replay().getHandler());
    }

    @Test
    public void replayInstructionIsAnswered_viaFacet() {
        njams.replay().setHandler(request -> {
            com.im.njams.sdk.communication.ReplayResponse resp =
                new com.im.njams.sdk.communication.ReplayResponse();
            resp.setResultCode(0);
            resp.setResultMessage("TestWorked");
            return resp;
        });
        com.faizsiegeln.njams.messageformat.v4.command.Instruction inst =
            new com.faizsiegeln.njams.messageformat.v4.command.Instruction();
        com.faizsiegeln.njams.messageformat.v4.command.Request req =
            new com.faizsiegeln.njams.messageformat.v4.command.Request();
        req.setCommand(com.faizsiegeln.njams.messageformat.v4.command.Command.REPLAY.commandString());
        inst.setRequest(req);
        njams.onInstruction(inst);
        assertEquals(0, inst.getResponse().getResultCode());
        assertEquals("TestWorked", inst.getResponse().getResultMessage());
    }

    // --- processes guards ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newAddImageThrowsAfterStart() {
        njams.start();
        njams.processes().addImage("late.image", "images/root.png");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetTreeElementTypeThrowsAfterStart() {
        njams.start();
        njams.processes().setTreeElementType(Path.of("SDK4", "TEST"), "custom.type");
    }

    @Test
    public void newProcessCreateIsAllowedAfterStartAndAnnouncable() {
        njams.start();
        com.im.njams.sdk.model.ProcessModel lazy = njams.processes().create(Path.of("LAZY"));
        njams.processes().announce(lazy); // must not throw
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newAnnounceBeforeStartThrows() {
        com.im.njams.sdk.model.ProcessModel model = njams.processes().create(Path.of("P1"));
        njams.processes().announce(model);
    }

    // --- processes parity mirrors ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void getProcessModelThrowsWhenAbsent_viaFacet() {
        njams.processes().get(Path.of("MISSING"));
    }

    @Test
    public void createProcessRegistersModelUnderAbsolutePath_viaFacet() {
        com.im.njams.sdk.model.ProcessModel created = njams.processes().create(Path.of("P1"));
        assertSame(created, njams.processes().get(Path.of("P1")));
        assertEquals(1, njams.processes().getAll().size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getProcessModelsIsUnmodifiable_viaFacet() {
        njams.processes().create(Path.of("P1"));
        njams.processes().getAll().clear();
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void addProcessModelOfForeignInstanceThrows_viaFacet() {
        Njams other = new Njams(Path.of("OTHER"), "1.0", "X", TestReceiver.getSettings());
        com.im.njams.sdk.model.ProcessModel foreign = other.processes().create(Path.of("P1"));
        njams.processes().add(foreign);
    }

    @Test
    public void addProcessModelIgnoresNull_viaFacet() {
        njams.processes().add(null); // must NOT throw
        assertTrue(njams.processes().getAll().isEmpty());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setTreeElementTypeForUnknownPathThrows_viaFacet() {
        njams.processes().setTreeElementType(Path.of("DOES", "NOT", "EXIST"), "some.type");
    }

    @Test
    public void setTreeElementTypeForClientPathWorks_viaFacet() {
        njams.processes().setTreeElementType(Path.of("SDK4", "TEST"), "custom.type");
    }

    @Test
    public void layouterAndDiagramFactoryAreReplaceable_viaFacet() {
        com.im.njams.sdk.model.layout.SimpleProcessModelLayouter layouter =
            new com.im.njams.sdk.model.layout.SimpleProcessModelLayouter();
        njams.processes().setLayouter(layouter);
        assertSame(layouter, njams.processes().getLayouter());

        com.im.njams.sdk.model.svg.ProcessDiagramFactory factory =
            new com.im.njams.sdk.model.svg.NjamsProcessDiagramFactory(njams);
        njams.processes().setDiagramFactory(factory);
        assertSame(factory, njams.processes().getDiagramFactory());
    }

    @Test
    public void defaultLayouter_isCommonBfsModelLayouter_viaFacet() {
        assertTrue("Default layouter must be CommonBfsModelLayouter",
            njams.processes().getLayouter() instanceof com.im.njams.sdk.model.layout.CommonBfsModelLayouter);
    }

    @Test
    public void sendProjectMessageContainsAddedImage_viaFacet() throws InterruptedException {
        njams.processes().addImage("my.image", "images/root.png");
        njams.start();
        CapturingSender capturing = new CapturingSender(msg -> msg.getImages().containsKey("my.image"));
        com.im.njams.sdk.communication.TestSender.setSenderMock(capturing);
        try {
            njams.processes().send();
            com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage sent =
                capturing.awaitProjectMessage();
            assertNotNull(sent);
            assertTrue(sent.getImages().containsKey("my.image"));
        } finally {
            com.im.njams.sdk.communication.TestSender.setSenderMock(null);
        }
    }

    @Test
    public void sendAdditionalProcessSendsProjectMessageWithThatProcess_viaFacet() throws InterruptedException {
        njams.start();
        com.im.njams.sdk.model.ProcessModel model = njams.processes().create(Path.of("LAZY"));
        CapturingSender capturing = new CapturingSender(msg -> !msg.getProcesses().isEmpty());
        com.im.njams.sdk.communication.TestSender.setSenderMock(capturing);
        try {
            njams.processes().announce(model);
            com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage sent =
                capturing.awaitProjectMessage();
            assertNotNull(sent);
            assertEquals(1, sent.getProcesses().size());
        } finally {
            com.im.njams.sdk.communication.TestSender.setSenderMock(null);
        }
    }

    /** Same pattern as NjamsFacadeBaselineTest.CapturingSender. */
    private static final class CapturingSender extends com.im.njams.sdk.communication.AbstractSender {
        private final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        private final java.util.function.Predicate<
            com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage> expected;
        private volatile com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage lastProjectMessage;

        CapturingSender(java.util.function.Predicate<
            com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage> expected) {
            this.expected = expected;
        }

        @Override
        public String getName() {
            return "CAPTURING";
        }

        @Override
        public void send(com.faizsiegeln.njams.messageformat.v4.common.CommonMessage msg, String clientSessionId) {
            if (msg instanceof com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage
                && expected.test((com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage) msg)) {
                lastProjectMessage = (com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage) msg;
                latch.countDown();
            }
        }

        com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage awaitProjectMessage()
            throws InterruptedException {
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return lastProjectMessage;
        }

        @Override
        protected void send(com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage msg,
            String clientSessionId) {
        }

        @Override
        protected void send(com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage msg,
            String clientSessionId) {
        }

        @Override
        protected void send(com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage msg,
            String clientSessionId) {
        }
    }
}
