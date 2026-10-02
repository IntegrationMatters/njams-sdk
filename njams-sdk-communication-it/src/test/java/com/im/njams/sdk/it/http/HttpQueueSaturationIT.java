package com.im.njams.sdk.it.http;

import java.util.Collection;
import java.util.Map;
import java.util.function.IntSupplier;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.it.support.Deliveries;
import com.im.njams.sdk.it.support.DiscardMode;
import com.im.njams.sdk.it.support.QueueSaturationScenario;
import com.im.njams.sdk.settings.ClientSettings;

public class HttpQueueSaturationIT extends QueueSaturationScenario {

    public HttpQueueSaturationIT(DiscardMode mode, Blockage blockage) {
        super(mode, blockage);
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
}
