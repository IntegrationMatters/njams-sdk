package com.im.njams.sdk.it.http;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.function.IntSupplier;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.it.support.Deliveries;
import com.im.njams.sdk.it.support.DiscardMode;
import com.im.njams.sdk.it.support.StartupReconnectScenario;
import com.im.njams.sdk.it.support.WireMockJournal;
import com.im.njams.sdk.settings.ClientSettings;

public class HttpStartupReconnectIT extends StartupReconnectScenario {

    public HttpStartupReconnectIT(DiscardMode mode) {
        super(mode);
    }

    @Override
    protected String proxy() {
        return "http";
    }

    @Override
    protected int maxDeliveriesPerLogId() {
        return 5; // HTTP: 1 + 3 quick retries that can each reach the server, plus a resend after reconnect
    }

    @Override
    protected void configureTransport(ClientSettings settings) {
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
    }

    @Override
    protected Map<String, Integer> deliveries(Collection<String> logIds, DiscardMode mode, IntSupplier discards)
        throws Exception {
        return Deliveries.viaHttp(env, logIds, mode, discards);
    }

    /** The WireMock journal is reset per test, so a project message in it is this test's own. */
    @Override
    protected Boolean projectMessageDelivered(Duration timeout) throws Exception {
        try {
            WireMockJournal.awaitProjectMessageSent(env, timeout);
            return Boolean.TRUE;
        } catch (IllegalStateException notSeen) {
            return Boolean.FALSE;
        }
    }
}
