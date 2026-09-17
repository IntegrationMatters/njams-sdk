package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Unit tests for {@link JobTruncation}, the activity/event truncation state machine of a job.
 * Real {@link ActivityImpl} instances are used so that {@link JobTruncation#hasEvent(Activity)}
 * sees genuine event state.
 */
public class JobTruncationTest extends AbstractTest {

    @After
    public void tearDown() {
        DataMasking.removePatterns();
    }

    private static JobSettings settings(String... keyValues) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            m.put(keyValues[i], keyValues[i + 1]);
        }
        return JobSettings.of(ClientSettings.from(m));
    }

    private ActivityModel model(String id) {
        ActivityModel existing = process.getActivity(id);
        return existing != null ? existing : process.createActivity(id, id, null);
    }

    private ActivityImpl activity(JobImpl job, String modelId, boolean withEvent) {
        ActivityImpl a = (ActivityImpl) job.createActivity(model(modelId)).build();
        if (withEvent) {
            a.setEventCode("code");
        }
        return a;
    }

    @Test
    public void truncationDisabledByDefaultAlwaysAdds() {
        JobImpl job = createDefaultStartedJob();
        JobTruncation truncation = new JobTruncation(job, settings());
        assertTrue(truncation.checkTruncating(activity(job, "d1", false), false));
        assertTrue(truncation.checkTruncating(activity(job, "d2", true), true));
    }

    @Test
    public void truncateOnSuccessDropsActivitiesWithoutEventOnSuccess() {
        JobImpl job = createDefaultStartedJob();
        JobTruncation truncation =
                new JobTruncation(job, settings(NjamsSettings.PROPERTY_TRUNCATE_ON_SUCCESS, "true"));
        assertFalse("an event-less activity must be dropped on success",
                truncation.checkTruncating(activity(job, "os1", false), true));
    }

    @Test
    public void truncateOnSuccessKeepsActivitiesWithEventOnSuccess() {
        JobImpl job = createDefaultStartedJob();
        JobTruncation truncation =
                new JobTruncation(job, settings(NjamsSettings.PROPERTY_TRUNCATE_ON_SUCCESS, "true"));
        assertTrue("an activity carrying an event must be kept",
                truncation.checkTruncating(activity(job, "oe1", true), true));
    }

    @Test
    public void truncateOnSuccessKeepsActivitiesBeforeSuccess() {
        JobImpl job = createDefaultStartedJob();
        JobTruncation truncation =
                new JobTruncation(job, settings(NjamsSettings.PROPERTY_TRUNCATE_ON_SUCCESS, "true"));
        assertTrue(truncation.checkTruncating(activity(job, "bs1", false), false));
    }

    @Test
    public void truncateLimitDropsExcessActivities() {
        JobImpl job = createDefaultStartedJob();
        JobTruncation truncation =
                new JobTruncation(job, settings(NjamsSettings.PROPERTY_TRUNCATE_LIMIT, "2"));
        // first two activities fit under the limit
        assertTrue(truncation.checkTruncating(activity(job, "l1", false), false));
        assertTrue(truncation.checkTruncating(activity(job, "l2", false), false));
        // the third exceeds the limit and is dropped (it carries no event)
        assertFalse(truncation.checkTruncating(activity(job, "l3", false), false));
        // a later event-less activity is still dropped while truncating activities
        assertFalse(truncation.checkTruncating(activity(job, "l4", false), false));
        // but an activity carrying an event is still kept
        assertTrue(truncation.checkTruncating(activity(job, "l5", true), false));
    }

    @Test
    public void hasEventDetectsAnyEventField() {
        JobImpl job = createDefaultStartedJob();
        assertFalse(JobTruncation.hasEvent(activity(job, "he0", false)));

        ActivityImpl byCode = activity(job, "heCode", false);
        byCode.setEventCode("c");
        assertTrue(JobTruncation.hasEvent(byCode));

        ActivityImpl byMessage = activity(job, "heMsg", false);
        byMessage.setEventMessage("m");
        assertTrue(JobTruncation.hasEvent(byMessage));

        ActivityImpl byPayload = activity(job, "hePayload", false);
        byPayload.setEventPayload("p");
        assertTrue(JobTruncation.hasEvent(byPayload));

        ActivityImpl byStack = activity(job, "heStack", false);
        byStack.setStackTrace("s");
        assertTrue(JobTruncation.hasEvent(byStack));

        ActivityImpl byStatus = activity(job, "heStatus", false);
        byStatus.setEventStatus(EventStatus.ERROR.getValue());
        assertTrue(JobTruncation.hasEvent(byStatus));
    }
}
