package com.im.njams.sdk.model.svg;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NjamsProcessDiagramFactoryTest {

    private Settings config;

    @Before
    public void setUp() {
        config = new Settings();
    }

    @Test
    public void secureProcessingIsEnabledPerDefault(){
        Njams njams = new Njams(new Path(), null, null, config);
        NjamsProcessDiagramFactory factory = (NjamsProcessDiagramFactory) njams.getProcessDiagramFactory();

        isProcessingWith(factory).secure();
    }

    @Test
    public void testSecureProcessingEnabled() {
        config.put(Settings.PROPERTY_DISABLE_SECURE_PROCESSING, "false");

        Njams njams = new Njams(new Path(), null, null, config);
        NjamsProcessDiagramFactory factory = (NjamsProcessDiagramFactory) njams.getProcessDiagramFactory();

        isProcessingWith(factory).secure();
    }

    @Test
    public void testSecureProcessingDisabled() {
        config.put(Settings.PROPERTY_DISABLE_SECURE_PROCESSING, "true");

        Njams njams = new Njams(new Path(), null, null, config);
        NjamsProcessDiagramFactory factory = (NjamsProcessDiagramFactory) njams.getProcessDiagramFactory();

        isProcessingWith(factory).notSecure();
    }

    private AssertionHelper isProcessingWith(NjamsProcessDiagramFactory factory) {
        return new AssertionHelper(factory);
    }

    private static class AssertionHelper {
        private final NjamsProcessDiagramFactory factory;

        public AssertionHelper(NjamsProcessDiagramFactory factory) {
            this.factory = factory;
        }

        public void secure() {
            Assert.assertFalse(factory.disableSecureProcessing);
        }

        public void notSecure(){
            Assert.assertTrue(factory.disableSecureProcessing);
        }
    }
}


