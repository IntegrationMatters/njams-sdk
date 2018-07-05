/* 
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * The Software shall be used for Good, not Evil.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.common;

import java.util.UUID;

/**
 * Util for generating or constructing different nJAMS IDs
 *
 * @author pnientiedt
 */
public class IdUtil {

    private IdUtil() {
        // private constructor to avoid instances
    }

    /**
     * Creates a logId which is used to identify a process instance.
     *
     * @return the generated LogId
     */
    public static String createLogId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Get a transitionId from the Activity IDs of start and end of the transition.
     *
     * @param fromActivityModelId
     *            the id of the Activity where Transition starts.
     * @param toActivityModelId
     *            the id of the Activity where Transition ends.
     * @return the constructed id
     */
    public static String getTransitionModelId(String fromActivityModelId, String toActivityModelId) {
        return new StringBuilder().append(fromActivityModelId).append("::").append(toActivityModelId).toString();
    }

    /**
     * Get an Activity instanceId from a modelId and a sequence value
     * 
     * @param modelId
     *            the modelId
     * @param sequence
     *            the unique sequence value
     * @return the constructed id
     */
    public static String getActivityInstanceId(String modelId, long sequence) {
        return new StringBuilder().append(modelId).append("$").append(sequence).toString();
    }
}
