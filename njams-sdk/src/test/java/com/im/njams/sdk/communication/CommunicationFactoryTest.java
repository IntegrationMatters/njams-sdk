/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
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
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
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