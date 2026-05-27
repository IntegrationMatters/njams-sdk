package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ExtractRule;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.RuleType;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Characterization test pinning down the invariant that extract rules always operate on the FULL,
 * untruncated serialized data, even when a payload size limit is configured. Only the stored
 * input/output is truncated; the data handed to extract rules is not.
 *
 * <p>This test must PASS against the current, unchanged production code. It is the safety net for a
 * later change (SDK-419) to the {@code Serializer} interface that introduces early size-limited
 * serialization.
 */
public class ActivityImplExtractDataTest extends AbstractTest {

    private static final int PAYLOAD_LIMIT = 10;

    /** A distinctive token that the regex captures. Short enough (8 chars) to survive the limit when stored. */
    private static final String NEEDLE = "NEEDLE42";

    /**
     * The needle is placed well past the payload limit so that it only appears in the FULL serialized
     * data, never in the truncated stored input.
     */
    private static final String INPUT =
        "0123456789AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" + NEEDLE + "TAIL";

    @Test
    public void extractRulesReceiveFullUntruncatedSerializedData() {
        ClientSettings settings = njams.getSettings();
        settings.put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, "truncate");
        settings.put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, String.valueOf(PAYLOAD_LIMIT));

        ExtractRule rule = new ExtractRule();
        rule.setAttribute("needleAttribute");
        rule.setRuleType(RuleType.REGEXP);
        rule.setInout("in");
        rule.setRule("(NEEDLE\\d+)");

        Extract extract = new Extract();
        extract.setName("needleExtract");
        extract.getExtractRules().add(rule);

        ActivityConfiguration activityConfig = njams.getConfiguration().getProcess(process.getPath().toString())
            .getOrCreateActivity(ACTIVITYMODELID);
        activityConfig.setExtract(extract);

        JobImpl job = createDefaultJob();
        // Enable tracing so that the stored input is set (and therefore truncated). Must be set before the
        // activity is created so ActivityImpl picks up the extract config and the trace flag.
        job.setDeepTrace(true);
        job.start();

        ActivityImpl activity = (ActivityImpl) createDefaultActivity(job);

        activity.processInput(INPUT);

        // The stored input IS truncated, proving the limit is active.
        String storedInput = activity.getInput();
        assertNotNull(storedInput);
        assertEquals(INPUT.substring(0, PAYLOAD_LIMIT) + JobImpl.PAYLOAD_TRUNCATED_SUFFIX, storedInput);
        assertTrue("needle must NOT be present in the truncated stored input",
            !storedInput.contains(NEEDLE));

        // The extract attribute WAS populated with the needle value. This is only possible if the regex saw
        // the full untruncated string, since the needle is located well beyond the payload limit.
        assertEquals(NEEDLE, activity.getAttributes().get("needleAttribute"));
    }
}
