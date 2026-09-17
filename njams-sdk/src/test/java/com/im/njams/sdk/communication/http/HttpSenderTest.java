package com.im.njams.sdk.communication.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
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
}
