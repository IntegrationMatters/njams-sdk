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
package com.im.njams.sdk.communication_rework.instruction.control.processor.replay;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Response for the replay command
 *
 * @author pnientiedt
 */
public class ReplayResponse {

    private int resultCode;
    private String resultMessage;
    private String exception;
    private String mainLogId;
    private LocalDateTime dateTime;
    private Map<String, String> parameters = new HashMap<>();

    /**
     * Adds all parameters of this ReplayResponse to the given Instruction
     *
     * @param instruction which where parameters should be added
     */
    public void addParametersToInstruction(Instruction instruction) {
        instruction.setResponseResultCode(getResultCode());
        instruction.setResponseResultMessage(getResultMessage());
        instruction.setResponseParameter("Exception", getException());
        instruction.setResponseParameter("MainLogId", getMainLogId());
        instruction.getResponse().setDateTime(dateTime);
        instruction.getResponse().getParameters().putAll(getParameters());
    }

    /**
     * @return the resultCode
     */
    public int getResultCode() {
        return resultCode;
    }

    /**
     * @param resultCode the resultCode to set
     */
    public void setResultCode(int resultCode) {
        this.resultCode = resultCode;
    }

    /**
     * @return the resultMessage
     */
    public String getResultMessage() {
        return resultMessage;
    }

    /**
     * @param resultMessage the resultMessage to set
     */
    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    /**
     * @return the exception
     */
    public String getException() {
        return exception;
    }

    /**
     * @param exception the exception to set
     */
    public void setException(String exception) {
        this.exception = exception;
    }

    /**
     * @return the mainLogId
     */
    public String getMainLogId() {
        return mainLogId;
    }

    /**
     * @param mainLogId the mainLogId to set
     */
    public void setMainLogId(String mainLogId) {
        this.mainLogId = mainLogId;
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

    /**
     * @return the dateTime
     */
    public LocalDateTime getDateTime() {
        return dateTime;
    }

    /**
     * @param dateTime the dateTime to set
     */
    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }
}
