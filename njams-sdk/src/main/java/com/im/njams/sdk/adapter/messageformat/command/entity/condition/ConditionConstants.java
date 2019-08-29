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
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.adapter.messageformat.command.entity.condition;

public interface ConditionConstants {

    //Client tier
    /**
     * Parameter key for getting or setting the LogMode
     */
    String LOG_MODE_KEY = "logMode";

    /**
     * Parameter key for getting or setting the EngineWideRecording
     */
    String ENGINE_WIDE_RECORDING_KEY = "EngineWideRecording";

    //Process tier
    /**
     * Parameter key for getting the ProcessPath
     */
    String PROCESS_PATH_KEY = "processPath";

    /**
     * Parameter key for getting or setting the LogLevel
     */
    String LOG_LEVEL_KEY = "logLevel";

    /**
     * Parameter key for getting or setting Excluded
     */
    String EXCLUDE_KEY = "exclude";

    /**
     * Parameter key for getting or setting the ProcessRecording
     */
    String PROCESS_RECORDING_KEY = "Record";

    //Activity tier
    /**
     * Parameter key for getting the ActivityId
     */
    String ACTIVITY_ID_KEY = "activityId";

    //Extract tier
    /**
     * Parameter key for getting or setting the Extract
     */
    String EXTRACT_KEY = "extract";

    //TracePoint tier
    /**
     * Parameter key for getting or setting the StartTime
     */
    String START_TIME_KEY = "starttime";

    /**
     * Parameter key for getting or setting the EndTime
     */
    String END_TIME_KEY = "endtime";

    /**
     * Parameter key for getting or setting the Iterations
     */
    String ITERATIONS_KEY = "iterations";

    /**
     * Parameter key for getting or setting DeepTrace
     */
    String DEEP_TRACE_KEY = "deepTrace";

    /**
     * Parameter key for knowing if tracing should be enabled;
     */
    String ENABLE_TRACING_KEY = "enableTracing";
}
