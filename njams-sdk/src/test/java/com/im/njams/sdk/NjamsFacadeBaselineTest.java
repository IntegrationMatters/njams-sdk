package com.im.njams.sdk;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.argos.ArgosComponent;
import com.im.njams.sdk.argos.ArgosMultiCollector;
import com.im.njams.sdk.argos.jvm.JVMCollector;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.ReplayResponse;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.layout.SimpleProcessModelLayouter;
import com.im.njams.sdk.model.svg.NjamsProcessDiagramFactory;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;

/**
 * Pins the current behavior of all public Njams methods migrated by SDK-359,
 * BEFORE the refactoring. Must pass unchanged against the refactored class.
 */
public class NjamsFacadeBaselineTest {

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
        TestSender.setSenderMock(null);
    }

    /** Builds a legacy relative path for the deprecated process facade methods under test. */
    private static com.im.njams.sdk.common.Path rel(String... parts) {
        return new com.im.njams.sdk.common.Path(parts);
    }

    // --- metadata ---

    @Test
    public void categoryIsUppercased() {
        assertEquals("SDK4", njams.getCategory());
    }

    @Test
    public void clientPathIsReturned() {
        assertEquals(Path.of("SDK4", "TEST"), njams.getClientPath());
    }

    @Test
    public void clientSessionIdAndCommunicationSessionIdAreTheSame() {
        assertNotNull(njams.getClientSessionId());
        assertEquals(njams.getClientSessionId(), njams.getCommunicationSessionId());
    }

    @Test
    public void clientVersionComesFromConstructorWhenNoVersionFile() {
        assertEquals("4.1.1", njams.getClientVersion());
    }

    @Test
    public void sdkVersionIsNeverNull() {
        assertNotNull(njams.getSdkVersion());
    }

    @Test
    public void machineIsNeverNull() {
        assertNotNull(njams.getMachine());
    }

    @Test
    public void runtimeVersionIsSettable() {
        assertNull(njams.getRuntimeVersion());
        njams.setRuntimeVersion("rt-1");
        assertEquals("rt-1", njams.getRuntimeVersion());
    }

    @Test
    public void runtimeVersionConstructorArgumentIsApplied() {
        Njams withRt = new Njams(Path.of("RT"), "1.0", "rt-2.5", "X", TestReceiver.getSettings());
        assertEquals("rt-2.5", withRt.getRuntimeVersion());
    }

    @Test
    public void addGlobalVariablesMergesIntoExisting() {
        Map<String, String> first = new HashMap<>();
        first.put("a", "1");
        njams.addGlobalVariables(first);
        Map<String, String> second = new HashMap<>();
        second.put("b", "2");
        second.put("a", "overwritten");
        njams.addGlobalVariables(second);
        assertEquals("overwritten", njams.getGlobalVariables().get("a"));
        assertEquals("2", njams.getGlobalVariables().get("b"));
    }

    @Test
    public void addGlobalVariablesAfterStartIsLenient() {
        njams.start();
        Map<String, String> late = new HashMap<>();
        late.put("late", "x");
        njams.addGlobalVariables(late); // must NOT throw (pinned lenient behavior)
        assertEquals("x", njams.getGlobalVariables().get("late"));
    }

    @Test
    public void setRuntimeVersionAfterStartIsLenient() {
        njams.start();
        njams.setRuntimeVersion("late-rt"); // must NOT throw
        assertEquals("late-rt", njams.getRuntimeVersion());
    }

    @Test
    public void setGlobalVariablesPatternAfterStartIsLenient() {
        njams.start();
        njams.setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)"); // must NOT throw
        assertNotNull(njams.getGlobalVariablesPattern());
    }

    // --- features / container mode ---

    @Test
    public void inherentFeaturesArePresentByDefault() {
        assertTrue(njams.hasFeature(Njams.Feature.EXPRESSION_TEST));
        assertTrue(njams.hasFeature(Njams.Feature.PING));
        assertTrue(njams.hasFeature(Njams.Feature.COMMANDS_SPLIT));
    }

    @Test
    public void addFeatureIsIdempotent() {
        njams.addFeature(Njams.Feature.INJECTION);
        njams.addFeature(Njams.Feature.INJECTION);
        assertEquals(1, njams.getFeatures().stream()
            .filter(f -> f == Njams.Feature.INJECTION).count());
    }

    @Test
    public void removeFeatureRemoves() {
        njams.addFeature(Njams.Feature.INJECTION);
        njams.removeFeature(Njams.Feature.INJECTION);
        assertFalse(njams.hasFeature(Njams.Feature.INJECTION));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void removingInherentFeatureThrows() {
        njams.removeFeature(Njams.Feature.PING);
    }

    @Test
    public void getFeaturesReturnsACopy() {
        List<Njams.Feature> features = njams.getFeatures();
        features.clear();
        assertTrue(njams.hasFeature(Njams.Feature.PING));
    }

    @Test
    public void addFeatureAfterStartIsLenient() {
        njams.start();
        njams.addFeature(Njams.Feature.INJECTION); // must NOT throw
        assertTrue(njams.hasFeature(Njams.Feature.INJECTION));
    }

    @Test
    public void containerModeIsOnByDefaultAndSettableBeforeStart() {
        assertTrue(njams.isContainerMode());
        njams.setContainerMode(false);
        assertFalse(njams.isContainerMode());
        assertFalse(njams.hasFeature(Njams.Feature.CONTAINER_MODE));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setContainerModeAfterStartThrows() {
        njams.start();
        njams.setContainerMode(false);
    }

    // --- replay handler ---

    @Test
    public void setReplayHandlerTogglesReplayFeature() {
        assertFalse(njams.hasFeature(Njams.Feature.REPLAY));
        njams.setReplayHandler(request -> new ReplayResponse());
        assertTrue(njams.hasFeature(Njams.Feature.REPLAY));
        assertNotNull(njams.getReplayHandler());
        njams.setReplayHandler(null);
        assertFalse(njams.hasFeature(Njams.Feature.REPLAY));
        assertNull(njams.getReplayHandler());
    }

    @Test
    public void setReplayHandlerAfterStartIsLenient() {
        njams.start();
        njams.setReplayHandler(request -> new ReplayResponse()); // must NOT throw
        assertTrue(njams.hasFeature(Njams.Feature.REPLAY));
    }

    // --- process models ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void getProcessModelThrowsWhenAbsent() {
        njams.getProcessModel(rel("MISSING"));
    }

    @Test
    public void createProcessRegistersModelUnderAbsolutePath() {
        ProcessModel created = njams.createProcess(rel("P1"));
        assertSame(created, njams.getProcessModel(rel("P1")));
        assertEquals(1, njams.getProcessModels().size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getProcessModelsIsUnmodifiable() {
        njams.createProcess(rel("P1"));
        njams.getProcessModels().clear();
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void addProcessModelOfForeignInstanceThrows() {
        Njams other = new Njams(Path.of("OTHER"), "1.0", "X", TestReceiver.getSettings());
        ProcessModel foreign = other.createProcess(rel("P1"));
        njams.addProcessModel(foreign);
    }

    @Test
    public void addProcessModelIgnoresNull() {
        njams.addProcessModel(null); // must NOT throw
        assertTrue(njams.getProcessModels().isEmpty());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setTreeElementTypeForUnknownPathThrows() {
        njams.setTreeElementType(Path.of("DOES", "NOT", "EXIST"), "some.type");
    }

    @Test
    public void setTreeElementTypeForClientPathWorks() {
        njams.setTreeElementType(Path.of("SDK4", "TEST"), "custom.type"); // client path exists in tree
    }

    @Test
    public void layouterAndDiagramFactoryAreReplaceable() {
        SimpleProcessModelLayouter layouter = new SimpleProcessModelLayouter();
        njams.setProcessModelLayouter(layouter);
        assertSame(layouter, njams.getProcessModelLayouter());

        ProcessDiagramFactory factory = new NjamsProcessDiagramFactory(njams);
        njams.setProcessDiagramFactory(factory);
        assertSame(factory, njams.getProcessDiagramFactory());
    }

    @Test
    public void addImageAfterStartIsLenient() {
        njams.start();
        njams.addImage("late.image", "images/root.png"); // must NOT throw
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void sendAdditionalProcessBeforeStartThrows() {
        ProcessModel model = njams.createProcess(rel("P1"));
        njams.sendAdditionalProcess(model);
    }

    @Test
    public void sendAdditionalProcessSendsProjectMessageWithThatProcess() throws InterruptedException {
        njams.start();
        ProcessModel model = njams.createProcess(rel("LAZY"));
        // The startup project message (0 processes) may still arrive asynchronously after the
        // mock was installed - capture only the message that carries the additional process.
        CapturingSender capturing = new CapturingSender(msg -> !msg.getProcesses().isEmpty());
        TestSender.setSenderMock(capturing);
        njams.sendAdditionalProcess(model);
        ProjectMessage sent = capturing.awaitProjectMessage();
        assertNotNull(sent);
        assertEquals(1, sent.getProcesses().size());
    }

    @Test
    public void sendProjectMessageContainsAddedImage() throws InterruptedException {
        njams.addImage("my.image", "images/root.png");
        njams.start();
        CapturingSender capturing = new CapturingSender();
        TestSender.setSenderMock(capturing);
        njams.sendProjectMessage();
        ProjectMessage sent = capturing.awaitProjectMessage();
        assertNotNull(sent);
        assertTrue(sent.getImages().containsKey("my.image"));
    }

    // --- jobs ---

    @Test
    public void jobLifecycleAfterStart() {
        njams.start();
        ProcessModel model = njams.createProcess(rel("P1"));
        Job job = model.createJob();
        assertSame(job, njams.getJobById(job.getJobId()));
        assertEquals(1, njams.getJobs().size());
        njams.removeJob(job.getJobId());
        assertNull(njams.getJobById(job.getJobId()));
        assertTrue(njams.getJobs().isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getJobsIsUnmodifiable() {
        njams.start();
        njams.getJobs().clear();
    }

    // --- instruction listeners / commands ---

    @Test
    public void instructionListenersAreAddableAndRemovable() {
        InstructionListener listener = instruction -> {
        };
        int before = njams.getInstructionListeners().size();
        njams.addInstructionListener(listener);
        assertEquals(before + 1, njams.getInstructionListeners().size());
        njams.removeInstructionListener(listener);
        assertEquals(before, njams.getInstructionListeners().size());
    }

    @Test
    public void getInstructionListenersReturnsACopy() {
        InstructionListener listener = instruction -> {
        };
        njams.addInstructionListener(listener);
        njams.getInstructionListeners().clear();
        assertTrue(njams.getInstructionListeners().contains(listener));
    }

    @Test
    public void pingInstructionIsAnswered() {
        Instruction inst = instructionFor(Command.PING);
        njams.onInstruction(inst);
        Response resp = inst.getResponse();
        assertEquals(0, resp.getResultCode());
        assertEquals("Pong", resp.getResultMessage());
        assertEquals(njams.getClientSessionId(), resp.getParameters().get("clientId"));
        assertEquals(njams.getCategory(), resp.getParameters().get("category"));
    }

    @Test
    public void getRequestHandlerInstructionReturnsClientId() {
        Instruction inst = instructionFor(Command.GET_REQUEST_HANDLER);
        njams.onInstruction(inst);
        assertEquals(0, inst.getResponse().getResultCode());
        assertEquals(njams.getClientSessionId(), inst.getResponseParameterByName("clientId"));
    }

    @Test
    public void unsupportedCommandIsRejected() {
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand("noSuchCommand");
        inst.setRequest(req);
        njams.onInstruction(inst);
        assertEquals(1, inst.getResponse().getResultCode());
    }

    // --- configuration ---

    @Test
    public void logModeDefaultsToComplete() {
        assertEquals(LogMode.COMPLETE, njams.getLogMode());
    }

    @Test
    public void isExcludedIsFalseByDefaultAndTrueForNull() {
        assertFalse(njams.isExcluded(Path.of("P1")));
        // null is not selected by the process filter -> reported as excluded
        assertTrue(njams.isExcluded(null));
    }

    @Test
    public void configurationIsNeverNull() {
        assertNotNull(njams.getConfiguration());
    }

    // --- argos ---

    @Test
    public void argosCollectorAddAndRemoveDoNotThrow() {
        ArgosMultiCollector<?> collector =
            new JVMCollector(new ArgosComponent("id", "name", "container", "measurement", "type"));
        njams.addArgosCollector(collector);
        njams.removeArgosCollector(collector);
    }

    // --- serializers (gap found by coverage check; add/find/serialize covered by NjamsTest) ---

    @Test
    public void removeSerializerReturnsTheRegisteredOneAndRestoresDefault() {
        com.im.njams.sdk.serializer.Serializer<String> custom = (value, sizeLimit) -> "custom:" + value;
        njams.addSerializer(String.class, custom);
        assertEquals("custom:x", njams.serialize("x"));
        assertSame(custom, njams.removeSerializer(String.class));
        assertEquals("x", njams.serialize("x")); // default string serializer again
        assertNull(njams.removeSerializer(String.class)); // nothing registered anymore
        assertNull(njams.removeSerializer(null)); // null key is tolerated
    }

    // --- equals / hashCode ---

    @Test
    public void equalsAndHashCodeAreBasedOnClientPath() {
        Njams samePath = new Njams(Path.of("SDK4", "TEST"), "9.9", "other", TestReceiver.getSettings());
        Njams otherPath = new Njams(Path.of("OTHER"), "4.1.1", "sdk4", TestReceiver.getSettings());
        assertEquals(njams, samePath);
        assertEquals(njams.hashCode(), samePath.hashCode());
        assertNotEquals(njams, otherPath);
        assertNotEquals(njams, null);
        assertNotEquals(njams, "not an Njams");
    }

    // --- sender ---

    @Test
    public void getSenderReturnsNonNullAndIsCached() {
        assertNotNull(njams.getSender());
        assertSame(njams.getSender(), njams.getSender());
    }

    // --- helpers ---

    private static Instruction instructionFor(Command command) {
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(command.commandString());
        inst.setRequest(req);
        return inst;
    }

    /** Same pattern as NjamsTest.CapturingSender, extended by a capture-predicate. */
    private static final class CapturingSender extends AbstractSender {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final java.util.function.Predicate<ProjectMessage> expected;
        private volatile ProjectMessage lastProjectMessage;

        CapturingSender() {
            this(msg -> true);
        }

        CapturingSender(java.util.function.Predicate<ProjectMessage> expected) {
            this.expected = expected;
        }

        @Override
        public String getName() {
            return "CAPTURING";
        }

        @Override
        public void send(CommonMessage msg, String clientSessionId) {
            if (msg instanceof ProjectMessage && expected.test((ProjectMessage) msg)) {
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
