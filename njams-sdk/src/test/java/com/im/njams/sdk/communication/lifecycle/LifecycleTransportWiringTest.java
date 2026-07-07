package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.settings.ClientSettings;

public class LifecycleTransportWiringTest extends AbstractLifecycleSpecTest {

    @Test
    public void factoryResolvesTheControllableSender() {
        ClientSettings cs = ClientSettings.from(LifecycleTestTransport.settings().getAllProperties());
        // getSender() must return our controllable sender, resolved by transport name
        AbstractSender sender = new CommunicationFactory(cs).getSender();
        assertNotNull(sender);
        assertEquals(LifecycleTestTransport.NAME, sender.getName());
    }
}
