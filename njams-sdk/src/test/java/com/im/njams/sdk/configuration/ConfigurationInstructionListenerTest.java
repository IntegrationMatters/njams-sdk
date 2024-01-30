package com.im.njams.sdk.configuration;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.AttributeType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ExtractRule;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.RuleType;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.configuration.provider.MemoryConfigurationProvider;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.JsonUtils;

public class ConfigurationInstructionListenerTest {

    private static final String ACT = "act_1";
    private static final String PATH = ">test>";

    private ConfigurationInstructionListener listener = null;
    private Configuration configuration = null;
    private Instruction instruction = null;

    @Before
    public void setUp() throws Exception {
        configuration = spy(new Configuration());
        configuration.setConfigurationProvider(new MemoryConfigurationProvider());
        Njams njams = mock(Njams.class);
        when(njams.getConfiguration()).thenReturn(configuration);
        when(njams.getSettings()).thenReturn(new Settings());
        listener = new ConfigurationInstructionListener(njams);
    }

    private ConfigurationInstructionListenerTest prepareInstruction(Command command) {
        Instruction instruction = new Instruction();
        Request request = new Request();
        instruction.setRequest(request);
        request.setCommand(command.commandString());
        Map<String, String> parameters = new TreeMap<>();
        request.setParameters(parameters);
        this.instruction = instruction;
        return this;
    }

    private ConfigurationInstructionListenerTest addPath(String path) {
        return addParameter("processPath", path);
    }

    private ConfigurationInstructionListenerTest addActivityId(String id) {
        return addParameter("activityId", id);
    }

    private ConfigurationInstructionListenerTest addParameter(String name, Object value) {
        String s;
        if (value instanceof LocalDateTime) {
            s = DateTimeUtility.toString((LocalDateTime) value);
        } else {
            s = String.valueOf(value);
        }
        instruction.getRequest().getParameters().put(name, s);
        return this;
    }

    private ProcessConfiguration addProcessConfig(String path) {
        ProcessConfiguration process = new ProcessConfiguration();
        configuration.getProcesses().put(path, process);
        return process;
    }

    private ActivityConfiguration addActivityConfig(String path, String activityId) {
        ProcessConfiguration process = configuration.getProcess(path);
        if (process == null) {
            process = addProcessConfig(path);
        }
        ActivityConfiguration activity = new ActivityConfiguration();
        process.getActivities().put(activityId, activity);
        return activity;
    }

    @Test
    public void testMissingOrFalseCommand() {
        prepareInstruction(GET_LOG_LEVEL);
        instruction.getRequest().setCommand(null);

        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertTrue(response.getResultMessage().contains("command"));
        assertTrue(response.getResultMessage().contains("null"));

        Map<String, String> parameters = response.getParameters();
        assertTrue(parameters.isEmpty());

        prepareInstruction(GET_LOG_LEVEL);
        instruction.getRequest().setCommand("blabla");

        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertTrue(response.getResultMessage().contains("command"));
        assertTrue(response.getResultMessage().contains("blabla"));

        parameters = response.getParameters();
        assertTrue(parameters.isEmpty());
    }

    @Test
    public void testGetLogLovel() {
        // default
        prepareInstruction(GET_LOG_LEVEL).addPath(PATH);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        Map<String, String> parameters = response.getParameters();
        assertEquals("INFO", parameters.get("logLevel"));
        assertEquals("COMPLETE", parameters.get("logMode"));
        assertEquals("false", parameters.get("exclude"));

        // with config
        ProcessConfiguration process = addProcessConfig(PATH);
        process.setExclude(true);
        process.setLogLevel(LogLevel.WARNING);
        configuration.setLogMode(LogMode.EXCLUSIVE);

        prepareInstruction(GET_LOG_LEVEL).addPath(PATH);
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        parameters = response.getParameters();
        assertEquals("WARNING", parameters.get("logLevel"));
        assertEquals("EXCLUSIVE", parameters.get("logMode"));

    }

