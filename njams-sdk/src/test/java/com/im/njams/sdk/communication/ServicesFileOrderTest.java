package com.im.njams.sdk.communication;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.junit.Test;

/**
 * Pins the order of the shipped services files. {@link CommunicationFactory} tries candidates in services-file
 * order, and both JMS namespace trees answer <code>getName()</code> with "JMS", so listing the jakarta entries
 * first is what makes jakarta the one chosen when both would work. Reordering the files silently changes the
 * selection, which no behavior test can detect: see <code>.claude/rules/communication-layer.md</code>.
 */
public class ServicesFileOrderTest {

    @Test
    public void senderServicesFileListsJakartaBeforeJavax() throws Exception {
        assertJakartaFirst("com.im.njams.sdk.communication.AbstractSender",
            "com.im.njams.sdk.communication.jms.JmsSender",
            "com.im.njams.sdk.communication.jms.javax.JmsSender");
    }

    @Test
    public void receiverServicesFileListsJakartaBeforeJavax() throws Exception {
        assertJakartaFirst("com.im.njams.sdk.communication.AbstractReceiver",
            "com.im.njams.sdk.communication.jms.JmsReceiver",
            "com.im.njams.sdk.communication.jms.javax.JmsReceiver");
    }

    @Test
    public void receiverServicesFileListsTheJakartaSharedReceiverBeforeTheJavaxOne() throws Exception {
        assertJakartaFirst("com.im.njams.sdk.communication.AbstractReceiver",
            "com.im.njams.sdk.communication.jms.SharedJmsReceiver",
            "com.im.njams.sdk.communication.jms.javax.SharedJmsReceiver");
    }

    private void assertJakartaFirst(String serviceType, String jakartaEntry, String javaxEntry) throws Exception {
        final List<String> entries = shippedEntries(serviceType, jakartaEntry);
        final int jakartaAt = entries.indexOf(jakartaEntry);
        final int javaxAt = entries.indexOf(javaxEntry);
        assertTrue(javaxEntry + " is not registered in " + serviceType + "; found " + entries, javaxAt >= 0);
        assertTrue(jakartaEntry + " must be listed before " + javaxEntry + ", but the order is " + entries,
            jakartaAt < javaxAt);
    }

    /** The entries of the services file on the classpath that registers the given implementation. */
    private List<String> shippedEntries(String serviceType, String mustContain) throws Exception {
        final Enumeration<URL> urls = getClass().getClassLoader().getResources("META-INF/services/" + serviceType);
        while (urls.hasMoreElements()) {
            final List<String> entries = read(urls.nextElement());
            if (entries.contains(mustContain)) {
                return entries;
            }
        }
        fail("No services file for " + serviceType + " registers " + mustContain);
        return Collections.emptyList();
    }

    private List<String> read(URL url) throws Exception {
        final List<String> entries = new ArrayList<>();
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final int comment = line.indexOf('#');
                final String entry = (comment < 0 ? line : line.substring(0, comment)).trim();
                if (!entry.isEmpty()) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }
}
