package com.im.njams.sdk.communication.http;

import static com.im.njams.sdk.communication.MessageHeaders.COMMAND_TYPE_REPLY;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CHUNK_MESSAGE_KEY_HTTP_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CHUNK_NO_HTTP_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CLIENTID_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CLIENTID_HTTP_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_MESSAGETYPE_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_MESSAGETYPE_HTTP_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_RECEIVER_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_RECEIVER_HTTP_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_REPLY_FOR_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_REPLY_FOR_HTTP_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.RECEIVER_SERVER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.communication.fragments.SplitSupport;
import com.im.njams.sdk.settings.ClientSettings;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.StreamIOException;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * Event handling, replies, error handling, init and stop of {@link HttpSseReceiver}, without any network: the
 * receiver never connects and replies go to a mocked {@link OkHttpClient} (SDK-499). Connect, drop and reconnect
 * are covered by {@link HttpSseReceiverLifecycleTest}.
 */
public class HttpSseReceiverTest {

    private static final String BASE_URL = "http://localhost:1";
    private static final String PING = "{\"request\":{\"command\":\"ping\"}}";

    private final List<Request> sent = new CopyOnWriteArrayList<>();
    private final List<Instruction> handled = new CopyOnWriteArrayList<>();
    private Njams njams;
    private String clientPath;
    private String sessionId;
    private TestableReceiver receiver;

    @Before
    public void setUp() throws IOException {
        njams = new Njams(Path.of("SDK4", "SSE"), "1.0", "test", TestSender.getSettings());
        njams.commands().add(instruction -> {
            handled.add(instruction);
            instruction.setResponseResultCode(0);
            instruction.setResponseResultMessage("pong");
        });
        clientPath = njams.metadata().getClientPath().toString();
        sessionId = njams.metadata().getClientSessionId();
        receiver = newReceiver(BASE_URL, null);
        receiver.client = replyClient(false);
    }

    @Test
    public void jsonHeaderEventIsAnswered() throws Exception {
        receiver.onMessage("message", event(jsonName(clientPath, "req-1", "json", sessionId), PING, "req-1"));

        assertEquals(1, handled.size());
        assertEquals(1, sent.size());
        Request reply = sent.get(0);
        assertEquals(BASE_URL + "/api/httpcommunication/reply", reply.url().toString());
        assertEquals("POST", reply.method());
        assertEquals("req-1", reply.header(NJAMS_REPLY_FOR_HTTP_HEADER));
        assertEquals(RECEIVER_SERVER, reply.header(NJAMS_RECEIVER_HTTP_HEADER));
        assertEquals(COMMAND_TYPE_REPLY, reply.header(NJAMS_MESSAGETYPE_HTTP_HEADER));
        assertEquals(sessionId, reply.header(NJAMS_CLIENTID_HTTP_HEADER));
        assertEquals("req-1", reply.header(NJAMS_REPLY_FOR_HEADER));
        assertEquals(RECEIVER_SERVER, reply.header(NJAMS_RECEIVER_HEADER));
        assertEquals(COMMAND_TYPE_REPLY, reply.header(NJAMS_MESSAGETYPE_HEADER));
        assertEquals(sessionId, reply.header(NJAMS_CLIENTID_HEADER));
        assertTrue(body(reply), body(reply).contains("pong"));
    }

    @Test
    public void legacyEventIsAnswered() throws Exception {
        receiver.onMessage("message", event(clientPath, PING, "req-2"));

        assertEquals(1, sent.size());
        assertEquals("req-2", sent.get(0).header(NJAMS_REPLY_FOR_HTTP_HEADER));
    }

    @Test
    public void rejectedEventsAreNotAnswered() throws Exception {
        receiver.onMessage("message", event(" ", PING, "a"));
        receiver.onMessage("message", event(jsonName(">other>", "b", "json", null), PING, "b"));
        receiver.onMessage("message", event(jsonName(clientPath, "c", "json", "other-client"), PING, "c"));
        receiver.onMessage("message", event(clientPath, PING, null));
        receiver.onMessage("message", event(jsonName(clientPath, "d", "xml", null), PING, "d"));
        receiver.onMessage("message", event(clientPath, "not json", "e"));
        njams.features().setContainerMode(false);
        receiver.onMessage("message", event(clientPath, "{\"request\":{\"command\":\"GetRequestHandler\"}}", "f"));

        assertTrue(handled.isEmpty());
        assertTrue(sent.isEmpty());
    }

    @Test
    public void chunkedEventIsAnsweredOnceComplete() throws Exception {
        String first = chunkName(1);
        String second = chunkName(2);

        receiver.onMessage("message", event(first, PING.substring(0, 10), "g"));
        assertTrue(sent.isEmpty());

        receiver.onMessage("message", event(second, PING.substring(10), "g"));
        assertEquals(1, sent.size());
        assertEquals("g", sent.get(0).header(NJAMS_REPLY_FOR_HTTP_HEADER));
    }

    @Test
    public void largeReplyIsSentInChunks() throws Exception {
        TestableReceiver chunking = newReceiver(BASE_URL + "/", String.valueOf(SplitSupport.MIN_SIZE_LIMIT));
        chunking.client = replyClient(false);
        Instruction big = new Instruction();
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 24 * 1024) {
            sb.append("abcdefgh");
        }
        big.setResponseResultMessage(sb.toString());

        chunking.sendReply("req-big", big, null);

