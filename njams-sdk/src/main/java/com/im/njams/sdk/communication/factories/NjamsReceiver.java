package com.im.njams.sdk.communication.factories;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.factories.pools.ReceiverPool;

import java.util.Properties;

public class NjamsReceiver extends NjamsCommunication {

    private static final String RECEIVER_COUNT = "njams.client.sdk.receiver.count";

    public NjamsReceiver(Njams njams, Properties properties) {
        super(njams, properties);
    }

    @Override
    protected ReceiverPool setConnectablePool(Njams njams, Properties properties) {
        return new ReceiverPool(njams, properties);
    }

    @Override
    protected final void init(Properties properties) {
        int maxQueueLength = Integer.parseInt(properties.getProperty(RECEIVER_COUNT, "1"));
        for (int i = 0; i < maxQueueLength; i++) {
            connectablePool.get();
        }
    }
}
