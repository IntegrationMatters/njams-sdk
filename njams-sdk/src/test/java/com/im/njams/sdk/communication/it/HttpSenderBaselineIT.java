package com.im.njams.sdk.communication.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Properties;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.http.HttpSender;
import com.im.njams.sdk.settings.ClientSettings;

public class HttpSenderBaselineIT {

    @Rule
    public IngestHttpServer server = new IngestHttpServer();

    /** HttpSender caches its connection-test in a static field; reset it so each test starts clean. */
    @Before
    public void resetHttpConnectionTestCache() throws Exception {
        Field f = HttpSender.class.getDeclaredField("connectionTest");
        f.setAccessible(true);
        f.set(null, null);
    }

    ClientSettings settings(IngestHttpServer server) {
        Properties p = new Properties();
        p.put(NjamsSettings.PROPERTY_COMMUNICATION, HttpSender.NAME);
        p.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, server.baseUrl());
        p.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, server.dataproviderSuffix());
        // NONE = never discard; block and retry until sent (see the JMS settings() note). Pins guaranteed delivery
        // so the reconnect test (Task 9) is deterministic; product DEFAULT is DISCARD.
        p.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        p.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "1");
        p.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "1");
        return ClientSettings.from(p);
    }

    static LogMessage logMessage(String logId, String path) {
        LogMessage msg = new LogMessage();
        msg.setLogId(logId);
        msg.setPath(path);
        return msg;
    }

    @Test
    public void sendsLogMessageThatArrivesAtTheIngestEndpoint() {
        NjamsSender sender = new NjamsSender(settings(server));
        try {
            sender.send(logMessage("log-1", ">a>b>"), "session-1");
            assertTrue("a POST should reach the ingest endpoint",
                Await.until(() -> server.postCount() >= 1, 5000));
            assertEquals(1, server.postCount());
            assertTrue("body should carry the logId", server.receivedBodies().get(0).contains("log-1"));
        } finally {
            sender.close();
        }
    }
}
