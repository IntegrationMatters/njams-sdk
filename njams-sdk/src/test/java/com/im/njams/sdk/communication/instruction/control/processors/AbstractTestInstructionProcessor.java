/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.instruction.control.processors;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.utils.JsonUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

public class AbstractTestInstructionProcessor {

    protected TestInstructionBuilder instructionBuilder;

    public AbstractTestInstructionProcessor() {
        instructionBuilder = new TestInstructionBuilder();
    }

    public static class TestInstructionBuilder {

        public static final String PROCESSPATH_KEY = "processPath";

        public static final String PROCESSPATH_VALUE = ">test>";

        public static final String ACTIVITYID_KEY = "activityId";

        public static final String ACTIVITYID_VALUE = "act_1";

        public static final String EXTRACT_KEY = "extract";

        public static final String EXTRACT_VALUE = "Extract";

        public static final String LOG_LEVEL_KEY = "logLevel";

        public static final LogLevel LOG_LEVEL_VALUE = LogLevel.INFO;

        public static final String LOG_MODE_KEY = "logMode";

        public static final LogMode LOG_MODE_VALUE = LogMode.COMPLETE;

        public static final String EXCLUDED_KEY = "exclude";

        public static final String EXCLUDED_VALUE = "true";

        public static final String ENABLE_TRACING_KEY = "enableTracing";

        public static final boolean ENABLE_TRACING_VALUE = true;

        public static final String START_TIME_KEY = "starttime";
        public static final LocalDateTime START_TIME_VALUE = DateTimeUtility.now();

        public static final String END_TIME_KEY = "endtime";
        public static final LocalDateTime END_TIME_VALUE = START_TIME_VALUE.plusMinutes(15);

        public static final String ITERATIONS_KEY = "iterations";
        public static final int ITERATIONS_VALUE = 5;

        public static final String DEEP_TRACE_KEY = "deepTrace";
        public static final boolean DEEP_TRACE_VALUE = true;

        public static final String ENGINE_WIDE_RECORDING_KEY = "EngineWideRecording";
        public static final boolean ENGINE_WIDE_RECORDING_VALUE = true;

        public static final String RECORDING_KEY = "Record";
        public static final String RECORDING_VALUE = "all";

        private Instruction instruction;

        public TestInstructionBuilder prepareInstruction(Command command) {
            Instruction instruction = new Instruction();
            Request request = new Request();
            instruction.setRequest(request);
            request.setCommand(command.commandString());
            Map<String, String> parameters = new TreeMap<>();
            request.setParameters(parameters);
            this.instruction = instruction;
            return this;
        }

        public TestInstructionBuilder prepareGetLogLevelInstruction() {
            return prepareInstruction(Command.GET_LOG_LEVEL);
        }

        public TestInstructionBuilder addDefaultPath() {
            return addPath(PROCESSPATH_VALUE);
        }

        public TestInstructionBuilder addPath(String path) {
            return addParameter(PROCESSPATH_KEY, path);
        }

        public TestInstructionBuilder addDefaultActivityId() {
            return addActivityId(ACTIVITYID_VALUE);
        }

        public TestInstructionBuilder addActivityId(String id) {
            return addParameter(ACTIVITYID_KEY, id);
        }

        public TestInstructionBuilder addDefaultExtract() throws JsonProcessingException {
            return addExtract("ex_1", RuleType.VALUE, "Hello", "IN");
        }

        public TestInstructionBuilder addExtract(String name, RuleType type, String rule, String inout)
                throws JsonProcessingException {
            Extract ex = new Extract();
            ex.setName(name);
            ExtractRule extractRule = new ExtractRule();
            extractRule.setRuleType(type);
            extractRule.setRule(rule);
            extractRule.setInout(inout);
            ex.getExtractRules().add(extractRule);
            return addParameter(EXTRACT_KEY, JsonUtils.serialize(ex));
        }

        public TestInstructionBuilder addDefaultLogLevel() {
            return addLogLevel(LOG_LEVEL_VALUE.name());
        }

        public TestInstructionBuilder addLogLevel(String logLevelValue) {
            return addParameter(LOG_LEVEL_KEY, logLevelValue);
        }

        public TestInstructionBuilder addDefaultLogMode() {
            return addLogMode(LOG_MODE_VALUE.name());
        }

        public TestInstructionBuilder addLogMode(String logModeValue) {
            return addParameter(LOG_MODE_KEY, logModeValue);
        }

        public TestInstructionBuilder addDefaultExcluded() {
            return addExcluded(String.valueOf(EXCLUDED_VALUE));
        }

        public TestInstructionBuilder addExcluded(String isExcluded) {
            return addParameter(EXCLUDED_KEY, isExcluded);
        }

        public TestInstructionBuilder addDefaultStartTime() {
            return addStartTime(DateTimeUtility.toString(START_TIME_VALUE));
        }

        public TestInstructionBuilder addStartTime(String startTime) {
            return addParameter(START_TIME_KEY, startTime);
        }

        public TestInstructionBuilder addDefaultEndTime() {
            return addEndTime(DateTimeUtility.toString(END_TIME_VALUE));
        }

        public TestInstructionBuilder addEndTime(String endtime) {
            return addParameter(END_TIME_KEY, endtime);
        }

        public TestInstructionBuilder addDefaultIterations() {
            return addIterations(String.valueOf(ITERATIONS_VALUE));
        }

        public TestInstructionBuilder addIterations(String iterations) {
            return addParameter(ITERATIONS_KEY, iterations);
        }

        public TestInstructionBuilder addDefaultDeepTrace() {
            return addDeepTrace(String.valueOf(DEEP_TRACE_VALUE));
        }

        public TestInstructionBuilder addDeepTrace(String deepTrace) {
            return addParameter(DEEP_TRACE_KEY, deepTrace);
        }

        public TestInstructionBuilder addDefaultEnableTracing() {
            return addEnableTracing(String.valueOf(ENABLE_TRACING_VALUE));
        }

        public TestInstructionBuilder addEnableTracing(String enableTracing) {
            return addParameter(ENABLE_TRACING_KEY, enableTracing);
        }

        public TestInstructionBuilder setDefaultEngineWideRecording() {
            return setEngineWideRecording(String.valueOf(ENGINE_WIDE_RECORDING_VALUE));
        }

        public TestInstructionBuilder setEngineWideRecording(String setEngineWideRecording) {
            return addParameter(ENGINE_WIDE_RECORDING_KEY, setEngineWideRecording);
        }

        public TestInstructionBuilder setDefaultRecording() {
            return setRecording(String.valueOf(RECORDING_VALUE));
        }

        public TestInstructionBuilder setRecording(String setRecording) {
            return addParameter(RECORDING_KEY, setRecording);
        }

        public TestInstructionBuilder addParameter(String name, Object value) {
            String s;
            if (value instanceof LocalDateTime) {
                s = DateTimeUtility.toString((LocalDateTime) value);
            } else {
                s = String.valueOf(value);
            }
            instruction.getRequest().getParameters().put(name, s);
            return this;
        }

        public Instruction build() {
            return instruction;
        }
    }
}
