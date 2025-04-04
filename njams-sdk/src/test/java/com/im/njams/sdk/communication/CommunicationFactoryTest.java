package com.im.njams.sdk.communication;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.jms.FailingJmsFactory;
import com.im.njams.sdk.settings.Settings;

public class CommunicationFactoryTest {

    private Njams njams = null;

    @Before
    public void setUp() {
        njams = mock(Njams.class);
        when(njams.getClientPath()).thenReturn(new Path("test"));
    }

    private Settings createSettings(String communicationType) {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, communicationType);
        return settings;
    }

    @Test
    public void testCreateAndInit() {
        Sender sender = mock(Sender.class);
        TestSender.setSenderMock(sender);
        CommunicationFactory factory = new CommunicationFactory(createSettings(TestSender.NAME));
        assertTrue(factory.getSender() instanceof TestSender);
        verify(sender).init(any());

        Receiver receiver = mock(Receiver.class);
        TestReceiver.setReceiverMock(receiver);
        assertTrue(factory.getReceiver(njams) instanceof TestReceiver);
        verify(receiver).init(any());
    }

    @Test
    public void testCreateFail() {
        CommunicationFactory factory = new CommunicationFactory(createSettings(FailingJmsFactory.NAME));
        try {
            factory.getSender();
            fail("IllegalStateException expected");
        } catch (IllegalStateException e) {
        }
        try {
            factory.getReceiver(njams);
            fail("IllegalStateException expected");
        } catch (IllegalStateException e) {
        }
    }

}