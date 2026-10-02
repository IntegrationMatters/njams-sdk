package com.im.njams.sdk.it.jms;

import java.util.Collection;
import java.util.Map;
import java.util.function.IntSupplier;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.it.support.Deliveries;
import com.im.njams.sdk.it.support.DiscardMode;
import com.im.njams.sdk.it.support.QueueSaturationScenario;
import com.im.njams.sdk.settings.ClientSettings;

public class QueueSaturationIT extends QueueSaturationScenario {

    public QueueSaturationIT(DiscardMode mode, Blockage blockage) {
        super(mode, blockage);
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
    protected void configureTransport(ClientSettings settings) {
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
