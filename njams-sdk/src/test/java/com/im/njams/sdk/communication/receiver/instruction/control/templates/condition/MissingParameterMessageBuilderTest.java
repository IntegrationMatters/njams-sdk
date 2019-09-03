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

package com.im.njams.sdk.communication.receiver.instruction.control.templates.condition;

import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionConstants;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.im.njams.sdk.communication.receiver.instruction.control.templates.condition.MissingParameterMessageBuilder.MISSING_PARAMETER_MESSAGE;
import static java.util.Collections.EMPTY_LIST;
import static org.junit.Assert.assertEquals;

public class MissingParameterMessageBuilderTest {

    private static final Logger LOG = LoggerFactory.getLogger(MissingParameterMessageBuilderTest.class);

    @Test
    public void setInvalidParameterResponseWithoutMissingParameters() {
        List<String> missingParameters = EMPTY_LIST;
        String message = new MissingParameterMessageBuilder(missingParameters).build();
        assertPluralMessage(missingParameters, message);
    }

    @Test
    public void setInvalidParameterResponseWithOneMissingParameter() {
        List<String> missingParameters = new ArrayList<>();
        fillMissingParametersWith(missingParameters, ConditionConstants.PROCESS_PATH_KEY);
        String message = new MissingParameterMessageBuilder(missingParameters).build();
        assertSingularMessage(missingParameters, message);
    }

    @Test
    public void setInvalidParameterResponseWithMoreThanOneMissingParameter() {
        List<String> missingParameters = new ArrayList<>();
        fillMissingParametersWith(missingParameters, ConditionConstants.PROCESS_PATH_KEY,
                ConditionConstants.ACTIVITY_ID_KEY);
        String message = new MissingParameterMessageBuilder(missingParameters).build();
        assertPluralMessage(missingParameters, message);
    }

    private void fillMissingParametersWith(List<String> missingParameters, String... parameters) {
        Arrays.stream(parameters).forEach(conditionParameter -> missingParameters.add(conditionParameter));
    }

    private void assertSingularMessage(List<String> parameters, String actualMessage) {
        LOG.debug(actualMessage);
        assertEquals(MISSING_PARAMETER_MESSAGE + ": " + parameters.toString(), actualMessage);

    }

    private void assertPluralMessage(List<String> parameters, String actualMessage) {
        LOG.debug(actualMessage);
        assertEquals(MISSING_PARAMETER_MESSAGE + "s" + ": " + parameters.toString(), actualMessage);
    }
}