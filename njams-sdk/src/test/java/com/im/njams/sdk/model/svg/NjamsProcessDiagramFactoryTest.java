package com.im.njams.sdk.model.svg;

import static org.mockito.Mockito.when;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.Settings;

public class NjamsProcessDiagramFactoryTest {

    @Test
    public void testSecureProcessingEnabled() {
        Settings config = new Settings();
        config.put(NjamsSettings.PROPERTY_DISABLE_SECURE_PROCESSING, "false");

        Njams njams = Mockito.mock(Njams.class);
        when(njams.getSettings()).thenReturn(config);
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(njams);

        Assert.assertFalse(factory.disableSecureProcessing);
    }

    @Test
    public void testSecureProcessingDisabled() {
        Settings config = new Settings();
        config.put(NjamsSettings.PROPERTY_DISABLE_SECURE_PROCESSING, "true");

        Njams njams = Mockito.mock(Njams.class);
        when(njams.getSettings()).thenReturn(config);
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(njams);

        Assert.assertTrue(factory.disableSecureProcessing);
    }
}
