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
package com.im.njams.sdk.communication.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ExtractRule;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Tracepoint;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.control.NjamsInstructionWrapper;
import com.im.njams.sdk.api.adapter.messageformat.command.exceptions.NjamsInstructionException;
import com.im.njams.sdk.api.communication.instruction.control.InstructionProcessor;
import com.im.njams.sdk.communication.instruction.control.processor.AbstractTestInstructionProcessor;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.Configuration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import com.im.njams.sdk.configuration.entity.TracepointExt;
import org.junit.Before;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class AbstractConfigurationProcessorHelper extends AbstractTestInstructionProcessor {

    protected Njams njamsMock;

    protected Configuration configuration;

    @Before
    public void setNewNjamsMock(){
        njamsMock = mock(Njams.class);
        configuration = new Configuration();
    }

    protected ProcessConfiguration addProcessConfig(String path) {
        ProcessConfiguration process = new ProcessConfiguration();
        configuration.getProcesses().put(path, process);
        return process;
    }

    protected ActivityConfiguration addActivityToProcessConfig(ProcessConfiguration process, String path){
        ActivityConfiguration activityConfiguration = new ActivityConfiguration();
        process.getActivities().put(path, activityConfiguration);
        return activityConfiguration;
    }

    protected Tracepoint addTracePointToActivityConfig(ActivityConfiguration activityConfiguration, int iterations, LocalDateTime startTime, LocalDateTime endTime, boolean isDeeptrace){
        TracepointExt tracepoint = new TracepointExt();
        tracepoint.setIterations(iterations);
        tracepoint.setStarttime(startTime);
        tracepoint.setEndtime(endTime);
        tracepoint.setDeeptrace(isDeeptrace);
        activityConfiguration.setTracepoint(tracepoint);
        return tracepoint;
    }

    protected Extract setExtractToActivityConfig(ActivityConfiguration activityConfiguration, String name, ExtractRule... rules){
        Extract extract = new Extract();
        extract.setName(name);
        for(ExtractRule rule : rules){
            extract.getExtractRules().add(rule);
        }
        activityConfiguration.setExtract(extract);
        return extract;
    }

    protected void checkResultMessageForMissingsParameters(InstructionProcessor instructionProcessor, String... parameters)
            throws NjamsInstructionException {
        Instruction instruction = instructionBuilder.build();
        instructionProcessor.processInstruction(new NjamsInstructionWrapper(instruction).wrap());
        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        for(String parameter : parameters){
            assertTrue(response.getResultMessage().contains(parameter));
        }
        Map<String, String> setParamters = response.getParameters();
        assertTrue(setParamters.isEmpty());
    }

    protected com.im.njams.sdk.api.adapter.messageformat.command.entity.Instruction getWrappedInstruction(Instruction instruction)
            throws NjamsInstructionException {
        return new NjamsInstructionWrapper(instruction).wrap();
    }
}
