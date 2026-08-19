package com.im.njams.sdk.communication.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.ClientSettings;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Unit tests for {@link HttpSender}: configuration validation in {@link HttpSender#init} and the
 * send / retry / discard behaviour driven through a mocked {@link OkHttpClient}.
 */
public class HttpSenderTest {

    private static ClientSettings settings(Map<String, String> props) {
        return ClientSettings.from(new HashMap<>(props));
    }

    private static Map<String, String> validProps() {
        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, "http://localhost:8080/");
        props.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "myDp");
        return props;
    }

    private static HttpSender initializedSender() {
        HttpSender sender = new HttpSender();
        sender.init(settings(validProps()));
        return sender;
    }

    private static LogMessage logMessage() {
        LogMessage msg = new LogMessage();
        msg.setLogId("log-1");
        msg.setPath(">a>b>");
        return msg;
    }

    private static Response response(HttpSender sender, int code) {
        Request dummy = new Request.Builder().url("http://localhost").build();
        return new Response.Builder().request(dummy).protocol(Protocol.HTTP_1_1).code(code).message("x").build();
    }

    private static OkHttpClient mockClientReturning(Response response) throws IOException {
        OkHttpClient client = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(client.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        return client;
    }

    private static OkHttpClient mockClientThrowing() throws IOException {
        OkHttpClient client = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(client.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("connection refused"));
        return client;
    }

    @Test
    public void getNameIsHttp() {
        assertEquals(HttpSender.NAME, new HttpSender().getName());
    }

    @Test
    public void initFailsWhenSuffixMissing() {
        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, "http://localhost:8080/");
        try {
            new HttpSender().init(settings(props));
            fail("expected IllegalStateException for missing dataprovider suffix");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void initFailsWhenBaseUrlMissing() {
        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "myDp");
        try {
            new HttpSender().init(settings(props));
            fail("expected IllegalStateException for missing base url");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void initBuildsIngestUrl() {
        HttpSender sender = initializedSender();
        assertNotNull(sender.url);
        assertTrue(sender.url.toString().contains("api/processing/ingest/myDp"));
    }

    @Test
    public void sendSucceedsOnStatus200() throws IOException {
        HttpSender sender = initializedSender();
        sender.client = mockClientReturning(response(sender, 200));
        sender.send(logMessage(), "session-1");
        verify(sender.client).newCall(any(Request.class));
    }

    @Test
    public void sendSucceedsOnStatus204() throws IOException {
        HttpSender sender = initializedSender();
        sender.client = mockClientReturning(response(sender, 204));
        sender.send(logMessage(), "session-1");
        verify(sender.client).newCall(any(Request.class));
    }

    @Test
    public void sendDiscardsOnConnectionLossPolicy() throws IOException {
        Map<String, String> props = validProps();
        props.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "onconnectionloss");
        HttpSender sender = new HttpSender();
        sender.init(settings(props));
        sender.client = mockClientThrowing();
        // discard policy makes the send give up immediately without throwing
        sender.send(logMessage(), "session-1");
        verify(sender.client, atLeastOnce()).newCall(any(Request.class));
    }

    @Test
    public void sendThrowsHttpSendExceptionAfterRetriesOnIoError() throws IOException {
        HttpSender sender = initializedSender();
        sender.client = mockClientThrowing();
        try {
            sender.send(logMessage(), "session-1");
            fail("expected HttpSendException after exhausting retries");
        } catch (HttpSendException expected) {
            // expected: communication failure triggers reconnect
        }
    }

    @Test
    public void sendThrowsRuntimeExceptionAfterRetriesOnErrorStatus() throws IOException {
        HttpSender sender = initializedSender();
        sender.client = mockClientReturning(response(sender, 500));
        try {
            sender.send(logMessage(), "session-1");
            fail("expected exception after exhausting retries on error status");
        } catch (HttpSendException e) {
            fail("error status must not raise HttpSendException (no reconnect on message error)");
        } catch (NjamsSdkRuntimeException expected) {
            // expected: server returned an error status repeatedly
        }
    }

    @Test
    public void sendThrowsHttpStatusExceptionCarryingTheCodeAfterRetriesOnErrorStatus() throws IOException {
        HttpSender sender = initializedSender();
        sender.client = mockClientReturning(response(sender, 503));
        try {
            sender.send(logMessage(), "session-1");
            fail("expected HttpStatusException after exhausting retries on error status");
        } catch (HttpStatusException expected) {
            assertEquals(503, expected.getStatusCode());
        }
    }

    @Test
    public void isCongestionTrueOnlyForTooManyRequests() {
        HttpSender sender = initializedSender();
        assertTrue(sender.isCongestion(new HttpStatusException(sender.url, 429)));
    }

    @Test
    public void isCongestionFalseForOtherStatusCodes() {
        HttpSender sender = initializedSender();
        assertFalse(sender.isCongestion(new HttpStatusException(sender.url, 404)));
        assertFalse(sender.isCongestion(new HttpStatusException(sender.url, 500)));
        // Deliberately not congestion: a proxy/gateway or app-level "not ready" signal can mean the actual
        // target is unreachable, not merely busy, and none of these lets the SDK tell the two apart.
        assertFalse(sender.isCongestion(new HttpStatusException(sender.url, 502)));
        assertFalse(sender.isCongestion(new HttpStatusException(sender.url, 503)));
        assertFalse(sender.isCongestion(new HttpStatusException(sender.url, 504)));
    }

    @Test
    public void isCongestionFalseForHttpSendException() {
        HttpSender sender = initializedSender();
        assertFalse(sender.isCongestion(new HttpSendException(sender.url, new IOException("connection refused"))));
    }

    @Test
    public void isCongestionFalseForNull() {
        assertFalse(initializedSender().isCongestion(null));
    }

    @Test
    public void isMessageRejectedTrueForPayloadTooLarge() {
        HttpSender sender = initializedSender();
        assertTrue(sender.isMessageRejected(new HttpStatusException(sender.url, 413)));
    }

    @Test
    public void isMessageRejectedFalseForOtherStatusCodes() {
        HttpSender sender = initializedSender();
        assertFalse(sender.isMessageRejected(new HttpStatusException(sender.url, 503)));
        assertFalse(sender.isMessageRejected(new HttpStatusException(sender.url, 500)));
    }

    @Test
    public void isMessageRejectedFalseForNull() {
        assertFalse(initializedSender().isMessageRejected(null));
    }

    @Test
    public void sendDiscardsImmediatelyOnPayloadTooLargeRegardlessOfDiscardPolicy() throws IOException {
        Map<String, String> props = validProps();
        // "none" would otherwise never give up and keep retrying - proves the bypass is unconditional
        props.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        HttpSender sender = new HttpSender();
        sender.init(settings(props));
        sender.client = mockClientReturning(response(sender, 413));
        sender.send(logMessage(), "session-1");
        verify(sender.client, times(1)).newCall(any(Request.class));
    }

    @Test
    public void sendRetriesPastTransportRetryBudgetOnCongestionUnderConnectionLossPolicy() throws IOException {
        Map<String, String> props = validProps();
        props.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "onconnectionloss");
        HttpSender sender = new HttpSender();
        sender.init(settings(props));
        OkHttpClient client = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(client.newCall(any(Request.class))).thenReturn(call);
        // more failures than MAX_TRIES (20): proves congestion is never discarded or escalated into a throw
        final int congestionAttempts = 25;
        final int[] attempt = { 0 };
        when(call.execute()).thenAnswer(invocation -> {
            attempt[0]++;
            return response(sender, attempt[0] <= congestionAttempts ? 429 : 200);
        });
        sender.client = client;
        sender.send(logMessage(), "session-1");
        verify(sender.client, times(congestionAttempts + 1)).newCall(any(Request.class));
    }

    @Test
    public void sendDiscardsImmediatelyOnServiceUnavailableUnderConnectionLossPolicy() throws IOException {
        Map<String, String> props = validProps();
        props.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "onconnectionloss");
        HttpSender sender = new HttpSender();
        sender.init(settings(props));
        // 503 is deliberately not congestion (an app-level "not ready" signal can outlast any retry budget), so
        // onconnectionloss must discard on the very first attempt rather than retry indefinitely.
        sender.client = mockClientReturning(response(sender, 503));
        sender.send(logMessage(), "session-1");
        verify(sender.client, times(1)).newCall(any(Request.class));
    }
}
