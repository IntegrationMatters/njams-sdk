/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import com.im.njams.sdk.njams.communication.receiver.NjamsReceiver;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.njams.metadata.NjamsMetadataFactory;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.util.ServiceConfigurationError;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class CommunicationFactoryTest {

    private final CommunicationServiceLoader<AbstractSender> SENDERS_NOT_NEEDED = null;
    private CommunicationFactory communicationFactory;
    private CommunicationServiceLoader<Receiver> receivers;

    @Before
    public void setUp() {
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
                                                            CommunicationServiceLoader<AbstractSender> senders) {

        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestReceiver.NAME);

        return new CommunicationFactory(settings, receivers, senders);
    }

    @Test
    public void returnsReceiver_evenIfServiceLoaderCantLoadThePreviousService() {
        firstReceiverIsFaulty_secondReceiverIsOk();

        final String NOT_NEEDED = null;
        NjamsMetadata metadata = NjamsMetadataFactory.createMetadataWith(new Path("CLIENT_PATH"), NOT_NEEDED, "SDK");
        NjamsReceiver njamsReceiver = new NjamsReceiver(null, null, null, null, null, null);
        Receiver receiver = communicationFactory.getReceiver(metadata, njamsReceiver);

        verify(receivers.iterator(), times(2)).next();
        assertNotNull(receiver);
    }

    private void firstReceiverIsFaulty_secondReceiverIsOk() {
        when(receivers.iterator().hasNext()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(receivers.iterator().next()).thenThrow(ServiceConfigurationError.class).thenReturn(new TestReceiver());
    }

}