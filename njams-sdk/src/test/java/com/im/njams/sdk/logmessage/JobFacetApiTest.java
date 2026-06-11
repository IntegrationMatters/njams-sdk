package com.im.njams.sdk.logmessage;

import static org.junit.Assert.*;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

/**
 * Tests the new job facet API of SDK-448: accessor identity, after-end guards, the inverted
 * activities deviation (new API allows pre-start adds), behavioral parity (mirror tests
 * suffixed _viaFacet), and the lenient behavior of the deprecated legacy methods.
 */
public class JobFacetApiTest {

    private Njams njams;
    private ProcessModel process;

    @Before
    public void setUp() {
        njams = new Njams(Path.of("SDK4", "TEST"), "1.0", "sdk4", TestReceiver.getSettings());
        process = njams.processes().create(Path.of("SDK4", "TEST", "PROCESSES"));
        process.createActivity("act", "Act", null);
        njams.start();
    }

    @After
    public void tearDown() {
        if (njams.isStarted()) {
            njams.stop();
        }
        TestSender.setSenderMock(null);
    }

    private JobImpl createJob() {
        return (JobImpl) process.createJob();
    }

    private JobImpl createStartedJob() {
        JobImpl job = createJob();
        job.start();
        return job;
    }

    private JobImpl createEndedJob() {
        JobImpl job = createStartedJob();
        job.end(true);
        return job;
    }

    private ActivityModel actModel() {
        return process.getActivity("act");
    }

    @Test
    public void accessorsReturnTheSameInstanceEveryTime() {
        Job job = createJob();
        assertSame(job.activities(), job.activities());
        assertSame(job.attributes(), job.attributes());
        assertSame(job.metadata(), job.metadata());
        assertSame(job.properties(), job.properties());
        assertSame(job.tracing(), job.tracing());
    }

    @Test
    public void hasStartedIsOnTheInterface() {
        Job job = createJob();
        assertFalse(job.hasStarted());
        job.start();
        assertTrue(job.hasStarted());
    }

