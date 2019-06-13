package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication_rework.instruction.control.processor.AbstractInstructionProcessor;
import com.im.njams.sdk.configuration.entity.Configuration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;

import static org.mockito.Mockito.mock;

public class AbstractConfigurationProcessor extends AbstractInstructionProcessor {

    protected Njams njamsMock;

    protected static final Configuration READ_ONLY_CONFIGURATION = new Configuration();
    protected Configuration writeableConfiguration;

    @Before
    public void setNewNjamsMock(){
        njamsMock = mock(Njams.class);
        writeableConfiguration = new Configuration();
    }

    protected ProcessConfiguration addProcessConfig(String path) {
        ProcessConfiguration process = new ProcessConfiguration();
        writeableConfiguration.getProcesses().put(path, process);
        return process;
    }
}
