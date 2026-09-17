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
package com.im.njams.sdk.communication;

import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base implementation of the {@link ReplayHandler} interface that hides request/response handling from the
 * actual implementation.
 * <p>
 * Implementations have to override {@link #executeReplay(Path, String, String)} and
 * {@link #testReplay(Path, String, String)} to provide the actual replay logic.
 *
 * @author cwinkler
 * @since 4.1.3
 *
 */
public abstract class AbstractReplayHandler implements ReplayHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractReplayHandler.class);

    private static final String SUCCESS = "Success";
    private static final int SUCESS_CODE = 0;
    private static final int ERROR_CODE = -1;

    @Override
    public ReplayResponse replay(final ReplayRequest request) {
        final ReplayResponse response = new ReplayResponse();
        final Path processPath = request.getProcessPath();
        final String processName = request.getProcess();
        try {
            if (processPath == null && processName == null) {
                throw new NullPointerException("No process in request.");
            }

            if (request.getTest()) {
                LOG.debug("Test replaying {} (hasPayload={})", logProcess(processPath, processName),
                    StringUtils.isNotBlank(request.getPayload()));
                testReplay(processPath, processName, request.getPayload());
                response.setMainLogId("$$test");
            } else {
                LOG.debug("Replay process {} (hasPayload={})", logProcess(processPath, processName),
                    StringUtils.isNotBlank(request.getPayload()));
                final String logId = executeReplay(processPath, processName, request.getPayload());
                response.setMainLogId(logId);
            }

            response.setResultCode(SUCESS_CODE);
            response.setResultMessage(SUCCESS);
            response.setDateTime(DateTimeUtility.now());
        } catch (final Exception e) {
            if (request.getTest()) {
                LOG.info("Test replay failed for process {} (hasPayload={}): {}", logProcess(processPath, processName),
                    StringUtils.isNotBlank(request.getPayload()), e.toString());
            } else {
                LOG.error("Replay failed for process {} (hasPayload={})", logProcess(processPath, processName),
                    StringUtils.isNotBlank(request.getPayload()), e);
            }
            response.setResultCode(ERROR_CODE);
            response.setException(e.toString());
            response.setResultMessage(e.getMessage());
            response.setDateTime(DateTimeUtility.now());
            response.setMainLogId("n/a");
        }
        return response;
    }

    private static String logProcess(final Path processPath, final String processName) {
        return processPath != null ? processPath.toString() : processName;
    }

    private static String resolveName(final Path processPath, final String processName) {
        if (processName != null) {
            return processName;
        }
        return processPath == null ? null : processPath.getName();
    }

    /**
     * Execute a replay for the given process.
     * <p>
     * The process to replay is identified preferably by {@code processPath} — the full, unambiguous nJAMS
     * path of the process model. {@code processName} is only the last path segment and is provided as a
     * fallback for servers that do not send a path; it may be ambiguous when several processes share the
     * same name. Prefer {@code processPath} whenever it is non-{@code null}.
     * <p>
     * The default implementation delegates to {@link #executeReplay(String, String)} for backward
     * compatibility. Override this method to make use of the full process path.
     *
     * @param processPath The full path of the process that shall be replayed, or {@code null} if the
     * server did not send a path. Preferred over {@code processName} when present.
     * @param processName The name (last path segment) of the process that shall be replayed. Fallback when
     * {@code processPath} is {@code null}.
     * @param startData Optional input data for executing the process. May be <code>null</code>.
     * @return The nJAMS log-ID of the replayed job instance needs to be returned for indicating that the according
     * job has been started or has been scheduled for start.
     * @throws Exception Any error that occurred when trying to start the replayed process.
     */
    public String executeReplay(final Path processPath, final String processName, final String startData)
        throws Exception {
        return executeReplay(resolveName(processPath, processName), startData);
    }

    /**
     * Test whether or not the given process can be replayed using the given arguments. Throwing any exception indicates
     * that the test failed while completing normal indicates test success.
     * <p>
     * The process to test is identified preferably by {@code processPath} — the full, unambiguous nJAMS
     * path of the process model. {@code processName} is only the last path segment and is provided as a
     * fallback for servers that do not send a path; it may be ambiguous when several processes share the
     * same name. Prefer {@code processPath} whenever it is non-{@code null}.
     * <p>
     * The default implementation delegates to {@link #testReplay(String, String)} for backward
     * compatibility. Override this method to make use of the full process path.
     *
     * @param processPath The full path of the process that shall be replayed, or {@code null} if the
     * server did not send a path. Preferred over {@code processName} when present.
     * @param processName The name (last path segment) of the process that shall be replayed. Fallback when
     * {@code processPath} is {@code null}.
     * @param startData Optional input data for executing the process. May be <code>null</code>.
     * @throws Exception Any error indicating that the test failed.
     */
    public void testReplay(final Path processPath, final String processName, final String startData)
        throws Exception {
        testReplay(resolveName(processPath, processName), startData);
    }

    /**
     * Execute a replay according to the given arguments.
     *
     * @param processName The name of the process that shall be replayed.
     * @param startData Optional input data for executing the process. May be <code>null</code>.
     * @return The nJAMS log-ID of the replayed job instance needs to be returned for indicating that the according
     * job has been started or has been scheduled for start.
     * @throws Exception Any error that occurred when trying to start the replayed process.
     * @deprecated Override {@link #executeReplay(Path, String, String)} instead, which also receives the full
     * process {@link Path}. This method is still invoked by the default implementation of that overload for
     * backward compatibility.
     */
    @Deprecated(since = "6.0", forRemoval = true)
    public String executeReplay(final String processName, final String startData) throws Exception {
        throw new UnsupportedOperationException(
            "Override executeReplay(Path, String, String) or the deprecated executeReplay(String, String)");
    }

    /**
     * Test whether or not the given process can be replayed using the given arguments. Throwing any exception indicates
     * that the test failed while completing normal indicates test success.
     *
     * @param processName The name of the process that shall be replayed.
     * @param startData Optional input data for executing the process. May be <code>null</code>.
     * @throws Exception Any error indicating that the test failed.
     * @deprecated Override {@link #testReplay(Path, String, String)} instead, which also receives the full
     * process {@link Path}. This method is still invoked by the default implementation of that overload for
     * backward compatibility.
     */
    @Deprecated(since = "6.0", forRemoval = true)
    public void testReplay(final String processName, final String startData) throws Exception {
        throw new UnsupportedOperationException(
            "Override testReplay(Path, String, String) or the deprecated testReplay(String, String)");
    }
}
