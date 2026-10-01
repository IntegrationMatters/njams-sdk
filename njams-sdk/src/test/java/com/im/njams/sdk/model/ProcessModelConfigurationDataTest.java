package com.im.njams.sdk.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;

/**
 * Tests that the serializable form of a {@link ProcessModel} carries the process specific data from the
 * {@link Configuration}.
 */
public class ProcessModelConfigurationDataTest extends AbstractTest {

    @Test
    public void defaultsAreSerializedWithoutProcessSpecificConfiguration() {
        com.faizsiegeln.njams.messageformat.v4.projectmessage.ProcessModel serialized =
            process.getSerializableProcessModel();

        assertEquals(LogLevel.INFO, serialized.getLogLevel());
        assertFalse(serialized.isExclude());
        assertTrue(serialized.getRecording());
    }

    @Test
    public void processSpecificConfigurationIsSerialized() {
        Configuration configuration = njams.configuration().get();
        ProcessConfiguration processConfig = configuration.getProcess(process.getPath().toString());
        processConfig.setLogLevel(LogLevel.ERROR);
        processConfig.setRecording(false);
        configuration.setProcessExcluded(process.getPath(), true);

        com.faizsiegeln.njams.messageformat.v4.projectmessage.ProcessModel serialized =
            process.getSerializableProcessModel();

        assertEquals(LogLevel.ERROR, serialized.getLogLevel());
        assertTrue(serialized.isExclude());
        assertFalse(serialized.getRecording());
    }
}
