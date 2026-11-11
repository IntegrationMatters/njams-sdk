package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.AttributeType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ExtractRule;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.RuleType;
import com.im.njams.sdk.ModelOwnerProbe;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsConfiguration;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.configuration.provider.MemoryConfigurationProvider;
import com.im.njams.sdk.logmessage.ExtractHandler.ExtractSource;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

/**
 * Extract-rule handling for all rule types, attribute targets and directions (SDK-499).
 */
public class ExtractHandlerRuleTest {

    private static final String PROCESS = "EXTRACT_RULES";
    private static final String KEY = "k";
    private static final AtomicInteger ACTIVITY_COUNTER = new AtomicInteger();

    private static Njams njams;

    @BeforeClass
    public static void configureNjams() {
        njams = spy(new Njams(Path.of("SDK4", "EXTRACT"), "1.0.0", "sdk4", TestReceiver.getSettings()));
        ModelOwnerProbe.create(njams, PROCESS);
        njams.start();

        Configuration conf = new Configuration();
        conf.setConfigurationProvider(new MemoryConfigurationProvider());
        conf.setLogMode(LogMode.COMPLETE);
        conf.setDataMasking(new ArrayList<>());
        conf.setRecording(true);
        ProcessConfiguration processConf = conf.getProcess(njams.model().get(PROCESS).getPath().toString());
        processConf.setLogLevel(LogLevel.INFO);
        processConf.setExclude(false);
        processConf.setRecording(true);
        processConf.setActivities(new HashMap<>());

        NjamsConfiguration configurationFacet = spy(njams.configuration());
        doReturn(conf).when(configurationFacet).get();
        doReturn(configurationFacet).when(njams).configuration();
    }

    @Before
    @After
    public void resetDataMasking() {
        DataMasking.removePatterns();
    }

    @Test
    public void xpathRuleSetsAttribute() {
        ActivityImpl activity = extractInput(rule(RuleType.XPATH, "/a/b"), "<a><b>v</b></a>");
        assertEquals("v", activity.getAttributes().get(KEY));
    }

    @Test
    public void xpathRuleWithNamespacesSetsAttribute() {
        ActivityImpl activity = extractInput(rule(RuleType.XPATH, "/p:a/p:b"),
            "<p:a xmlns:p=\"urn:test\"><p:b>v</p:b></p:a>");
        assertEquals("v", activity.getAttributes().get(KEY));
    }

    @Test
    public void xpathRuleWithoutMatchSetsEmptyValue() {
        ActivityImpl activity = extractInput(rule(RuleType.XPATH, "/a/c"), "<a><b>v</b></a>");
        assertEquals("", activity.getAttributes().get(KEY));
    }

    @Test
    public void xpathRuleWithInvalidExpressionIsIgnored() {
        ActivityImpl activity = extractInput(rule(RuleType.XPATH, "//["), "<a><b>v</b></a>");
        assertNull(activity.getAttributes().get(KEY));
    }

    @Test
    public void jmespathRuleSetsAttributeWithoutQuotes() {
        ActivityImpl activity = extractInput(rule(RuleType.JMESPATH, "a.b"), "{\"a\":{\"b\":\"v\"}}");
        assertEquals("v", activity.getAttributes().get(KEY));
    }

    @Test
    public void jmespathRuleOnInvalidJsonIsIgnored() {
        ActivityImpl activity = extractInput(rule(RuleType.JMESPATH, "a.b"), "not json");
        assertNull(activity.getAttributes().get(KEY));
    }

    @Test
    public void eventRuleSetsEventStatus() {
        ActivityImpl activity = extractInput(rule(RuleType.EVENT, "error"), "data");
        assertEquals(Integer.valueOf(3), activity.getEventStatus());

        ActivityImpl unchanged = extractInput(rule(RuleType.EVENT, ""), "data");
        assertNull(unchanged.getEventStatus());
    }

