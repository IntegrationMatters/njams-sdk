/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
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

    // Flat size estimate (in characters) for the subprocess reference (name + path + logId and
    // their JSON structure). A constant average is used rather than measuring each field, since
    // these values are bounded and exact counting is not worth the cost on this path.
    static final long SUBPROCESS_ESTIMATED_SIZE = 200L;

    private boolean subProcessSizeCounted;

    public SubProcessActivityImpl(JobImpl job, SubProcessActivityModel model) {
        super(job, model);
    }

    /**
     * Create SubProcessActivityImpl with given JobImpl
     *
     * @param subProcess ProcessModel of the subprocess
     * @param job Job which contains this SubProcessActivityImpl
     * @param model The activity model for this instance.
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

    /**
     * Sets the subprocess reference and, the first time a non-null reference is set, adds a flat
     * constant ({@value #SUBPROCESS_ESTIMATED_SIZE}) to the estimated size to account for the
     * subprocess name, path and logId. All other {@code setSubProcess} overloads funnel through
     * this method, so the constant is counted exactly once per subprocess activity.
     *
     * @param subProcess the subprocess reference to set
     */
    @Override
    public void setSubProcess(com.faizsiegeln.njams.messageformat.v4.common.SubProcess subProcess) {
        super.setSubProcess(subProcess);
        if (subProcess != null && !subProcessSizeCounted) {
            subProcessSizeCounted = true;
            addToEstimatedSize(SUBPROCESS_ESTIMATED_SIZE);
        }
    }

}
