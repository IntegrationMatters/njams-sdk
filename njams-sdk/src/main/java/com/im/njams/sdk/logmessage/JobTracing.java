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
 * activity including sub processes), the instrumentation marker, and the trace marker maintained
 * by the SDK. Obtain via {@code job.tracing()}.
 */
public class JobTracing {

    // volatile: a Job is shared across threads (parallel activities update these flags); volatile
    // gives correct visibility without locking. Each flag is independent, so no atomicity is needed.
    private volatile boolean deepTrace;

    private volatile boolean instrumented = false;
    private volatile boolean traces;

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

    /**
     * Marks this job as instrumented, i.e., as actively handled by the client implementation.
     * <p>
     * If the log mode is {@link com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode#EXCLUSIVE EXCLUSIVE},
     * only jobs that are marked as instrumented are sent to nJAMS Server; a job that is not instrumented is
     * suppressed when it is flushed. In any other log mode this flag has no effect on whether the job is sent.
     * <p>
     * The SDK marks a job as instrumented on its own whenever one of its activities gets an event status
     * (success, warning, or error) or a non-blank event message, event code, event payload, or stack trace, and
     * whenever an extract rule is applied. Client implementations that record other relevant events must call this
     * method themselves. Once set, the flag cannot be cleared again.
     */
    public void setInstrumented() {
        instrumented = true;
    }

    /**
     * Returns whether this job has been marked as instrumented, either by the client implementation or by the SDK.
     * See {@link #setInstrumented()} for the meaning of this flag.
     *
     * @return <b>true</b> if and only if this job is marked as instrumented.
     */
    public boolean isInstrumented() {
        return instrumented;
    }
}
