package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.logmessage.JobImpl;

/**
 * Tests for {@link ReplayHandler#markAsReplayed(com.im.njams.sdk.logmessage.Job)}.
 */
public class ReplayHandlerTest extends AbstractTest {

    @Test
    public void markAsReplayedSetsMarkerAttribute() {
        JobImpl job = createDefaultStartedJob();

        ReplayHandler.markAsReplayed(job);

        assertEquals("true", job.attributes().get(ReplayHandler.NJAMS_REPLAYED_ATTRIBUTE));
    }

    @Test
    public void markAsReplayedOnFinishedJobDoesNotThrow() {
        JobImpl job = createDefaultStartedJob();
        job.end(true);

        ReplayHandler.markAsReplayed(job);
    }
}
