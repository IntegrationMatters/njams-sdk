package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.model.ActivityModel;

/**
 * SDK-462: a job carries a single start data. The first start data set wins; later attempts (on the
 * same or another activity, via {@code setStartData} or {@code processStartData}) are ignored.
 */
public class StartDataOncePerJobTest extends AbstractTest {

    private ActivityImpl newActivity(JobImpl job, String modelId) {
        ActivityModel model = process.getActivity(modelId);
        if (model == null) {
            model = process.createActivity(modelId, modelId, null);
        }
        return (ActivityImpl) job.createActivity(model).build();
    }

    @Test
    public void firstSetStartDataWinsAcrossActivities() {
        JobImpl job = createDefaultStartedJob();
        ActivityImpl first = newActivity(job, "a1");
        ActivityImpl second = newActivity(job, "a2");

        first.setStartData("first");
        second.setStartData("second");

        assertEquals("first", first.getStartData());
        assertNull("a later activity must not carry start data", second.getStartData());
    }

    @Test
    public void repeatedSetStartDataOnSameActivityKeepsTheFirst() {
        JobImpl job = createDefaultStartedJob();
        ActivityImpl activity = newActivity(job, "a1");

        activity.setStartData("first");
        activity.setStartData("second");

        assertEquals("first", activity.getStartData());
    }

    @Test
    public void nullStartDataDoesNotConsumeTheSlot() {
        JobImpl job = createDefaultStartedJob();
        ActivityImpl activity = newActivity(job, "a1");

        activity.setStartData(null);
        activity.setStartData("real");

        assertEquals("real", activity.getStartData());
    }

    @Test
    public void processStartDataIsIgnoredAfterStartDataWasSet() {
        JobImpl job = createDefaultStartedJob();
        ActivityImpl first = newActivity(job, "a1");
        ActivityImpl second = newActivity(job, "a2");

        first.setStartData("first");
        second.processStartData("ignored");

        assertEquals("first", first.getStartData());
        assertNull(second.getStartData());
    }
}
