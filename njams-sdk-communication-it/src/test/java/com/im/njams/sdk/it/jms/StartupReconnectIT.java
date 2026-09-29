package com.im.njams.sdk.it.jms;

import java.util.Collection;
import java.util.Map;
import java.util.function.IntSupplier;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.it.support.Deliveries;
import com.im.njams.sdk.it.support.DiscardMode;
import com.im.njams.sdk.it.support.StartupReconnectScenario;
import com.im.njams.sdk.settings.Settings;

public class StartupReconnectIT extends StartupReconnectScenario {

    public StartupReconnectIT(DiscardMode mode) {
        super(mode);
    }

    @Override
    protected String proxy() {
        return "jms";
    }

    @Override
    protected int maxDeliveriesPerLogId() {
        return 2; // JMS: one attempt plus at most one ambiguous-outcome retry
    }

    @Override
    protected void configureTransport(Settings settings) {
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        env.configureJms(settings);
    }

    @Override
    protected Map<String, Integer> deliveries(Collection<String> logIds, DiscardMode mode, IntSupplier discards)
        throws Exception {
        return Deliveries.viaJms(env, logIds, mode, discards);
    }
}
