/*
 * Copyright (c) 2020 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication;

import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.utils.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base implementation of the {@link ReplayHandler} interface that hides request/response handling from the
 * actual implementation.
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
        final String process = request.getProcess();
        try {
            if (process == null) {
                throw new NullPointerException("No process name in request.");
            }

            if (request.getTest()) {
                LOG.debug("Test replaying {} (hasPayload={})", process, StringUtils.isNotBlank(request.getPayload()));
                testReplay(process, request.getPayload());
                response.setMainLogId("$$test");
            } else {
                LOG.debug("Replay process {} (hasPayload={})", process, StringUtils.isNotBlank(request.getPayload()));
                final String logId = executeReplay(process, request.getPayload());
                response.setMainLogId(logId);
            }

            response.setResultCode(SUCESS_CODE);
            response.setResultMessage(SUCCESS);
            response.setDateTime(DateTimeUtility.now());
        } catch (final Exception e) {
            if (request.getTest()) {
                LOG.info("Test replay failed for process {} (hasPayload={}): {}", process,
                    StringUtils.isNotBlank(request.getPayload()), e.toString());
            } else {
                LOG.error("Replay failed for process {} (hasPayload={})", process,
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

    /**
     * Execute a replay according to the given arguments.
     *
     * @param processName The name of the process that shall be replayed.
     * @param startData Optional input data for executing the process. May be <code>null</code>.
     * @return The nJAMS log-ID of the replayed job instance needs to be returned for indicating that the according
     * job has been started or has been scheduled for start.
     * @throws Exception Any error that occurred when trying to start the replayed process.
     */
    public abstract String executeReplay(String processName, String startData) throws Exception;

    /**
     * Test whether or not the given process can be replayed using the given arguments. Throwing any exception indicates
     * that the test failed while completing normal indicates test success.
     *
     * @param processName The name of the process that shall be replayed.
     * @param startData Optional input data for executing the process. May be <code>null</code>.
     * @throws Exception Any error indicating that the test failed.
     */
    public abstract void testReplay(String processName, String startData) throws Exception;
}
