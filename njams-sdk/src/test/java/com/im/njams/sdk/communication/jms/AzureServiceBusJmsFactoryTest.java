package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.jms.JMSException;

import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Unit tests for {@link AzureServiceBusJmsFactory}: identity, not-initialized guard and the
 * exception handling for an invalid provider URL.
 */
public class AzureServiceBusJmsFactoryTest {

    @Test
    public void nameAndToString() {
        AzureServiceBusJmsFactory factory = new AzureServiceBusJmsFactory();
        assertEquals(AzureServiceBusJmsFactory.NAME, factory.getName());
        assertTrue(factory.toString().contains(AzureServiceBusJmsFactory.NAME));
    }

    @Test
    public void createConnectionFactoryBeforeInitThrows() {
        try {
            new AzureServiceBusJmsFactory().createConnectionFactory();
            fail("expected IllegalStateException when not initialized");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void initWithInvalidProviderUrlThrowsJmsException() {
        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, ":::");

        try {
            new AzureServiceBusJmsFactory().init(ClientSettings.from(props));
            fail("expected JMSException for an invalid provider URL");
        } catch (JMSException expected) {
            // expected: the malformed URI is wrapped into a JMSException
        }
    }

    /**
     * Pins the provider class names this factory reflects on. They are plain strings, so nothing but this
     * test notices when they drift to the other Azure package: the jakarta client lives in
     * {@code com.azure.servicebus.jms}, the javax one in {@code com.microsoft.azure.servicebus.jms}.
     */
    @Test
    public void reflectsOnExactlyTheExpectedAzureClassNames() throws Exception {
        Set<String> expected = new TreeSet<>(Arrays.asList(
            "com.azure.servicebus.jms.ConnectionStringBuilder",
            "com.azure.servicebus.jms.ServiceBusJmsConnectionFactory",
            "com.azure.servicebus.jms.ServiceBusJmsConnectionFactorySettings"));

        assertEquals(expected, serviceBusClassNamesReferencedBy(AzureServiceBusJmsFactory.class));
    }

    /**
     * Collects every {@code com.*.servicebus.jms.*} string constant from the given class's class file.
     * The constant pool stores no terminator, so the match is anchored on {@code com} to keep a
     * preceding length byte that happens to be printable out of the result.
     */
    private static Set<String> serviceBusClassNamesReferencedBy(Class<?> type) throws IOException {
        byte[] classFile;
        try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            assertNotNull("class file of " + type.getName(), in);
            classFile = in.readAllBytes();
        }
        Matcher matcher = Pattern.compile("com[A-Za-z0-9_.$]*servicebus\\.jms\\.[A-Za-z0-9_$]+")
            .matcher(new String(classFile, StandardCharsets.ISO_8859_1));
        Set<String> found = new TreeSet<>();
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