    @Test
    public void testGetLogLovelFail() {
        prepareInstruction(GET_LOG_LEVEL);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertTrue(response.getResultMessage().contains("processPath"));

        Map<String, String> parameters = response.getParameters();
        assertTrue(parameters.isEmpty());
    }

    @Test
    public void testSetLogLevel() {
        prepareInstruction(SET_LOG_LEVEL).addPath(PATH).addParameter("logLevel", LogLevel.ERROR.name());
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        verify(configuration).save();
        ProcessConfiguration process = configuration.getProcess(PATH);
        assertNotNull(process);
        assertEquals(LogLevel.ERROR, process.getLogLevel());
    }

    @Test
    public void testSetLogLevelFail() {
        prepareInstruction(SET_LOG_LEVEL);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertTrue(response.getResultMessage().contains("processPath"));
        assertTrue(response.getResultMessage().contains("logLevel"));
        Map<String, String> parameters = response.getParameters();
        assertTrue(parameters.isEmpty());

        prepareInstruction(SET_LOG_LEVEL).addPath(PATH).addParameter("logLevel", "blabla");
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertFalse(response.getResultMessage().contains("processPath"));
        assertTrue(response.getResultMessage().contains("logLevel"));
        parameters = response.getParameters();
        assertTrue(parameters.isEmpty());
    }

    @Test
    public void testGetLogMode() {
        prepareInstruction(GET_LOG_MODE);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        Map<String, String> parameters = response.getParameters();
        assertEquals(LogMode.COMPLETE.name(), parameters.get("logMode"));

        configuration.setLogMode(LogMode.EXCLUSIVE);
        prepareInstruction(GET_LOG_MODE);
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        parameters = response.getParameters();
        assertEquals(LogMode.EXCLUSIVE.name(), parameters.get("logMode"));
    }

    @Test
    public void testSetLogMode() {
        prepareInstruction(SET_LOG_MODE).addParameter("logMode", LogMode.EXCLUSIVE.name());
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        verify(configuration).save();
        assertEquals(LogMode.EXCLUSIVE, configuration.getLogMode());
    }

    @Test
    public void testSetLogModeFail() {
        prepareInstruction(SET_LOG_MODE);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertTrue(response.getResultMessage().contains("logMode"));
        Map<String, String> parameters = response.getParameters();
        assertTrue(parameters.isEmpty());

        prepareInstruction(SET_LOG_MODE).addPath(PATH).addParameter("logMode", "blabla");
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertTrue(response.getResultMessage().contains("logMode"));
        parameters = response.getParameters();
        assertTrue(parameters.isEmpty());
    }

    @Test
    public void testGetTracing() {
        ActivityConfiguration activity = addActivityConfig(PATH, ACT);
        TracepointExt tp = new TracepointExt();
        tp.setStarttime(DateTimeUtility.now());
        tp.setEndtime(DateTimeUtility.now().plusMinutes(10));
        tp.setDeeptrace(true);
        tp.setIterations(5);
        activity.setTracepoint(tp);

        prepareInstruction(GET_TRACING).addPath(PATH).addActivityId(ACT);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        Map<String, String> parameters = response.getParameters();
        assertEquals(DateTimeUtility.toString(tp.getStarttime()), parameters.get("starttime"));
        assertEquals(DateTimeUtility.toString(tp.getEndtime()), parameters.get("endtime"));
        assertEquals("5", parameters.get("iterations"));
        assertEquals("true", parameters.get("deeptrace"));
    }

    @Test
    public void testGetTracingFail() {
        prepareInstruction(Command.GET_TRACING);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertTrue(response.getResultMessage().contains("processPath"));
        assertTrue(response.getResultMessage().contains("activityId"));
        Map<String, String> parameters = response.getParameters();
        assertTrue(parameters.isEmpty());

        prepareInstruction(GET_TRACING).addPath(PATH).addActivityId(ACT);
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertFalse(response.getResultMessage().contains("processPath"));
        assertFalse(response.getResultMessage().contains("activityId"));
        assertTrue(response.getResultMessage().contains("not found"));
        parameters = response.getParameters();
        assertTrue(parameters.isEmpty());
    }

