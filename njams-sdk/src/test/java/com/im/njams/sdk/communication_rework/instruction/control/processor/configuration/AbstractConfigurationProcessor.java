package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ExtractRule;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication_rework.instruction.control.processor.AbstractInstructionProcessor;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.Configuration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class AbstractConfigurationProcessor extends AbstractInstructionProcessor {

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

    protected Extract setExtractToActivityConfig(ActivityConfiguration activityConfiguration, String name, ExtractRule... rules){
        Extract extract = new Extract();
        extract.setName(name);
        for(ExtractRule rule : rules){
            extract.getExtractRules().add(rule);
        }
        activityConfiguration.setExtract(extract);
        return extract;
    }

    protected void checkResultMessageForMissingsParameters(ConfigurationProcessor configurationProcessor, String... parameters) {
        Instruction instruction = instructionBuilder.build();
        configurationProcessor.processInstruction(instruction);
        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        for(String parameter : parameters){
            assertTrue(response.getResultMessage().contains(parameter));
        }
        Map<String, String> setParamters = response.getParameters();
        assertTrue(setParamters.isEmpty());
    }
}
