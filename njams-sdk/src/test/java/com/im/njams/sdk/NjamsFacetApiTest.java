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
        assertSame(njams.model(), njams.model());
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
        njams.model().addImage("late.image", "images/root.png");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetTreeElementTypeThrowsAfterStart() {
        njams.start();
        njams.model().setTreeElementType(Path.of("SDK4", "TEST"), "custom.type");
    }

    @Test
    public void newProcessCreateIsAllowedAfterStartAndAnnouncable() {
        njams.start();
        com.im.njams.sdk.model.ProcessModel lazy = njams.model().create("LAZY");
        njams.model().announce(lazy); // must not throw
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newAnnounceBeforeStartThrows() {
        com.im.njams.sdk.model.ProcessModel model = njams.model().create("P1");
        njams.model().announce(model);
    }

    // --- processes parity mirrors ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void getProcessModelThrowsWhenAbsent_viaFacet() {
        njams.model().get("MISSING");
    }

    @Test
    public void createProcessRegistersModelUnderAbsolutePath_viaFacet() {
        com.im.njams.sdk.model.ProcessModel created = njams.model().create("P1");
        assertSame(created, njams.model().get("P1"));
        assertEquals(1, njams.model().getAll().size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getProcessModelsIsUnmodifiable_viaFacet() {
        njams.model().create("P1");
        njams.model().getAll().clear();
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void addProcessModelOfForeignInstanceThrows_viaFacet() {
        Njams other = new Njams(Path.of("OTHER"), "1.0", "X", TestReceiver.getSettings());
        com.im.njams.sdk.model.ProcessModel foreign = other.model().create("P1");
        njams.model().add(foreign);
    }

    @Test
    public void addProcessModelIgnoresNull_viaFacet() {
        njams.model().add(null); // must NOT throw
        assertTrue(njams.model().getAll().isEmpty());
    }

    // --- SDK-447: absolute paths and single-segment name convenience ---

    @Test
    public void createByAbsolutePath_isFoundByItsOwnPath() {
        Path absolute = njams.metadata().getClientPath().getOrCreateChild("PROC447");
        com.im.njams.sdk.model.ProcessModel model = njams.model().create(absolute);
        // the model is registered under exactly the given absolute path - no re-rooting
        assertSame(absolute, model.getPath());
        assertTrue(njams.model().has(model.getPath()));
        assertSame(model, njams.model().get(model.getPath()));
    }

    @Test
    public void createByName_isFoundByNameAndByAbsolutePath() {
        com.im.njams.sdk.model.ProcessModel model = njams.model().create("PROC447");
        assertTrue(njams.model().has("PROC447"));
        assertSame(model, njams.model().get("PROC447"));
        assertSame(njams.metadata().getClientPath().getChild("PROC447"), model.getPath());
        assertTrue(njams.model().has(model.getPath()));
    }

    @Test
    public void hasByAbsolutePath_isFalseWhenAbsent() {
        Path absent = njams.metadata().getClientPath().getOrCreateChild("ABSENT447");
        assertFalse(njams.model().has(absent));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void createPathNotUnderClientPath_throws() {
        njams.model().create(Path.of("OUTSIDE", "X"));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setTreeElementTypeForUnknownPathThrows_viaFacet() {
        njams.model().setTreeElementType(Path.of("DOES", "NOT", "EXIST"), "some.type");
    }

    @Test
    public void setTreeElementTypeForClientPathWorks_viaFacet() {
        njams.model().setTreeElementType(Path.of("SDK4", "TEST"), "custom.type");
    }

    @Test
    public void layouterAndDiagramFactoryAreReplaceable_viaFacet() {
        com.im.njams.sdk.model.layout.SimpleProcessModelLayouter layouter =
            new com.im.njams.sdk.model.layout.SimpleProcessModelLayouter();
        njams.model().setLayouter(layouter);
        assertSame(layouter, njams.model().getLayouter());

        com.im.njams.sdk.model.svg.ProcessDiagramFactory factory =
            new com.im.njams.sdk.model.svg.NjamsProcessDiagramFactory(njams);
        njams.model().setDiagramFactory(factory);
        assertSame(factory, njams.model().getDiagramFactory());
    }

    @Test
    public void defaultLayouter_isCommonBfsModelLayouter_viaFacet() {
        assertTrue("Default layouter must be CommonBfsModelLayouter",
            njams.model().getLayouter() instanceof com.im.njams.sdk.model.layout.CommonBfsModelLayouter);
    }

    @Test
    public void sendProjectMessageContainsAddedImage_viaFacet() throws InterruptedException {
        njams.model().addImage("my.image", "images/root.png");
        njams.start();
        CapturingSender capturing = new CapturingSender(msg -> msg.getImages().containsKey("my.image"));
        com.im.njams.sdk.communication.TestSender.setSenderMock(capturing);
        try {
            njams.model().send();
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
        com.im.njams.sdk.model.ProcessModel model = njams.model().create("LAZY");
        CapturingSender capturing = new CapturingSender(msg -> !msg.getProcesses().isEmpty());
        com.im.njams.sdk.communication.TestSender.setSenderMock(capturing);
        try {
            njams.model().announce(model);
            com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage sent =
                capturing.awaitProjectMessage();
            assertNotNull(sent);
            assertEquals(1, sent.getProcesses().size());
        } finally {
            com.im.njams.sdk.communication.TestSender.setSenderMock(null);
        }
    }

    // --- jobs parity ---

    @Test
    public void newJobsApiMatchesLegacyBehavior() {
        njams.start();
        com.im.njams.sdk.model.ProcessModel model = njams.model().create("P1");
        com.im.njams.sdk.logmessage.Job job = model.createJob();
        assertSame(job, njams.jobs().get(job.getJobId()));
        assertEquals(1, njams.jobs().getAll().size());
        njams.jobs().remove(job.getJobId());
        assertNull(njams.jobs().get(job.getJobId()));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newJobsAddBeforeStartThrows() {
        com.im.njams.sdk.model.ProcessModel model = njams.model().create("P1");
        model.createJob(); // createJob registers the job and requires a started instance
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getJobsIsUnmodifiable_viaFacet() {
        njams.start();
        njams.jobs().getAll().clear();
    }

    // --- serializers parity ---

    @Test
    public void serializerHierarchyResolution_viaFacet() {
        final com.im.njams.sdk.serializer.Serializer<java.util.List> listSerializer = (l, sizeLimit) -> "list";
        njams.serializers().add(java.util.ArrayList.class, (a, sizeLimit) -> a.getClass().getSimpleName());
        njams.serializers().add(java.util.List.class, listSerializer);

        // found ArrayList serializer
        assertEquals("ArrayList", njams.serializers().serialize(new java.util.ArrayList<>()));
        // found default string serializer
        assertEquals("{}", njams.serializers().serialize(new HashMap<>()));
        // found list serializer via interface hierarchy
        assertEquals("list", njams.serializers().serialize(new java.util.LinkedList<>()));
    }

    @Test
    public void serializeWithSizeLimitForwardsLimitToRegisteredSerializer_viaFacet() {
        final int[] capturedLimit = { -1 };
        njams.serializers().add(String.class, (value, sizeLimit) -> {
            capturedLimit[0] = sizeLimit;
            return value;
        });
        assertEquals("hello", njams.serializers().serialize("hello", 7));
        assertEquals(7, capturedLimit[0]);
    }

    @Test
    public void serializeWithoutSizeLimitStillUsesMaxValue_viaFacet() {
        final int[] capturedLimit = { -1 };
        njams.serializers().add(String.class, (value, sizeLimit) -> {
            capturedLimit[0] = sizeLimit;
            return value;
        });
        njams.serializers().serialize("hello");
        assertEquals(Integer.MAX_VALUE, capturedLimit[0]);
    }

    @Test
    public void newSerializersApiWorks() {
        njams.serializers().add(String.class, (value, sizeLimit) -> "X" + value);
        assertEquals("Xhello", njams.serializers().serialize("hello"));
        assertNotNull(njams.serializers().remove(String.class));
    }

    // --- commands / configuration / argos parity ---

    @Test
    public void newCommandsApiWorks() {
        com.im.njams.sdk.communication.InstructionListener listener = instruction -> {
        };
        int before = njams.commands().list().size();
        njams.commands().add(listener);
        assertEquals(before + 1, njams.commands().list().size());
        njams.commands().remove(listener);
        assertEquals(before, njams.commands().list().size());
    }

    @Test
    public void getInstructionListenersReturnsACopy_viaFacet() {
        com.im.njams.sdk.communication.InstructionListener listener = instruction -> {
        };
        njams.commands().add(listener);
        njams.commands().list().clear();
        assertTrue(njams.commands().list().contains(listener));
    }

    @Test
    public void newConfigurationApiWorks() {
        assertNotNull(njams.configuration().get());
        assertEquals(com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode.COMPLETE,
            njams.configuration().getLogMode());
        assertFalse(njams.configuration().isExcluded(Path.of("P1")));
    }

    @Test
    public void isExcludedIsFalseByDefaultAndTrueForNull_viaFacet() {
        assertFalse(njams.configuration().isExcluded(Path.of("P1")));
        // null is not selected by the process filter -> reported as excluded
        assertTrue(njams.configuration().isExcluded(null));
    }

    @Test
    public void argosCollectorAddAndRemoveDoNotThrow_viaFacet() {
        com.im.njams.sdk.argos.ArgosMultiCollector<?> collector =
            new com.im.njams.sdk.argos.jvm.JVMCollector(
                new com.im.njams.sdk.argos.ArgosComponent("id", "name", "container", "measurement", "type"));
        njams.argos().add(collector);
        njams.argos().remove(collector);
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
