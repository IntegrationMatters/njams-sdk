package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.After;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.model.ActivityModel;

/**
 * Unit tests for {@link ActivityBuilder}: instance-id generation, the fluent setters and the
 * validation performed at build time.
 */
public class ActivityBuilderTest extends AbstractTest {

    @After
    public void tearDown() {
        DataMasking.removePatterns();
    }

    private ActivityModel model(String id) {
        ActivityModel existing = process.getActivity(id);
        return existing != null ? existing : process.createActivity(id, id, null);
    }

    @Test
    public void buildGeneratesInstanceIdAndStartsActivity() {
        JobImpl job = createDefaultStartedJob();
        Activity activity = job.createActivity(model("gen")).build();

        assertNotNull(activity.getInstanceId());
        assertTrue(activity.getInstanceId().length() > 0);
        assertEquals(ActivityStatus.RUNNING, activity.getActivityStatus());
        // the built activity is registered with the job
        assertSame(activity, job.getActivityByInstanceId(activity.getInstanceId()));
    }

    @Test
    public void explicitInstanceIdIsHonored() {
        JobImpl job = createDefaultStartedJob();
        Activity activity = job.createActivity(model("explicit")).setInstanceId("my-id").build();
        assertEquals("my-id", activity.getInstanceId());
    }

    @Test
    public void fluentSettersAreAppliedToBuiltActivity() {
        JobImpl job = createDefaultStartedJob();
        LocalDateTime execution = DateTimeUtility.now();

        ActivityImpl activity = (ActivityImpl) job.createActivity(model("fluent"))
                .setIteration(2L)
                .setMaxIterations(7L)
                .setParentInstanceId("parent")
                .setExecution(execution)
                .setDuration(123L)
                .setCpuTime(45L)
                .setEventCode("EC")
                .setEventMessage("EM")
                .setEventPayload("EP")
                .setStackTrace("ST")
                .setStartData("SD")
                .addAttribute("k", "v")
                .build();

        assertEquals(Long.valueOf(2L), activity.getIteration());
        assertEquals(Long.valueOf(7L), activity.getMaxIterations());
        assertEquals("parent", activity.getParentInstanceId());
        assertEquals(execution, activity.getExecution());
        assertEquals(123L, activity.getDuration());
        assertEquals(45L, activity.getCpuTime());
        assertEquals("EC", activity.getEventCode());
        assertEquals("EM", activity.getEventMessage());
        assertEquals("EP", activity.getEventPayload());
        assertEquals("ST", activity.getStackTrace());
        assertEquals("SD", activity.getStartData());
        assertEquals("v", activity.getAttributes().get("k"));
    }

    @Test
    public void setStarterMarksStartActivity() {
        JobImpl job = createDefaultStartedJob();
        Activity activity = job.createActivity(model("starter")).setStarter().build();
        assertTrue(activity.isStarter());
        assertSame(activity, job.getStartActivity());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void buildThrowsWhenModelIdMissing() {
        JobImpl job = createDefaultStartedJob();
        ActivityModel noId = mock(ActivityModel.class);
        when(noId.getId()).thenReturn(null);
        new ActivityBuilder(job, noId).build();
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void buildThrowsWhenSequenceMissing() {
        JobImpl job = createDefaultStartedJob();
        // this constructor does not assign a sequence
        ActivityImpl raw = new ActivityImpl(job, model("noSeq"));
        new ActivityBuilder(raw).build();
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void stepFromNullThrows() {
        JobImpl job = createDefaultStartedJob();
        new ActivityBuilder(job, model("step")).stepFrom(null);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void stepFromNullTransitionModelThrows() {
        JobImpl job = createDefaultStartedJob();
        Activity from = job.createActivity(model("from")).build();
        new ActivityBuilder(job, model("to")).stepFrom(from, null);
    }
}
