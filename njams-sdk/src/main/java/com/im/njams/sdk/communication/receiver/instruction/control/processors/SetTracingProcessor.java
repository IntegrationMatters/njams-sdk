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
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.receiver.instruction.control.processors;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionConstants;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.communication.receiver.instruction.control.templates.condition.ConditionWriterTemplate;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.TracepointExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Todo: Write Doc
 */
public class SetTracingProcessor extends ConditionWriterTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(SetTracingProcessor.class);

    private static final String[] neededParameter = new String[]{ConditionConstants.PROCESS_PATH_KEY,
            ConditionConstants.ACTIVITY_ID_KEY};

    private static final int DEFAULT_TRACING_ENABLED_IN_MINUTES = 15;

    private static final int DEFAULT_ITERATIONS = 0;

    private LocalDateTime endTimeOfNewTracePoint;

    private boolean isTracePointUseful;

    private boolean isTracingEnabled;

    public SetTracingProcessor(Njams njams) {
        super(njams);
    }

    @Override
    protected String[] getEssentialParametersForProcessing() {
        return neededParameter;
    }

    @Override
    protected void configureCondition() throws NjamsInstructionException {

        endTimeOfNewTracePoint = getEndTime();

        isTracePointUseful = endTimeOfNewTracePoint.isAfter(DateTimeUtility.now());

        isTracingEnabled = requestReader.getTracingEnabled();

        if (isTracingEnabled && isTracePointUseful) {
            updateTracePoint();
        } else {
            deleteTracePoint();
        }
    }

    private LocalDateTime getEndTime() throws NjamsInstructionException {
        LocalDateTime endTime = requestReader.getEndTime();
        if (endTime == null) {
            endTime = DateTimeUtility.now().plusMinutes(DEFAULT_TRACING_ENABLED_IN_MINUTES);
        }
        return endTime;
    }

    void updateTracePoint() throws NjamsInstructionException {
        TracepointExt tracePointToSet = createTracePointFromRequest();

        ActivityConfiguration activityCondition = conditionFacade.getOrCreateActivityCondition();

        activityCondition.setTracepoint(tracePointToSet);
    }

    private TracepointExt createTracePointFromRequest() throws NjamsInstructionException {
        final TracepointExt tp = new TracepointExt();
        tp.setStarttime(getStartTime());
        tp.setEndtime(endTimeOfNewTracePoint);
        tp.setIterations(getIterations());
        tp.setDeeptrace(getDeepTrace());
        return tp;
    }

    private LocalDateTime getStartTime() throws NjamsInstructionException {
        LocalDateTime startTime = requestReader.getStartTime();
        if (startTime == null) {
            startTime = DateTimeUtility.now();
        }
        return startTime;
    }

    private Integer getIterations() {
        Integer iterations = DEFAULT_ITERATIONS;
        try {
            iterations = requestReader.getIterations();
        } catch (NjamsInstructionException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error(e.getMessage(), e.getCause());
            }
        }
        return iterations;
    }

    private Boolean getDeepTrace() {
        return requestReader.getDeepTrace();
    }

    void deleteTracePoint() throws NjamsInstructionException {

        ActivityConfiguration activityCondition = conditionFacade.getActivityCondition();

        activityCondition.setTracepoint(null);
    }

    @Override
    protected void logProcessingSuccess() {
        String updateOrDelete = isTracingEnabled && isTracePointUseful ? "Updated" : "Deleted";
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} Tracepoint of {}#{}.", updateOrDelete, requestReader.getProcessPath(),
                    requestReader.getActivityId());
        }
    }
}
