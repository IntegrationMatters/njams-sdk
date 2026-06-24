package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.model.ActivityModel;

/**
 * Unit tests for {@link JobActivities}, the activity registry facet of a job: registration,
 * model/instance lookups, the start activity and the flush bookkeeping.
 */
public class JobActivitiesTest extends AbstractTest {

    private JobImpl startedJob() {
        return createDefaultStartedJob();
    }

    private ActivityModel model(String id) {
        ActivityModel existing = process.getActivity(id);
        return existing != null ? existing : process.createActivity(id, id.toUpperCase(), null);
    }

    @Test
    public void getByInstanceIdReturnsActivityOrNull() {
        JobImpl job = startedJob();
        Activity a = job.activities().create(model("inst")).build();
        assertSame(a, job.activities().getByInstanceId(a.getInstanceId()));
        assertNull(job.activities().getByInstanceId("unknown"));
    }

    @Test
    public void getByModelIdReturnsLastAdded() {
        JobImpl job = startedJob();
        ActivityModel m = model("dup");
        Activity first = job.activities().create(m).build();
        Activity second = job.activities().create(m).build();
        assertSame(second, job.activities().getByModelId("dup"));
        // make sure the two are really distinct instances of the same model
        assertFalse(first.getInstanceId().equals(second.getInstanceId()));
    }

    @Test
    public void getRunningAndCompletedByModelId() {
        JobImpl job = startedJob();
        ActivityModel m = model("status");
        Activity first = job.activities().create(m).build();
        Activity second = job.activities().create(m).build();

        // both running -> last running is the second
        assertSame(second, job.activities().getRunningByModelId("status"));
        assertNull(job.activities().getCompletedByModelId("status"));

        // complete the second -> last running falls back to the first, last completed is the second
        second.setActivityStatus(ActivityStatus.SUCCESS);
        assertSame(first, job.activities().getRunningByModelId("status"));
        assertSame(second, job.activities().getCompletedByModelId("status"));
    }

    @Test
    public void getAllReturnsDetachedCopy() {
        JobImpl job = startedJob();
        Activity a = job.activities().create(model("copy")).build();

        Collection<Activity> all = job.activities().getAll();
        assertTrue(all.contains(a));

        all.clear();
        // clearing the returned copy must not affect the registry
        assertTrue(job.activities().getAll().contains(a));
    }

    @Test
    public void startActivityIsTrackedAndDuplicatesRejected() {
        JobImpl job = startedJob();
        assertNull(job.activities().getStart());

        Activity starter = job.activities().create(model("starter")).setStarter().build();
        assertSame(starter, job.activities().getStart());

        try {
            job.activities().create(model("starter2")).setStarter().build();
            org.junit.Assert.fail("a second start activity must be rejected");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    @Test
    public void removeNotRunningKeepsRunningAndDropsCompleted() {
        JobImpl job = startedJob();
        Activity running = job.activities().create(model("running")).build();
        Activity completed = job.activities().create(model("completed")).build();
        completed.setActivityStatus(ActivityStatus.SUCCESS);

        job.activities().removeNotRunning();

        assertTrue(job.activities().getAll().contains(running));
        assertFalse(job.activities().getAll().contains(completed));
    }

    @Test
    public void shouldFlushAndHasActivityToSend() {
        JobImpl job = startedJob();
        Activity a = job.activities().create(model("flush")).build();
        // a freshly added activity has never been flushed -> must be sent
        assertTrue(job.activities().shouldFlush(a));
        assertTrue(job.activities().hasActivityToSend());
    }

    @Test
    public void sequenceIncreasesMonotonically() {
        JobImpl job = startedJob();
        long s1 = job.activities().getNextSequence();
        long s2 = job.activities().getNextSequence();
        assertEquals(s1 + 1, s2);
    }
}