    @Test
    public void testSetTracing() {
        LocalDateTime start = DateTimeUtility.now();
        LocalDateTime end = DateTimeUtility.now().plusMinutes(10);
        prepareInstruction(SET_TRACING).addPath(PATH).addActivityId(ACT).addParameter("enableTracing", true)
            .addParameter("starttime", start).addParameter("endtime", end).addParameter("iterations", 5)
            .addParameter("deepTrace", true);

        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        verify(configuration).save();
        ProcessConfiguration process = configuration.getProcess(PATH);
        assertNotNull(process);
        ActivityConfiguration activity = process.getActivity(ACT);
        assertNotNull(activity);
        TracepointExt tp = activity.getTracepoint();
        assertNotNull(tp);
        assertEquals(start, tp.getStarttime());
        assertEquals(end, tp.getEndtime());
        assertEquals(5, (int) tp.getIterations());
        assertEquals(true, tp.isDeeptrace());

        // disable
        prepareInstruction(SET_TRACING).addPath(PATH).addActivityId(ACT).addParameter("enableTracing", false)
            .addParameter("starttime", start).addParameter("endtime", end).addParameter("iterations", 5)
            .addParameter("deepTrace", true);
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        verify(configuration, times(2)).save();
        process = configuration.getProcess(PATH);
        assertNotNull(process);
        activity = process.getActivity(ACT);
        assertNotNull(activity);
        tp = activity.getTracepoint();
        assertNull(tp);

        // enable but expired
        end = DateTimeUtility.now().minusMinutes(10);
        prepareInstruction(SET_TRACING).addPath(PATH).addActivityId(ACT).addParameter("enableTracing", true)
            .addParameter("starttime", start).addParameter("endtime", end).addParameter("iterations", 5)
            .addParameter("deepTrace", true);
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        verify(configuration, times(3)).save();
        process = configuration.getProcess(PATH);
        assertNotNull(process);
        activity = process.getActivity(ACT);
        assertNotNull(activity);
        tp = activity.getTracepoint();
        assertNull(tp);
    }

    @Test
    public void testSetTracingFail() {
        prepareInstruction(SET_TRACING);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertTrue(response.getResultMessage().contains("processPath"));
        assertTrue(response.getResultMessage().contains("activityId"));
        Map<String, String> parameters = response.getParameters();
        assertTrue(parameters.isEmpty());
    }

    @Test
    public void testConfigureExtract() throws Exception {
        Extract ex = new Extract();
        ex.setName("ex_1");
        ExtractRule rule = new ExtractRule();
        rule.setAttribute("Attr1");
        rule.setAttributeType(AttributeType.ATTRIBUTE);
        rule.setRuleType(RuleType.VALUE);
        rule.setRule("Hello");
        rule.setInout("IN");
        ex.getExtractRules().add(rule);
        String extract = JsonUtils.serialize(ex);
        prepareInstruction(CONFIGURE_EXTRACT).addPath(PATH).addActivityId(ACT).addParameter("extract", extract);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        verify(configuration).save();
        ProcessConfiguration process = configuration.getProcess(PATH);
        assertNotNull(process);
        ActivityConfiguration activity = process.getActivity(ACT);
        assertNotNull(activity);
        Extract cex = activity.getExtract();
        assertNotNull(cex);
        assertEquals(1, cex.getExtractRules().size());
        ExtractRule crule = cex.getExtractRules().get(0);
        assertEquals(rule.getRuleType(), crule.getRuleType());
        assertEquals(rule.getRule(), crule.getRule());
        assertEquals(rule.getInout(), crule.getInout());

    }

    @Test
    public void testConfigureExtractFail() {
        prepareInstruction(CONFIGURE_EXTRACT);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertTrue(response.getResultMessage().contains("processPath"));
        assertTrue(response.getResultMessage().contains("activityId"));
        assertTrue(response.getResultMessage().contains("extract"));
        Map<String, String> parameters = response.getParameters();
        assertTrue(parameters.isEmpty());
    }

