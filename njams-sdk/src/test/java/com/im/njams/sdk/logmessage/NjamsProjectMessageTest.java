package com.im.njams.sdk.logmessage;

import com.im.njams.sdk.client.NjamsMetadata;
import com.im.njams.sdk.client.NjamsMetadataFactory;
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
        final NjamsMetadata njamsMetadata = NjamsMetadataFactory.createMetadataFor(clientPath, null, null, null);
        njamsProjectMessage = new NjamsProjectMessage(njamsMetadata, null, null, null, null, null, null, settings);
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