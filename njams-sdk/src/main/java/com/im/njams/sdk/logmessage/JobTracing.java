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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.logmessage;

/**
 * Owns the tracing flags of a {@link Job}: deep-trace (collect trace information for every
 * activity including sub processes) and the trace/instrumentation markers maintained by
 * the SDK. Obtain via {@code job.tracing()}.
 */
public class JobTracing {

    private boolean deepTrace;

    private boolean instrumented = false;
    private boolean traces;

    JobTracing() {
        // created by JobImpl only
    }

    /**
     * Marks that the job shall collect trace information for each activity
     * (including sub processes).
     *
     * @param deepTrace <b>true</b> if deep trace shall be activated.
     */
    public void setDeepTrace(boolean deepTrace) {
        this.deepTrace = deepTrace;
    }

    /**
     * Indicates that trace information shall be collected for all activities of
     * this job (including sub processes).
     *
     * @return <b>true</b> if and only if deep trace is enabled.
     */
    public boolean isDeepTrace() {
        return deepTrace;
    }

    /**
     * Returns whether any tracepoint has been triggered for this job.
     *
     * @return the traces flag
     */
    public boolean isTraces() {
        return traces;
    }

    void setTraces(boolean traces) {
        this.traces = traces;
    }

    void setInstrumented() {
        instrumented = true;
    }

    boolean isInstrumented() {
        return instrumented;
    }
}