    @Test
    public void eventAttributeTypeSetsEventStatus() {
        assertEquals(Integer.valueOf(1),
            extractInput(eventRule(RuleType.REGEXP, "(success)"), "status: success").getEventStatus());
        assertEquals(Integer.valueOf(2), extractInput(eventRule(RuleType.VALUE, "warning"), null).getEventStatus());
        assertEquals(Integer.valueOf(3),
            extractInput(eventRule(RuleType.XPATH, "/s"), "<s>error</s>").getEventStatus());
        assertEquals(Integer.valueOf(2),
            extractInput(eventRule(RuleType.JMESPATH, "s"), "{\"s\":\"2\"}").getEventStatus());
        assertEquals(Integer.valueOf(0), extractInput(eventRule(RuleType.VALUE, "other"), null).getEventStatus());
    }

    @Test
    public void specialAttributeNamesSetJobAndActivityFields() {
        ActivityImpl activity = newActivity();
        JobImpl job = (JobImpl) activity.getJob();
        Extract extract = extract(
            valueRule("CorrelationLogId", "corr"),
            valueRule("parentlogid", "parent"),
            valueRule("externallogid", "external"),
            valueRule("businessservice", "a>b"),
            valueRule("businessobject", "c"),
            valueRule("eventmessage", "message"),
            valueRule("eventcode", "code"),
            valueRule("payload", "payload"),
            valueRule("stacktrace", "trace"));

        ExtractHandler.handleExtract(job, extract, activity, ExtractSource.INPUT, null);

        assertEquals("corr", job.metadata().getCorrelationLogId());
        assertEquals("parent", job.metadata().getParentLogId());
        assertEquals("external", job.metadata().getExternalLogId());
        assertEquals(">a>b>", job.metadata().getBusinessService());
        assertEquals(">c>", job.metadata().getBusinessObject());
        assertEquals("message", activity.getEventMessage());
        assertEquals("code", activity.getEventCode());
        assertEquals("payload", activity.getEventPayload());
        assertEquals("trace", activity.getStackTrace());
        assertTrue(activity.getAttributes().isEmpty());
    }

    @Test
    public void disabledRuleAndOtherDirectionAreSkipped() {
        ExtractRule disabled = rule(RuleType.DISABLED, "v");
        ExtractRule output = rule(RuleType.VALUE, "v");
        output.setInout("out");

        ActivityImpl activity = extractInput(extract(disabled, output), "data");

        assertTrue(activity.getAttributes().isEmpty());
    }

    @Test
    public void outputRuleIsAppliedToOutputData() {
        ExtractRule output = rule(RuleType.REGEXP, "o(.)");
        output.setInout("OUT");
        ActivityImpl activity = newActivity();

        ExtractHandler.handleExtract((JobImpl) activity.getJob(), extract(output), activity, ExtractSource.OUTPUT,
            "out");

        assertEquals("u", activity.getAttributes().get(KEY));
    }

    @Test
    public void regexpOnBlankDataAndInvalidPatternAreIgnored() {
        assertNull(extractInput(rule(RuleType.REGEXP, "a"), " ").getAttributes().get(KEY));
        assertNull(extractInput(rule(RuleType.REGEXP, "("), "abc").getAttributes().get(KEY));
    }

    @Test
    public void regexpUsesFirstGroupOrWholeMatch() {
        assertEquals("b", extractInput(rule(RuleType.REGEXP, "a(b)c"), "xabcx").getAttributes().get(KEY));
        assertEquals("abc", extractInput(rule(RuleType.REGEXP, "abc"), "xabcx").getAttributes().get(KEY));
        assertNull(extractInput(rule(RuleType.REGEXP, "zzz"), "xabcx").getAttributes().get(KEY));
    }

    @Test
    public void nullExtractIsIgnored() {
        ActivityImpl activity = newActivity();

        ExtractHandler.handleExtract((JobImpl) activity.getJob(), (Extract) null, activity, ExtractSource.INPUT,
            "data");

        assertTrue(activity.getAttributes().isEmpty());
    }

