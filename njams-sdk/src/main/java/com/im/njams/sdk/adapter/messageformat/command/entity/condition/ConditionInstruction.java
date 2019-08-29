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
import com.im.njams.sdk.adapter.messageformat.command.entity.AbstractInstruction;

public class ConditionInstruction extends AbstractInstruction<ConditionRequestReader, ConditionResponseWriter> {

    public static final String PROCESS_PATH = "processPath";
    public static final String ACTIVITY_ID = "activityId";
    public static final String EXTRACT = "extract";
    public static final String LOG_LEVEL = "logLevel";
    public static final String LOG_MODE = "logMode";
    public static final String EXCLUDE = "exclude";
    public static final String START_TIME = "starttime";
    public static final String END_TIME = "endtime";
    public static final String ITERATIONS = "iterations";
    public static final String DEEP_TRACE = "deepTrace";
    public static final String ENGINE_WIDE_RECORDING = "EngineWideRecording";
    public static final String PROCESS_RECORDING = "Record";
    public static final String ENABLE_TRACING = "enableTracing";

    public ConditionInstruction(Instruction messageFormatInstruction) {
        super(messageFormatInstruction);
    }

    @Override
    protected ConditionRequestReader createRequestReaderInstance(Request request) {
        return new ConditionRequestReader(request);
    }

    @Override
    protected ConditionResponseWriter createResponseWriterInstance(Response response) {
        return new ConditionResponseWriter(response);
    }

}