        assertTrue(sent.size() > 1);
        String key = sent.get(0).header(NJAMS_CHUNK_MESSAGE_KEY_HTTP_HEADER);
        for (int i = 0; i < sent.size(); i++) {
            Request chunk = sent.get(i);
            assertEquals(BASE_URL + "/api/httpcommunication/reply", chunk.url().toString());
            assertEquals(String.valueOf(i + 1), chunk.header(NJAMS_CHUNK_NO_HTTP_HEADER));
            assertEquals(key, chunk.header(NJAMS_CHUNK_MESSAGE_KEY_HTTP_HEADER));
            assertNull(chunk.header(NJAMS_CLIENTID_HTTP_HEADER));
        }
    }

    @Test
    public void failedReplyIsSwallowed() throws Exception {
        receiver.client = replyClient(true);

        receiver.sendReply("req-io", new Instruction(), "cid");

        assertEquals(1, sent.size());
    }

    @Test
    public void onErrorHandling() {
        receiver.status(ConnectionStatus.CONNECTED);
        receiver.onError(new StreamIOException(new SocketTimeoutException()));
        assertTrue(receiver.isConnected());
        assertTrue(receiver.exceptions.isEmpty());

        IOException broken = new IOException("x");
        receiver.onError(broken);
        assertTrue(receiver.isDisconnected());
        assertEquals(1, receiver.exceptions.size());
        assertEquals(broken, receiver.exceptions.get(0));

        receiver.onError(new IOException("y"));
        assertEquals(1, receiver.exceptions.size());

        receiver.status(ConnectionStatus.CONNECTED);
        Error error = new Error("err");
        receiver.onError(error);
        assertEquals(2, receiver.exceptions.size());
        Exception wrapped = receiver.exceptions.get(1);
        assertTrue(wrapped instanceof NjamsSdkRuntimeException);
        assertEquals("Connection failed.", wrapped.getMessage());
        assertEquals(error, wrapped.getCause());
    }

    @Test
    public void initValidatesBaseUrl() {
        expectInitFailure(null);
        expectInitFailure("http://bad host:1");

        assertEquals(newReceiver(BASE_URL, null).replyUrl, newReceiver(BASE_URL + "/", null).replyUrl);
        assertEquals(BASE_URL + "/api/httpcommunication/subscribe",
            newReceiver(BASE_URL, null).subscribeUri.toString());
    }

    @Test
    public void stopReleasesClient() throws IOException {
        TestableReceiver idle = newReceiver(BASE_URL, null);
        idle.stop();
        assertTrue(idle.isDisconnected());

        receiver.status(ConnectionStatus.CONNECTED);
        receiver.stop();

        assertTrue(receiver.isDisconnected());
        assertNull(receiver.client);
    }

    @Test
    public void lifecycleCallbacksAreNoOps() throws Exception {
        receiver.onOpen();
        receiver.onComment("comment");
        receiver.onClosed();

        assertTrue(sent.isEmpty());
        assertTrue(receiver.exceptions.isEmpty());
    }

    private void expectInitFailure(String baseUrl) {
        try {
            newReceiver(baseUrl, null);
            fail("init must fail for base URL " + baseUrl);
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    private String chunkName(int chunkNo) {
        return "{\"njams-receiver\":\"" + clientPath + "\",\"njams-message-id\":\"g\",\"njams-content\":\"json\","
            + "\"njams-chunks\":\"2\",\"njams-chunk-no\":\"" + chunkNo + "\",\"njams-chunk-message-key\":\"k\"}";
    }

    private static String jsonName(String receiverPath, String messageId, String content, String clientId) {
        return "{\"njams-receiver\":\"" + receiverPath + "\",\"njams-message-id\":\"" + messageId
            + "\",\"njams-content\":\"" + content + "\""
            + (clientId == null ? "" : ",\"njams-clientid\":\"" + clientId + "\"") + "}";
    }

    private static MessageEvent event(String name, String data, String id) {
        return new MessageEvent(name, data, id, null);
    }

    private static String body(Request request) throws IOException {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }

    private OkHttpClient replyClient(boolean failing) throws IOException {
        OkHttpClient client = mock(OkHttpClient.class);
        when(client.newCall(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            sent.add(request);
            Call call = mock(Call.class);
            if (failing) {
                when(call.execute()).thenThrow(new IOException("boom"));
            } else {
                when(call.execute()).thenReturn(new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create("", null)).build());
            }
            return call;
        });
        when(client.connectionPool()).thenReturn(new ConnectionPool());
        return client;
    }

    private TestableReceiver newReceiver(String baseUrl, String maxMessageSize) {
        Properties properties = new Properties();
        properties.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        if (baseUrl != null) {
            properties.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, baseUrl);
        }
        if (maxMessageSize != null) {
            properties.put(NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, maxMessageSize);
        }
        TestableReceiver testable = new TestableReceiver();
        testable.setNjams(njams);
        testable.init(ClientSettings.from(properties));
        return testable;
    }

    /** Exposes the connection status and records reconnect triggers instead of reconnecting. */
    private static final class TestableReceiver extends HttpSseReceiver {
        private final List<Exception> exceptions = new CopyOnWriteArrayList<>();

        private void status(ConnectionStatus status) {
            connectionStatus = status;
        }

        @Override
        public void onException(Exception exception) {
            exceptions.add(exception);
        }
    }
}
