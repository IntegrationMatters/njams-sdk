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
import com.im.njams.sdk.model.GroupModel;

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
    public void getByModelIdFallsBackToRemainingAfterRemoveNotRunning() {
        JobImpl job = startedJob();
        ActivityModel m = model("evict");
        Activity first = job.activities().create(m).build();
        Activity second = job.activities().create(m).build();
        assertSame(second, job.activities().getByModelId("evict"));

        // the most recent one completes and is evicted by the flush bookkeeping
        second.setActivityStatus(ActivityStatus.SUCCESS);
        job.activities().removeNotRunning();

        // the lookup must fall back to the still-running predecessor, not return the evicted one
        assertSame(first, job.activities().getByModelId("evict"));
    }

    @Test
    public void getByModelIdReturnsNullWhenAllMatchesEvicted() {
        JobImpl job = startedJob();
        Activity only = job.activities().create(model("gone")).build();
        only.setActivityStatus(ActivityStatus.SUCCESS);
        job.activities().removeNotRunning();

        assertNull(job.activities().getByModelId("gone"));
    }

    @Test
    public void reAddingAnAlreadyRegisteredActivityKeepsItsOriginalPosition() {
        JobImpl job = startedJob();
        ActivityModel m = model("readd");
        Activity first = job.activities().create(m).build();
        Activity second = job.activities().create(m).build();

        // re-registering the earlier activity must not make it the "last added" one
        job.activities().add(first);

        assertSame(second, job.activities().getByModelId("readd"));
        assertEquals(2, job.activities().getAll().size());
    }

    @Test
    public void lookupIsUnaffectedByOtherModelIds() {
        JobImpl job = startedJob();
        ActivityModel target = model("target");
        ActivityModel noise = model("noise");
        Activity wanted = job.activities().create(target).build();
        for (int i = 0; i < 5; i++) {
            job.activities().create(noise).build();
        }

        assertSame(wanted, job.activities().getByModelId("target"));
        assertNull(job.activities().getByModelId("unknown"));
    }

    @Test
    public void groupLoopResolvesToTheMostRecentIteration() {
        JobImpl job = startedJob();
        GroupModel groupModel = process.getGroup("loopGroup") != null ? process.getGroup("loopGroup")
            : process.createGroup("loopGroup", "LOOPGROUP", null);
        ActivityModel childModel = model("loopChild");

        Group group = (Group) job.activities().createGroup(groupModel).build();
        Activity last = null;
        for (int i = 0; i < 25; i++) {
            last = group.createChildActivity(childModel).build();
            last.end();
            group.iterate();
        }

        // every iteration adds a new activity instance; the lookup resolves to the newest one
        assertEquals(26, job.activities().getAll().size());
        assertSame(last, job.activities().getByModelId("loopChild"));
    }

    @Test
    public void activityThatWasAddedButNeverStartedIsReturnedByModelLookup() {
        JobImpl job = startedJob();
        ActivityModel m = model("nostatus");
        Activity started = job.activities().create(m).build();

        // an activity may be registered without ever being started -> it has no status yet
        ActivityImpl unstarted = new ActivityImpl(job, m);
        unstarted.setInstanceId("nostatus$unstarted");
        job.activities().add(unstarted);

        assertSame(unstarted, job.activities().getByModelId("nostatus"));
    }

    @Test
    public void sequenceIncreasesMonotonically() {
        JobImpl job = startedJob();
        long s1 = job.activities().getNextSequence();
        long s2 = job.activities().getNextSequence();
        assertEquals(s1 + 1, s2);
    }

    @Test
    public void facetBuilderMayBuildBeforeJobIsStarted() {
        JobImpl job = createDefaultJob();
        Activity a = job.activities().create(model("prestart")).build();
        assertSame(a, job.activities().getByInstanceId(a.getInstanceId()));
    }

    @Test
    public void internalStepToBuilderRequiresStartedJob() {
        JobImpl job = createDefaultJob();
        ActivityModel from = model("prestartFrom");
        ActivityModel to = from.transitionTo("prestartTo", "TO", null);
        Activity a = job.activities().create(from).build();
        try {
            a.stepTo(to).build();
            org.junit.Assert.fail("stepTo on a not started job must be rejected");
        } catch (NjamsSdkRuntimeException expected) {
            assertEquals("The method start() must be called before activities can be added to the job!",
                    expected.getMessage());
        }
    }

    @Test
    public void internalGroupChildBuilderRequiresStartedJob() {
        JobImpl job = createDefaultJob();
        GroupModel groupModel = (GroupModel) process.createGroup("prestartGroup", "G", null);
        Group group = job.activities().createGroup(groupModel).build();
        try {
            group.createChildActivity(model("prestartChild")).build();
            org.junit.Assert.fail("createChildActivity on a not started job must be rejected");
        } catch (NjamsSdkRuntimeException expected) {
            assertEquals("The method start() must be called before activities can be added to the job!",
                    expected.getMessage());
        }
    }
}
