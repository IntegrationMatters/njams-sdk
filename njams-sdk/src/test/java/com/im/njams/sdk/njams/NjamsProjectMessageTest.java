package com.im.njams.sdk.njams;

import com.im.njams.sdk.njams.NjamsProjectMessage;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.njams.metadata.NjamsMetadataFactory;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class NjamsProjectMessageTest {

    private NjamsProjectMessage njamsProjectMessage;

    @Before
    public void setUp() {
        final Settings settings = new Settings();
        final Path clientPath = new Path();
        final NjamsMetadata njamsMetadata = NjamsMetadataFactory.createMetadataWith(clientPath, null, "SDK");
        njamsProjectMessage = new NjamsProjectMessage(njamsMetadata, null, null, null, null, null, settings);
    }

    @Test
    public void testHasNoProcessModel() {
        assertFalse(njamsProjectMessage.hasProcessModel(new Path("PROCESSES")));
    }

    @Test
    public void testNoProcessModelForNullPath() {
        assertFalse(njamsProjectMessage.hasProcessModel(null));
    }

    @Test
    public void testHasProcessModel() {
        njamsProjectMessage.createProcess(new Path("PROCESSES"));
        assertTrue(njamsProjectMessage.hasProcessModel(new Path("PROCESSES")));
    }
}