    @Test
    public void extractFromActivityConfiguration() {
        ProcessModel process = njams.model().get(PROCESS);
        ActivityModel configured = process.createActivity(nextActivityName(), "Act", null);
        ActivityModel unconfigured = process.createActivity(nextActivityName(), "Act", null);
        ActivityConfiguration activityConfiguration = new ActivityConfiguration();
        activityConfiguration.setExtract(extract(rule(RuleType.VALUE, "v")));
        Map<String, ActivityConfiguration> activities = new HashMap<>();
        activities.put(configured.getId(), activityConfiguration);
        ProcessConfiguration processConfiguration =
            njams.configuration().get().getProcess(process.getPath().toString());
        processConfiguration.setActivities(activities);
        try {
            JobImpl job = (JobImpl) process.createJob();
            job.start();
            ActivityImpl configuredActivity = (ActivityImpl) job.activities().create(configured).build();
            ActivityImpl unconfiguredActivity = (ActivityImpl) job.activities().create(unconfigured).build();

            ExtractHandler.handleExtract(job, configuredActivity, ExtractSource.INPUT, "data");
            ExtractHandler.handleExtract(job, unconfiguredActivity, ExtractSource.INPUT, "data");

            assertEquals("v", configuredActivity.getAttributes().get(KEY));
            assertTrue(unconfiguredActivity.getAttributes().isEmpty());
        } finally {
            processConfiguration.setActivities(new HashMap<>());
        }
    }

    @Test
    public void byDirectionIsCaseInsensitiveAndRejectsUnknown() {
        assertSame(ExtractSource.INPUT, ExtractSource.byDirection("IN"));
        assertSame(ExtractSource.OUTPUT, ExtractSource.byDirection("Out"));
        try {
            ExtractSource.byDirection("x");
            throw new AssertionError("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void testExpressionCoversAllRuleTypes() throws Exception {
        assertEquals("1", ExtractHandler.testExpression(RuleType.JMESPATH, "a", "{\"a\":1}"));
        assertNull(ExtractHandler.testExpression(RuleType.JMESPATH, " ", "{\"a\":1}"));
        assertNull(ExtractHandler.testExpression(RuleType.DISABLED, "a", "a"));
        assertNull(ExtractHandler.testExpression(RuleType.EVENT, "a", "a"));
        assertNull(ExtractHandler.testExpression(RuleType.REGEXP, "zzz", "abc"));
        assertNull(ExtractHandler.testExpression(RuleType.REGEXP, "a", " "));
        assertEquals("b", ExtractHandler.testExpression(RuleType.REGEXP, "a(b)", "abc"));
        assertNull(ExtractHandler.testExpression(RuleType.XPATH, "/a", " "));
        try {
            ExtractHandler.testExpression(null, "a", "a");
            throw new AssertionError("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static ActivityImpl extractInput(ExtractRule rule, String data) {
        return extractInput(extract(rule), data);
    }

    private static ActivityImpl extractInput(Extract extract, String data) {
        ActivityImpl activity = newActivity();
        ExtractHandler.handleExtract((JobImpl) activity.getJob(), extract, activity, ExtractSource.INPUT, data);
        return activity;
    }

    private static ActivityImpl newActivity() {
        ProcessModel process = njams.model().get(PROCESS);
        ActivityModel model = process.createActivity(nextActivityName(), "Act", null);
        JobImpl job = (JobImpl) process.createJob();
        job.start();
        return (ActivityImpl) job.activities().create(model).build();
    }

    private static String nextActivityName() {
        return "activity" + ACTIVITY_COUNTER.incrementAndGet();
    }

    private static Extract extract(ExtractRule... rules) {
        Extract extract = new Extract();
        extract.setName("test");
        for (ExtractRule rule : rules) {
            extract.getExtractRules().add(rule);
        }
        return extract;
    }

    private static ExtractRule rule(RuleType type, String expression) {
        ExtractRule rule = new ExtractRule();
        rule.setAttribute(KEY);
        rule.setAttributeType(AttributeType.ATTRIBUTE);
        rule.setRuleType(type);
        rule.setRule(expression);
        rule.setInout("in");
        return rule;
    }

    private static ExtractRule eventRule(RuleType type, String expression) {
        ExtractRule rule = rule(type, expression);
        rule.setAttributeType(AttributeType.EVENT);
        return rule;
    }

    private static ExtractRule valueRule(String attribute, String value) {
        ExtractRule rule = rule(RuleType.VALUE, value);
        rule.setAttribute(attribute);
        return rule;
    }
}
