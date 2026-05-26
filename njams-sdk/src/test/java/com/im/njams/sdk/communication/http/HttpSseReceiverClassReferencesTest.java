package com.im.njams.sdk.communication.http;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;

import org.junit.Test;

/**
 * Regression test for SDK-429.
 *
 * The HTTP receiver must not reference any {@code javax.jms.*} class. Previously,
 * {@link HttpSseReceiver} accidentally imported and threw {@code javax.jms.IllegalStateException}
 * instead of {@link java.lang.IllegalStateException}, forcing every HTTP-only consumer
 * to ship the JMS API.
 *
 * The test inspects the compiled class file directly so it is independent of method-level
 * test setup; it catches any future reintroduction of a {@code javax.jms.*} reference anywhere
 * in {@link HttpSseReceiver}.
 */
public class HttpSseReceiverClassReferencesTest {

    @Test
    public void httpSseReceiverHasNoJavaxJmsReference() throws Exception {
        byte[] classBytes = readClassBytes(HttpSseReceiver.class);
        boolean containsJmsReference = containsAscii(classBytes, "javax/jms");
        assertFalse("HttpSseReceiver must not reference javax.jms.* classes (SDK-429)",
                containsJmsReference);
    }

    private static byte[] readClassBytes(Class<?> type) throws Exception {
        String resourceName = type.getSimpleName() + ".class";
        URL resource = type.getResource(resourceName);
        assertNotNull("Could not locate class file for " + type.getName(), resource);
        try (InputStream in = resource.openStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static boolean containsAscii(byte[] haystack, String needle) {
        byte[] needleBytes = needle.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= haystack.length - needleBytes.length; i++) {
            for (int j = 0; j < needleBytes.length; j++) {
                if (haystack[i + j] != needleBytes[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
