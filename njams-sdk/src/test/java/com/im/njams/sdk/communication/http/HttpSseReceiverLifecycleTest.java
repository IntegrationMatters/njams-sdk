package com.im.njams.sdk.communication.http;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.settings.ClientSettings;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Okio;

/**
 * SDK-375: connection lifecycle of the HTTP SSE receiver, driven through a mocked HTTP client (no network).
 */
public class HttpSseReceiverLifecycleTest {

    private final List<PipedOutputStream> streams = new CopyOnWriteArrayList<>();
    private final AtomicBoolean failSubscribe = new AtomicBoolean();
    private Njams njams;
    private TestableReceiver receiver;

    @Before
    public void setUp() {
        streams.clear();
        failSubscribe.set(false);
        njams = new Njams(Path.of("SDK4", "SSE_LIFECYCLE"), "1.0", "test", TestSender.getSettings());
    }

    @After
    public void tearDown() throws IOException {
        if (receiver != null) {
            receiver.setShouldShutdown(true);
            receiver.cancelReconnect();
            receiver.stop();
            if (receiver.source != null) {
                receiver.source.close();
            }
        }
        for (PipedOutputStream stream : streams) {
            stream.close();
        }
    }

    @Test
    public void receiverWhoseInitialSubscriptionIsRejectedReconnectsOnceTheServerAccepts()
        throws InterruptedException {
        failSubscribe.set(true);
        receiver = newReceiver();

        receiver.beginConnect();

        assertTrue("receiver must reconnect after the server rejected its initial subscription",
            awaitCondition(receiver::isConnected, 20));
    }

    @Test
    public void shutdownRightAfterAStreamDropReleasesTheDroppedEventSource() throws Exception {
        receiver = newReceiver();
        receiver.connect();
        assertTrue(receiver.isConnected());
        // a shutdown that lands between the drop and the reconnect's next connect attempt
        receiver.shutdownBeforeReconnect = true;

        streams.get(0).close();

        assertTrue("the drop must be reported", awaitCondition(() -> receiver.dropped, 5));
        assertTrue("shutdown must release the dropped event source", awaitCondition(() -> receiver.source == null, 5));
        assertNull(receiver.client);
    }

    private TestableReceiver newReceiver() {
        Properties properties = new Properties();
        properties.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        properties.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, "http://localhost:1");
        TestableReceiver r = new TestableReceiver(mockClient());
        r.setNjams(njams);
        r.init(ClientSettings.from(properties));
        return r;
    }

    private OkHttpClient mockClient() {
        OkHttpClient client = mock(OkHttpClient.class);
        when(client.connectionPool()).thenReturn(new ConnectionPool());
        when(client.newCall(any(Request.class))).thenAnswer(inv -> {
            Request request = inv.getArgument(0);
            Call call = mock(Call.class);
            if (failSubscribe.get()) {
                when(call.execute()).thenReturn(response(request, 503, ResponseBody.create("", null)));
            } else {
                PipedOutputStream out = new PipedOutputStream();
                PipedInputStream in = new PipedInputStream(out, 65536);
                streams.add(out);
                ResponseBody body = ResponseBody.create(Okio.buffer(Okio.source(in)),
                    MediaType.get("text/event-stream"), -1);
                when(call.execute()).thenReturn(response(request, 200, body));
            }
            return call;
        });
        return client;
    }

    private static Response response(Request request, int code, ResponseBody body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body(body).build();
    }

    private static boolean awaitCondition(BooleanSupplier condition, int seconds) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }

    private class TestableReceiver extends HttpSseReceiver {
        private volatile boolean shutdownBeforeReconnect;
        private volatile boolean dropped;
        private final OkHttpClient mockClient;

        private TestableReceiver(OkHttpClient mockClient) {
            this.mockClient = mockClient;
        }

        @Override
        public void connect() {
            // a failed connect releases the client; keep every attempt on the mocked one
            client = mockClient;
            try {
                super.connect();
            } catch (RuntimeException e) {
                // the server accepts again once the receiver's first connect attempt has failed
                failSubscribe.set(false);
                throw e;
            }
        }

        @Override
        public void onException(Exception exception) {
            if (shutdownBeforeReconnect) {
                // mirrors the receiver part of Njams.stop()
                setShouldShutdown(true);
                stop();
                cancelReconnect();
            }
            super.onException(exception);
            dropped = true;
        }
    }
}
