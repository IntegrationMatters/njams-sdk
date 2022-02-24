package com.im.njams.sdk.communication;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.util.ServiceConfigurationError;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommunicationFactoryTest {

    CommunicationFactory communicationFactory;
    Settings settings;
    Iterator iterator;
    CommunicationServiceLoader serviceLoader;

    @Before
    public void setUp() {
        iterator = mock(Iterator.class);
        serviceLoader = mock(CommunicationServiceLoader.class);
        when(serviceLoader.iterator()).thenReturn(iterator);

        settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestReceiver.NAME);
        communicationFactory = new CommunicationFactory(
            settings,
            serviceLoader,
            serviceLoader
        );
    }

    @Test
    public void returnsReceiverEvenIfServiceLoaderCantLoadThePreviousService() {
        firstReceiverIsFaultySecondReceiverIsOk();
        Njams NOT_NEEDED = null;

        Receiver receiver = communicationFactory.getReceiver(NOT_NEEDED);

        assertNotNull(receiver);
    }

    private void firstReceiverIsFaultySecondReceiverIsOk() {
        when(iterator.hasNext())
            .thenReturn(true)
            .thenReturn(true)
            .thenReturn(false);
        when(iterator.next())
            .thenThrow(ServiceConfigurationError.class)
            .thenReturn(new TestReceiver());
    }

}