    @Test
    public void testExtractWithBlanks() {
        Extract ex = new Extract();
        ex.setName("ex_1");
        ExtractRule rule = new ExtractRule();
        rule.setAttribute(" Attr1 ");
        rule.setAttributeType(AttributeType.ATTRIBUTE);
        rule.setRuleType(RuleType.VALUE);
        rule.setRule("Hello");
        rule.setInout("IN");
        ex.getExtractRules().add(rule);
        String extract = JsonUtils.serialize(ex);
        prepareInstruction(CONFIGURE_EXTRACT).addPath(PATH).addActivityId(ACT).addParameter("extract", extract);
        listener.onInstruction(instruction);
        Response resp = instruction.getResponse();
        assertEquals(1, resp.getResultCode());
        assertEquals("Invalid attribute names [ Attr1 ] in extract for ex_1", resp.getResultMessage());
    }

    @Test
    public void testDeleteExtract() {
        ActivityConfiguration activity = addActivityConfig(PATH, ACT);
        activity.setExtract(new Extract());

        prepareInstruction(DELETE_EXTRACT).addPath(PATH).addActivityId(ACT);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        verify(configuration).save();
        ProcessConfiguration process = configuration.getProcess(PATH);
        assertNotNull(process);
        activity = process.getActivity(ACT);
        assertNotNull(activity);
        Extract cex = activity.getExtract();
        assertNull(cex);
    }

    @Test
    public void testDeleteExtractFail() {
        prepareInstruction(DELETE_EXTRACT);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertTrue(response.getResultMessage().contains("processPath"));
        assertTrue(response.getResultMessage().contains("activityId"));
        Map<String, String> parameters = response.getParameters();
        assertTrue(parameters.isEmpty());

        prepareInstruction(DELETE_EXTRACT).addPath(PATH).addActivityId(ACT);
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertFalse(response.getResultMessage().contains("processPath"));
        assertFalse(response.getResultMessage().contains("activityId"));
        assertTrue(response.getResultMessage().contains("not found"));
        parameters = response.getParameters();
        assertTrue(parameters.isEmpty());
    }

    @Test
    public void testGetExtract() {
        Extract ex = new Extract();
        ex.setName("ex_1");
        ExtractRule rule = new ExtractRule();
        rule.setRuleType(RuleType.VALUE);
        rule.setRule("Hello");
        rule.setInout("IN");
        ex.getExtractRules().add(rule);
        ActivityConfiguration activity = addActivityConfig(PATH, ACT);
        activity.setExtract(ex);

        prepareInstruction(GET_EXTRACT).addPath(PATH).addActivityId(ACT);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        Map<String, String> parameters = response.getParameters();
        String extract = parameters.get("extract");
        assertNotNull(extract);
        assertTrue(extract.startsWith("{"));
        assertTrue(extract.contains("Hello"));
        assertTrue(extract.contains("IN"));
        assertTrue(extract.contains(RuleType.VALUE.toString()));
    }

    @Test
    public void testGetExtractFail() {
        prepareInstruction(GET_EXTRACT);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertTrue(response.getResultMessage().contains("processPath"));
        assertTrue(response.getResultMessage().contains("activityId"));
        Map<String, String> parameters = response.getParameters();
        assertTrue(parameters.isEmpty());

        prepareInstruction(GET_EXTRACT).addPath(PATH).addActivityId(ACT);
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertFalse(response.getResultMessage().contains("processPath"));
        assertFalse(response.getResultMessage().contains("activityId"));
        assertTrue(response.getResultMessage().contains("not found"));
        parameters = response.getParameters();
        assertTrue(parameters.isEmpty());
    }

