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
package com.im.njams.sdk.communication;

import java.util.HashMap;
import java.util.Map;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;

/**
 * Request for the replay command
 *
 * @author pnientiedt
 */
public class ReplayRequest {

    private static final String PARAM_START_ACTIVITY = "StartActivity";
    private static final String PARAM_TEST = "Test";
    private static final String PARAM_DEEPTRACE = "DeepTrace";
    private static final String PARAM_PAYLOAD = "Payload";
    private static final String PARAM_PROCESS = "Process";

    private String process;
    private String activity;
    private String payload;
    private boolean deepTrace;
    private boolean test;
    private Map<String, String> parameters = new HashMap<>();

    /**
     * Create new ReplayRequest from a given Instruction
     *
     * @param instruction Instruction which contains a replay command
     */
    public ReplayRequest(Instruction instruction) {
        process = instruction.getRequestParameterByName(PARAM_PROCESS);
        payload = instruction.getRequestParameterByName(PARAM_PAYLOAD);
        String param = instruction.getRequestParameterByName(PARAM_TEST);
        test = param == null ? false : Boolean.valueOf(param);
        param = instruction.getRequestParameterByName(PARAM_DEEPTRACE);
        deepTrace = param == null ? false : Boolean.valueOf(param);
        param = instruction.getRequestParameterByName(PARAM_START_ACTIVITY);
        activity = param == null ? null : param;
        parameters.putAll(instruction.getRequest().getParameters());
    }

    /**
     * @return the process
     */
    public String getProcess() {
        return process;
    }

    /**
     * @param process the process to set
     */
    public void setProcess(String process) {
        this.process = process;
    }

    /**
     * @return the activity
     */
    public String getActivity() {
        return activity;
    }

    /**
     * @param activity the activity to set
     */
    public void setActivity(String activity) {
        this.activity = activity;
    }

    /**
     * @return the data
     */
    public String getPayload() {
        return payload;
    }

    /**
     * @param data the data to set
     */
    public void setPayload(String data) {
        payload = data;
    }

    /**
     * @return the deepTrace
     */
    public boolean getDeepTrace() {
        return deepTrace;
    }

    /**
     * @param deepTrace the deepTrace to set
     */
    public void setDeepTrace(boolean deepTrace) {
        this.deepTrace = deepTrace;
    }

    /**
     * @return the test
     */
    public boolean getTest() {
        return test;
    }

    /**
     * @param test the test to set
     */
    public void setTest(boolean test) {
        this.test = test;
    }

    /**
     * @return the parameters
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * @param parameters the parameters to set
     */
    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }
}
