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

package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Request;

public class ReplayRequestReader extends DefaultRequestReader {

    private static final String PROCESS = "Process";
    private static final String START_ACTIVITY = "StartActivity";
    private static final String PAYLOAD = "Payload";
    private static final String DEEPTRACE = "Deeptrace";
    private static final String TEST = "Test";

    public ReplayRequestReader(Request requestToRead) {
        super(requestToRead);
    }

    public String getProcess() {
        return getParameter(PROCESS);
    }

    public String getStartActivity() {
        return getParameter(START_ACTIVITY);
    }

    public String getPayload() {
        return getParameter(PAYLOAD);
    }

    public boolean isDeepTrace() {
        return Boolean.valueOf(getParameter(DEEPTRACE));
    }

    public boolean getTest() {
        return Boolean.valueOf(getParameter(TEST));
    }
}