    @Test
    public void testRecord() {
        addProcessConfig(PATH);
        addProcessConfig(">test2>");
        addProcessConfig(">test3>");
        assertEquals(3, configuration.getProcesses().size());

        prepareInstruction(RECORD).addParameter("EngineWideRecording", true);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        verify(configuration).save();
        assertTrue(configuration.isRecording());
        assertTrue(configuration.getProcesses().values().stream().allMatch(ProcessConfiguration::isRecording));

        prepareInstruction(RECORD).addPath(PATH).addParameter("Record", "xxx");
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        verify(configuration, times(2)).save();
        assertFalse(configuration.getProcess(PATH).isRecording());
        assertTrue(configuration.getProcess(">test2>").isRecording());
        assertTrue(configuration.getProcess(">test3>").isRecording());

        prepareInstruction(RECORD).addPath(">test4>").addParameter("Record", "xxx");
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        verify(configuration, times(3)).save();
        assertFalse(configuration.getProcess(PATH).isRecording());
        assertTrue(configuration.getProcess(">test2>").isRecording());
        assertTrue(configuration.getProcess(">test3>").isRecording());
        assertFalse(configuration.getProcess(">test4>").isRecording());

    }

    @Test
    public void testUnhandledCommand_SDK_148() {
        final LocalDateTime time = LocalDateTime.of(2010, 12, 31, 1, 0);
        prepareInstruction(REPLAY);
        Response anotherResponse = new Response();
        anotherResponse.setDateTime(time);
        anotherResponse.setResultCode(4711);
        anotherResponse.setResultMessage("XXX");
        instruction.setResponse(anotherResponse);
        listener.onInstruction(instruction);

        verify(configuration, never()).save();

        assertEquals(anotherResponse, instruction.getResponse());
        assertEquals(4711, instruction.getResponse().getResultCode());
        assertEquals("XXX", instruction.getResponse().getResultMessage());
        assertEquals(time, instruction.getResponse().getDateTime());
    }

    @Test
    public void testExpressionTestRegex() {
        prepareInstruction(Command.TEST_EXPRESSION);
        instruction.setRequestParameter("ruleType", "regexp");
        instruction.setRequestParameter("expression", "wo\\w+");
        instruction.setRequestParameter("data", "Hello world!!!");
        listener.onInstruction(instruction);

        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("world", response.getParameters().get("matchResult"));

    }

    @Test
    public void testExpressionTestXpath() {
        prepareInstruction(Command.TEST_EXPRESSION);
        instruction.setRequestParameter("ruleType", "xpath");
        instruction.setRequestParameter("expression", "//name");
        instruction.setRequestParameter("data", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
            "<configuration scan=\"true\">\r\n" +
            "    <name>Hello world</name>\r\n" +
            "</configuration>");
        listener.onInstruction(instruction);

        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Hello world", response.getParameters().get("matchResult"));

    }

    @Test
    public void testExpressionTestValue() {
        prepareInstruction(Command.TEST_EXPRESSION);
        instruction.setRequestParameter("ruleType", "value");
        instruction.setRequestParameter("expression", "bla");
        instruction.setRequestParameter("data", "Hello world!!!");
        listener.onInstruction(instruction);

        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("bla", response.getParameters().get("matchResult"));

    }

    @Test
    public void testExpressionTestOther() {
        prepareInstruction(Command.TEST_EXPRESSION);
        instruction.setRequestParameter("ruleType", "xxx");
        instruction.setRequestParameter("expression", "bla");
        instruction.setRequestParameter("data", "Hello world!!!");

        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertNull(response.getParameters().get("matchResult"));

        instruction.setRequestParameter("ruleType", "eventType");
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertNull(response.getParameters().get("matchResult"));
    }

    @Test
    public void testExpressionTestFail() {
        prepareInstruction(Command.TEST_EXPRESSION);
        listener.onInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertNotNull(response.getResultMessage());
        assertTrue(response.getResultMessage().startsWith("missing parameter"));
        assertNull(response.getParameters().get("matchResult"));

        instruction.setRequestParameter("ruleType", "regexp");
        instruction.setRequestParameter("expression", "wo\\E");
        instruction.setRequestParameter("data", "Hello world!!!");
        listener.onInstruction(instruction);
        response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertNotNull(response.getResultMessage());
        assertTrue(response.getResultMessage().startsWith("Expression test failed"));
        assertNull(response.getParameters().get("matchResult"));
    }
}