    // --- metadata: after-end guards + chaining ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetCorrelationLogIdThrowsAfterEnd() {
        createEndedJob().metadata().setCorrelationLogId("late");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetParentLogIdThrowsAfterEnd() {
        createEndedJob().metadata().setParentLogId("late");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetExternalLogIdThrowsAfterEnd() {
        createEndedJob().metadata().setExternalLogId("late");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetBusinessServiceThrowsAfterEnd() {
        createEndedJob().metadata().setBusinessService("late");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetBusinessObjectThrowsAfterEnd() {
        createEndedJob().metadata().setBusinessObject("late");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetBusinessStartThrowsAfterEnd() {
        createEndedJob().metadata().setBusinessStart(DateTimeUtility.now());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetBusinessEndThrowsAfterEnd() {
        createEndedJob().metadata().setBusinessEnd(DateTimeUtility.now());
    }

    @Test
    public void deprecatedMetadataSettersStayLenientAfterEnd() {
        JobImpl job = createEndedJob();
        job.setCorrelationLogId("late-corr"); // WARN, no throw
        job.setParentLogId("late-parent"); // WARN, no throw
        assertEquals("late-corr", job.getCorrelationLogId());
        assertEquals("late-parent", job.getParentLogId());
    }

    @Test
    public void metadataMutatorsAreChainable() {
        JobImpl job = createJob();
        LocalDateTime t = DateTimeUtility.now();
        JobMetadata result = job.metadata()
            .setCorrelationLogId("corr")
            .setParentLogId("parent")
            .setExternalLogId("ext")
            .setBusinessService("svc")
            .setBusinessObject("obj")
            .setBusinessStart(t)
            .setBusinessEnd(t);
        assertSame(job.metadata(), result);
        assertEquals("corr", job.metadata().getCorrelationLogId());
        assertEquals("parent", job.metadata().getParentLogId());
    }

    // --- metadata: parity mirrors ---

    @Test
    public void correlationLogIdDefaultsToLogIdAndIsSettable_viaFacet() {
        JobImpl job = createJob();
        assertEquals(job.getLogId(), job.metadata().getCorrelationLogId());
        assertEquals(job.getLogId(), job.metadata().getLogId());
        assertEquals(job.getJobId(), job.metadata().getJobId());
        job.metadata().setCorrelationLogId("corr-1");
        assertEquals("corr-1", job.metadata().getCorrelationLogId());
    }

    @Test
    public void parentAndExternalLogIdAreSettable_viaFacet() {
        JobImpl job = createJob();
        job.metadata().setParentLogId("p-1").setExternalLogId("e-1");
        assertEquals("p-1", job.metadata().getParentLogId());
        assertEquals("e-1", job.metadata().getExternalLogId());
    }

    @Test
    public void businessServiceAndObjectAcceptStringAndPath_viaFacet() {
        JobImpl job = createJob();
        job.metadata().setBusinessService("svc");
        job.metadata().setBusinessObject(Path.of("obj"));
        assertNotNull(job.metadata().getBusinessService());
        assertNotNull(job.metadata().getBusinessObject());
        job.metadata().setBusinessService((Path) null); // null Path ignored, must NOT throw
    }

    @Test
    public void businessStartAndEndAreSettable_viaFacet() {
        JobImpl job = createJob();
        LocalDateTime t = DateTimeUtility.now();
        job.metadata().setBusinessStart(t).setBusinessEnd(t);
        assertEquals(t, job.metadata().getBusinessStart());
        assertEquals(t, job.metadata().getBusinessEnd());
    }

    @Test
    public void overlongFieldValueIsTruncated_viaFacet() {
        JobImpl job = createJob();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < JobImpl.MAX_VALUE_LIMIT + 100; i++) {
            sb.append('x');
        }
        job.metadata().setParentLogId(sb.toString());
        assertEquals(JobImpl.MAX_VALUE_LIMIT - 1, job.metadata().getParentLogId().length());
    }

    // --- attributes: after-end guard + parity mirrors ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newAttributesAddThrowsAfterEnd() {
        createEndedJob().attributes().add("late", "x");
    }

    @Test
    public void deprecatedAddAttributeStaysLenientAfterEnd() {
        JobImpl job = createEndedJob();
        job.addAttribute("late", "x"); // WARN, no throw
        assertEquals("x", job.getAttribute("late"));
    }

    @Test
    public void attributesArePutAndQueried_viaFacet() {
        JobImpl job = createStartedJob();
        job.attributes().add("k", "v");
        assertEquals("v", job.attributes().get("k"));
        assertTrue(job.attributes().has("k"));
        assertFalse(job.attributes().has("missing"));
        Map<String, String> all = job.attributes().getAll();
        assertEquals("v", all.get("k"));
        all.clear(); // detached copy
        assertTrue(job.attributes().has("k"));
    }

    @Test
    public void nullAttributeValueIsIgnored_viaFacet() {
        JobImpl job = createStartedJob();
        job.attributes().add("k", null); // must NOT throw
        assertFalse(job.attributes().has("k"));
    }

    // --- activities: inverted deviation + parity mirrors ---

    @Test
    public void newActivitiesAddWorksBeforeStart() {
        JobImpl job = createJob();
        // THE inverted deviation: the deprecated addActivity throws before start (pinned in the
        // baseline), the new API allows pre-start activity creation.
        Activity activity = job.activities().create(actModel()).build();
        assertFalse(job.hasStarted());
        assertSame(activity, job.activities().getByInstanceId(activity.getInstanceId()));
    }

    @Test
    public void prestartActivityIsFlushedAfterStart() throws InterruptedException {
        JobImpl job = createJob();
        job.activities().create(actModel()).build();
        job.start();
        CapturingLogMessageSender capturing = new CapturingLogMessageSender();
        TestSender.setSenderMock(capturing);
        try {
            job.end(true);
            com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage sent = capturing.poll(5000);
            assertNotNull(sent);
            assertEquals(1, sent.getActivities().size());
        } finally {
            TestSender.setSenderMock(null);
        }
    }

    @Test
    public void activityLifecycleAfterStart_viaFacet() {
        JobImpl job = createStartedJob();
        Activity activity = job.activities().create(actModel()).build();
        assertSame(activity, job.activities().getByInstanceId(activity.getInstanceId()));
        assertSame(activity, job.activities().getByModelId("act"));
        assertSame(activity, job.activities().getRunningByModelId("act"));
        assertNull(job.activities().getCompletedByModelId("act"));
        activity.end();
        assertSame(activity, job.activities().getCompletedByModelId("act"));
        assertNull(job.activities().getRunningByModelId("act"));
        assertEquals(1, job.activities().getAll().size());
    }

    @Test
    public void getActivitiesReturnsDetachedCopy_viaFacet() {
        JobImpl job = createStartedJob();
        job.activities().create(actModel()).build();
        job.activities().getAll().clear();
        assertEquals(1, job.activities().getAll().size());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void secondStartActivityThrows_viaFacet() {
        JobImpl job = createStartedJob();
        ActivityModel starter = process.createActivity("starterF", "StarterF", null);
        starter.setStarter(true);
        job.activities().create(starter).setStarter().build();
        job.activities().create(starter).setStarter().build();
    }

    @Test
    public void startActivityIsTracked_viaFacet() {
        JobImpl job = createStartedJob();
        ActivityModel starter = process.createActivity("starterF2", "StarterF2", null);
        starter.setStarter(true);
        Activity activity = job.activities().create(starter).setStarter().build();
        assertSame(activity, job.activities().getStart());
    }

    @Test
    public void createGroupAndSubProcess_viaFacet() {
        JobImpl job = createStartedJob();
        com.im.njams.sdk.model.GroupModel groupModel = process.createGroup("grpF", "GroupF", null);
        Activity group = job.activities().createGroup(groupModel).build();
        assertNotNull(group);
        com.im.njams.sdk.model.SubProcessActivityModel subModel =
            process.createSubProcess("subF", "SubF", null);
        Activity sub = job.activities().createSubProcess(subModel).build();
        assertNotNull(sub);
    }

    // --- properties + tracing: parity mirrors ---

    @Test
    public void propertiesRoundTrip_viaFacet() {
        JobImpl job = createJob();
        assertFalse(job.properties().has("p"));
        job.properties().set("p", 42);
        assertTrue(job.properties().has("p"));
        assertEquals(42, job.properties().get("p"));
        assertEquals(42, job.properties().remove("p"));
        assertNull(job.properties().get("p"));
        assertNull(job.properties().remove("p"));
    }

    @Test
    public void deepTraceFlag_viaFacet() {
        JobImpl job = createJob();
        assertFalse(job.tracing().isDeepTrace());
        job.tracing().setDeepTrace(true);
        assertTrue(job.tracing().isDeepTrace());
        assertFalse(job.tracing().isTraces());
    }

    /** Captures the first LogMessage passed to the sender, with an await-with-timeout. */
    private static final class CapturingLogMessageSender extends com.im.njams.sdk.communication.AbstractSender {
        private final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        private volatile com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage lastLogMessage;

        @Override
        public String getName() {
            return "CAPTURING";
        }

        @Override
        public void send(com.faizsiegeln.njams.messageformat.v4.common.CommonMessage msg, String clientSessionId) {
            if (msg instanceof com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage) {
                lastLogMessage = (com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage) msg;
                latch.countDown();
            }
        }

        com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage poll(long millis) throws InterruptedException {
            latch.await(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
            return lastLogMessage;
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
