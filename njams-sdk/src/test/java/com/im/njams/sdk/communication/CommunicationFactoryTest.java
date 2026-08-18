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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.http.HttpSseReceiver;
import com.im.njams.sdk.communication.jms.FailingJmsFactory;
import com.im.njams.sdk.settings.Settings;

public class CommunicationFactoryTest {

    private Njams njams = null;

    @Before
    public void setUp() {
        njams = mock(Njams.class);
        when(njams.getClientPath()).thenReturn(Path.of("test"));
    }

    private Settings createSettings(String communicationType) {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, communicationType);
        return settings;
    }

    @Test
    public void testCreateAndInit() {
        AbstractSender sender = mock(AbstractSender.class);
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
    public void httpReceiverWithoutSharingIsAnHttpSseReceiver() {
        Settings settings = createSettings("HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, "http://localhost:8080/njams/");
        CommunicationFactory factory = new CommunicationFactory(settings);
        assertTrue(factory.getReceiver(njams) instanceof HttpSseReceiver);
    }

    @Test
    public void httpReceiverWithSharingRequestedFallsBackToHttpSseReceiver() {
        Settings settings = createSettings("HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, "http://localhost:8080/njams/");
        settings.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
        CommunicationFactory factory = new CommunicationFactory(settings);
        assertTrue(factory.getReceiver(njams) instanceof HttpSseReceiver);
    }

    @Test
    public void theDedicatedReceiverFallbackIsLoggedAtInfoNotWarn() {
        Logger factoryLogger = Logger.getLogger(CommunicationFactory.class);
        CapturingAppender appender = new CapturingAppender();
        Level originalLevel = factoryLogger.getLevel();
        factoryLogger.addAppender(appender);
        factoryLogger.setLevel(Level.DEBUG);
        try {
            Settings settings = createSettings("HTTP");
            settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, "http://localhost:8080/njams/");
            settings.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
            new CommunicationFactory(settings).getReceiver(njams);

            List<LoggingEvent> aboutSharing = appender.events().stream()
                .filter(e -> String.valueOf(e.getRenderedMessage()).contains("dedicated receiver instance"))
                .collect(Collectors.toList());
            assertEquals("the fallback must be reported exactly once", 1, aboutSharing.size());
            assertEquals("a deliberate design decision must not be logged as a warning",
                Level.INFO, aboutSharing.get(0).getLevel());
        } finally {
            factoryLogger.removeAppender(appender);
            factoryLogger.setLevel(originalLevel);
        }
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

    @Test
    public void communicationTypeAlternativeKeyIsAccepted() {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_TYPE, TestSender.NAME);

        AbstractSender sender = mock(AbstractSender.class);
        TestSender.setSenderMock(sender);
        CommunicationFactory factory = new CommunicationFactory(settings);
        assertTrue(factory.getSender() instanceof TestSender);

        Receiver receiver = mock(Receiver.class);
        TestReceiver.setReceiverMock(receiver);
        assertTrue(factory.getReceiver(njams) instanceof TestReceiver);
    }

    @Test
    public void primaryCommunicationKeyTakesPrecedenceOverAlternative() {
        Settings settings = createSettings(TestSender.NAME);
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_TYPE, FailingJmsFactory.NAME);

        AbstractSender sender = mock(AbstractSender.class);
        TestSender.setSenderMock(sender);
        CommunicationFactory factory = new CommunicationFactory(settings);
        assertTrue(factory.getSender() instanceof TestSender);
    }

    @Test
    public void missingBothCommunicationKeysThrows() {
        CommunicationFactory factory = new CommunicationFactory(new Settings());
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

    /** Captures log events so a test can assert on their level. Mirrors {@code ReceiverLoggingSpecTest}'s. */
    private static final class CapturingAppender extends AppenderSkeleton {
        private final List<LoggingEvent> events = new CopyOnWriteArrayList<>();

        List<LoggingEvent> events() {
            return events;
        }

        @Override
        protected void append(LoggingEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
            // nothing to release
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }

}