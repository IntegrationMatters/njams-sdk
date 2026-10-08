package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsMetadata;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.CapturingLogAppender;

/**
 * Verifies what is reported when the configured sender or receiver implementation cannot be found.
 */
public class CommunicationFactoryNotFoundTest {

    private Njams njams;

    @Before
    public void setUp() {
        njams = mock(Njams.class);
        NjamsMetadata metadata = mock(NjamsMetadata.class);
        when(njams.metadata()).thenReturn(metadata);
        when(metadata.getClientPath()).thenReturn(Path.of("test"));
    }

    private ClientSettings settings(String communicationType) {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, communicationType);
        return settings;
    }

    private void assertReported(CapturingLogAppender appender, String kind, String exceptionMessage,
        String unavailableCause) {
        assertTrue(exceptionMessage, exceptionMessage.contains("'doesNotExist'"));
        assertTrue(exceptionMessage, exceptionMessage.contains("See the log"));
        assertFalse("the exception must not carry the lists: " + exceptionMessage,
            exceptionMessage.contains("Available"));

        List<LoggingEvent> errors = appender.events(Level.ERROR);
        assertEquals(1, errors.size());
        String message = errors.get(0).getRenderedMessage();
        assertTrue(message, message.contains("Unable to find " + kind + " implementation"));
        assertTrue(message, message.contains("'doesNotExist'"));
        assertTrue(message, message.contains(NjamsSettings.PROPERTY_COMMUNICATION));
        assertTrue(message, message.contains("Available: ["));
        assertTrue(message, message.contains(TestSender.NAME));
        assertTrue(message, message.contains(unavailableCause));
    }

    @Test
    public void senderNotFoundIsLoggedAsErrorWithAvailableAndUnavailableImplementations() {
        CapturingLogAppender appender = CapturingLogAppender.attach(CommunicationFactory.class);
        try {
            try {
                new CommunicationFactory(settings("doesNotExist")).getSender();
                fail("IllegalStateException expected");
            } catch (IllegalStateException e) {
                assertReported(appender, "sender", e.getMessage(),
                    "IllegalStateException: sender constructor failed");
            }
        } finally {
            appender.detach();
        }
    }

    @Test
    public void receiverNotFoundIsLoggedAsErrorWithAvailableAndUnavailableImplementations() {
        CapturingLogAppender appender = CapturingLogAppender.attach(CommunicationFactory.class);
        try {
            try {
                new CommunicationFactory(settings("doesNotExist")).getReceiver(njams);
                fail("IllegalStateException expected");
            } catch (IllegalStateException e) {
                assertReported(appender, "receiver", e.getMessage(),
                    "IllegalStateException: receiver constructor failed");
            }
        } finally {
            appender.detach();
        }
    }

    @Test
    public void nothingIsLoggedAtErrorWhenTheSenderIsFound() {
        CapturingLogAppender appender = CapturingLogAppender.attach(CommunicationFactory.class);
        try {
            TestSender.setSenderMock(mock(AbstractSender.class));
            new CommunicationFactory(settings(TestSender.NAME)).getSender();
            assertTrue(appender.events(Level.ERROR).isEmpty());
        } finally {
            appender.detach();
        }
    }
}
