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
package com.im.njams.sdk.plugin.replay.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.api.plugin.replay.ReplayRequest;
import com.im.njams.sdk.communication.instruction.util.InstructionWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Request for the replay command
 *
 * @author pnientiedt
 */
public class NjamsReplayRequest implements ReplayRequest {

    private static final String PARAM_START_ACTIVITY = "StartActivity";
    private static final String PARAM_TEST = "Test";
    private static final String PARAM_DEEPTRACE = "Deeptrace";
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
    public NjamsReplayRequest(Instruction instruction) {
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

    public NjamsReplayRequest(InstructionWrapper instructionWrapper) {
        if (instructionWrapper != null) {
            tryToLoadFieldsFromRequest(instructionWrapper.getRequest());
        }
    }

    private void tryToLoadFieldsFromRequest(Request request) {
        if (request != null) {
            extractFieldsFromParameters(request.getParameters());
        }
    }

    private void extractFieldsFromParameters(Map<String, String> parametersToExtract) {
        if(parametersToExtract != null) {
            extractClientProcess(parametersToExtract);
            extractPayload(parametersToExtract);
            extractTest(parametersToExtract);
            extractDeepTrace(parametersToExtract);
            extractActivity(parametersToExtract);
            extractRemainingParameters(parametersToExtract);
        }
    }

    private void extractRemainingParameters(Map<String, String> parametersToExtract) {
        this.parameters.putAll(parametersToExtract);
    }

    private void extractActivity(Map<String, String> parametersToExtract) {
        activity = parametersToExtract.get(PARAM_START_ACTIVITY);;
    }

    private void extractDeepTrace(Map<String, String> parametersToExtract) {
        String deepTraceAsString = parametersToExtract.get(PARAM_DEEPTRACE);
        deepTrace = deepTraceAsString == null ? false : Boolean.valueOf(deepTraceAsString);
    }

    private void extractTest(Map<String, String> parametersToExtract) {
        String testAsString = parametersToExtract.get(PARAM_TEST);
        test = testAsString == null ? false : Boolean.valueOf(testAsString);
    }

    private void extractPayload(Map<String, String> parametersToExtract) {
        payload = parametersToExtract.get(PARAM_PAYLOAD);
    }

    private void extractClientProcess(Map<String, String> parametersToExtract) {
        process = parametersToExtract.get(PARAM_PROCESS);
    }

    /**
     * @return the process
     */
    @Override
    public String getProcess() {
        return process;
    }

    /**
     * @param process the process to set
     */
    @Override
    public void setProcess(String process) {
        this.process = process;
    }

    /**
     * @return the activity
     */
    @Override
    public String getActivity() {
        return activity;
    }

    /**
     * @param activity the activity to set
     */
    @Override
    public void setActivity(String activity) {
        this.activity = activity;
    }

    /**
     * @return the data
     */
    @Override
    public String getPayload() {
        return payload;
    }

    /**
     * @param data the data to set
     */
    @Override
    public void setPayload(String data) {
        this.payload = data;
    }

    /**
     * @return the deepTrace
     */
    @Override
    public boolean isDeepTrace() {
        return deepTrace;
    }

    /**
     * @param deepTrace the deepTrace to set
     */
    @Override
    public void setDeepTrace(boolean deepTrace) {
        this.deepTrace = deepTrace;
    }

    /**
     * @return the parameters
     */
    @Override
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * @param parameters the parameters to set
     */
    @Override
    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
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
}
