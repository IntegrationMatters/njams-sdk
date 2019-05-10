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
package com.im.njams.sdk.logmessage;

import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.SubProcessActivityModel;

/**
 * Implementation of the SubProcessActivity interface
 *
 * @author pnientiedt
 */
public class SubProcessActivityImpl extends GroupImpl implements SubProcessActivity {

    /**
     * Create SubProcessActivityImpl with given JobImpl
     * @deprecated SDK-140
     * @param job Job which contains this SubProcessActivityImpl
     */
    @Deprecated
    public SubProcessActivityImpl(JobImpl job, String modelId) {
        super(job, modelId);
    }

    public SubProcessActivityImpl(JobImpl job, SubProcessActivityModel model) {
        super(job, model);
    }

    /**
     * Create SubProcessActivityImpl with given JobImpl
     *
     * @param subProcess ProcessModel of the subprocess
     * @param job Job which contains this SubProcessActivityImpl
     */
    public SubProcessActivityImpl(ProcessModel subProcess, JobImpl job, SubProcessActivityModel model) {
        super(job, model);
        setSubProcess(new com.faizsiegeln.njams.messageformat.v4.common.SubProcess(subProcess.getName(), subProcess
                .getPath().toString(), null));
    }

    /**
     * Set the subprocess by adding a ProcessModel for that subprocess
     *
     * @param subProcess ProcessModel for subprocess
     */
    @Override
    public void setSubProcess(ProcessModel subProcess) {
        setSubProcess(new com.faizsiegeln.njams.messageformat.v4.common.SubProcess(subProcess.getName(), subProcess
                .getPath().toString(), null));
    }

    /**
     * Set the subprocess by adding all parameters for that subprocess. Name and Path are needed if it is a inline supprocess. LogId is needed if it is a spawned subprocess
     *
     * @param subProcessName Name of the subprocess
     * @param subProcessPath Path of the subprocess
     * @param subProcessLogId LogId of the subprocess
     */
    @Override
    public void setSubProcess(String subProcessName, String subProcessPath, String subProcessLogId) {
        setSubProcess(new com.faizsiegeln.njams.messageformat.v4.common.SubProcess(subProcessName, subProcessPath,
                subProcessLogId));
    }

}
