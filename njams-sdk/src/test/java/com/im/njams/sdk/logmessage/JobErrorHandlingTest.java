package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Unit tests for {@link JobErrorHandling}: storing the last activity error and committing it as an
 * error event, both in immediate ("log all errors") and deferred (commit-on-failure) mode.
 */
public class JobErrorHandlingTest extends AbstractTest {

    @After
    public void tearDown() {
        DataMasking.removePatterns();
    }

    private static JobSettings jobSettings(boolean allErrors) {
        Map<String, String> m = new HashMap<>();
        m.put(NjamsSettings.PROPERTY_LOG_ALL_ERRORS, Boolean.toString(allErrors));
        return JobSettings.of(ClientSettings.from(m));
    }

    private ActivityImpl newBuiltActivity(JobImpl job, String modelId) {
        ActivityModel model = process.getActivity(modelId);
        if (model == null) {
            model = process.createActivity(modelId, modelId, null);
        }
        return (ActivityImpl) job.createActivity(model).build();
    }

    private ErrorEvent error() {
        return new ErrorEvent().setCode("CODE").setMessage("MSG").setStatus(EventStatus.WARNING);
    }

    @Test
    public void allErrorsAppliesImmediately() {
        JobImpl job = createDefaultStartedJob();
        JobErrorHandling handling = new JobErrorHandling(job, jobSettings(true));
        ActivityImpl act = newBuiltActivity(job, "immErr");

        handling.setActivityErrorEvent(act, error());

        assertEquals("CODE", act.getEventCode());
        assertEquals("MSG", act.getEventMessage());
        assertEquals(ActivityStatus.WARNING, act.getActivityStatus());
    }

    @Test
    public void deferredErrorIsAppliedOnlyOnCommit() {
        JobImpl job = createDefaultStartedJob();
        JobErrorHandling handling = new JobErrorHandling(job, jobSettings(false));
        ActivityImpl act = newBuiltActivity(job, "defErr");

        handling.setActivityErrorEvent(act, error());
        // not applied yet
        assertNull(act.getEventCode());

        handling.commitActivityError();
        // now applied
        assertEquals("CODE", act.getEventCode());
        assertEquals(ActivityStatus.WARNING, act.getActivityStatus());
    }

    @Test
    public void nullActivityOrEventIsIgnored() {
        JobImpl job = createDefaultStartedJob();
        JobErrorHandling handling = new JobErrorHandling(job, jobSettings(false));
        ActivityImpl act = newBuiltActivity(job, "nullErr");

        handling.setActivityErrorEvent(null, error());
        handling.setActivityErrorEvent(act, null);
        handling.commitActivityError();

        assertNull("nothing was stored, so commit must not change the activity", act.getEventCode());
    }

    @Test
    public void commitWithoutStoredErrorIsNoOp() {
        JobImpl job = createDefaultStartedJob();
        JobErrorHandling handling = new JobErrorHandling(job, jobSettings(false));
        // must not throw
        handling.commitActivityError();
    }

    @Test
    public void commitReaddsActivityThatWasAlreadySent() {
        JobImpl job = createDefaultStartedJob();
        JobErrorHandling handling = new JobErrorHandling(job, jobSettings(false));

        // an activity that is not (or no longer) part of the job's registry
        ActivityModel model = process.createActivity("ghostErr", "ghostErr", null);
        ActivityImpl ghost = new ActivityImpl(job, model);
        ghost.setInstanceId("ghost-instance");
        assertNull(job.getActivityByInstanceId("ghost-instance"));

        handling.setActivityErrorEvent(ghost, error());
        handling.commitActivityError();

        assertSame("commit must re-add an already-sent activity so the error is transmitted",
                ghost, job.getActivityByInstanceId("ghost-instance"));
    }

    @Test
    public void nullEventStatusDefaultsToError() {
        JobImpl job = createDefaultStartedJob();
        JobErrorHandling handling = new JobErrorHandling(job, jobSettings(true));
        ActivityImpl act = newBuiltActivity(job, "statusErr");

        handling.setActivityErrorEvent(act, new ErrorEvent().setStatus(null).setMessage("M"));

        assertEquals(ActivityStatus.ERROR, act.getActivityStatus());
    }
}
