/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.adapter.messageformat.command.entity.condition;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.utils.JsonUtils;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

public class ConditionInstructionTest {


    public static final String EXTRACT_KEY = "extract";
    public static final Extract EXTRACT = new Extract();
    public static final String EXTRACT_VALUE = serialize(EXTRACT);

    public static final String LOG_LEVEL_KEY = "logLevel";
    public static final LogLevel LOG_LEVEL = LogLevel.INFO;
    public static final String LOG_LEVEL_VALUE = LOG_LEVEL.name();

    public static final String LOG_MODE_KEY = "logMode";
    public static final LogMode LOG_MODE = LogMode.COMPLETE;
    public static final String LOG_MODE_VALUE = LOG_MODE.name();

    public static final String EXCLUDE_KEY = "exclude";
    public static final boolean EXCLUDE = true;
    public static final String EXCLUDE_VALUE = String.valueOf(EXCLUDE);

    public static final String START_TIME_KEY = "starttime";
    public static final LocalDateTime START_TIME = DateTimeUtility.now();
    public static final String START_TIME_VALUE = DateTimeUtility.toString(START_TIME);

    public static final String END_TIME_KEY = "endtime";
    public static final LocalDateTime END_TIME = DateTimeUtility.now().plusMinutes(15);
    public static final String END_TIME_VALUE = DateTimeUtility.toString(END_TIME);

    public static final String ITERATIONS_KEY = "iterations";
    public static final int ITERATIONS = 10;
    public static final String ITERATIONS_VALUE = String.valueOf(10);

    public static final String DEEP_TRACE_KEY = "deepTrace";
    public static final boolean DEEP_TRACE = true;
    public static final String DEEP_TRACE_VALUE = String.valueOf(DEEP_TRACE);

    public static final String ENGINE_WIDE_RECORDING_KEY = "EngineWideRecording";
    public static final boolean ENGINE_WIDE_RECORDING = false;
    public static final String ENGINE_WIDE_RECORDING_VALUE = String.valueOf(ENGINE_WIDE_RECORDING);

    public static final String PROCESS_RECORDING_KEY = "Record";
    public static final boolean PROCESS_RECORDING = true;
    public static final String PROCESS_RECORDING_VALUE = String.valueOf(PROCESS_RECORDING);

    public static final String ENABLE_TRACING_KEY = "enableTracing";
    public static final boolean ENABLE_TRACING = true;
    public static final String ENABLE_TRACING_VALUE = String.valueOf(ENABLE_TRACING);

    static String serialize(Object o) {
        try {
            return JsonUtils.serialize(o);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    //Constructor test

    @Test
    public void constructorCreatesCorrectReaderAndWriter(){
        ConditionInstruction conditionInstruction = spy(new ConditionInstruction(mock(Instruction.class)));
        assertTrue(conditionInstruction.getRequestReader() instanceof ConditionRequestReader);
        assertTrue(conditionInstruction.getResponseWriter() instanceof ConditionResponseWriter);
    }
}