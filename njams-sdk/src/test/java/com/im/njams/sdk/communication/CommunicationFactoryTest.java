package com.im.njams.sdk.communication;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.util.ServiceConfigurationError;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class CommunicationFactoryTest {

    private final CommunicationServiceLoader<Sender> SENDERS_NOT_NEEDED = null;
    private CommunicationFactory communicationFactory;
    private CommunicationServiceLoader<Receiver> receivers;

    @Before public void setUp() {
        receivers = createServiceLoaderMock();
        communicationFactory = createCommunicationFactory(receivers, SENDERS_NOT_NEEDED);
    }

    private CommunicationServiceLoader<Receiver> createServiceLoaderMock() {
        Iterator<Receiver> iterator = mock(Iterator.class);
        CommunicationServiceLoader<Receiver> receivers = mock(CommunicationServiceLoader.class);
        when(receivers.iterator()).thenReturn(iterator);

        return receivers;
    }

    private CommunicationFactory createCommunicationFactory(CommunicationServiceLoader<Receiver> receivers,
        CommunicationServiceLoader<Sender> senders) {

        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestReceiver.NAME);

        return new CommunicationFactory(settings, receivers, senders);
    }

    @Test public void returnsReceiver_evenIfServiceLoaderCantLoadThePreviousService() {
        firstReceiverIsFaulty_secondReceiverIsOk();
        Njams njams = mock(Njams.class);
        when(njams.getClientPath()).thenReturn(new Path("CLIENT_PATH"));

        Receiver receiver = communicationFactory.getReceiver(njams);

        verify(receivers.iterator(), times(2)).next();
        assertNotNull(receiver);
    }

    private void firstReceiverIsFaulty_secondReceiverIsOk() {
        when(receivers.iterator().hasNext()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(receivers.iterator().next()).thenThrow(ServiceConfigurationError.class).thenReturn(new TestReceiver());
    }

}