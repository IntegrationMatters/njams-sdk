package com.im.njams.sdk.communication.connection.receiver;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.connection.NjamsConnectable;
import com.im.njams.sdk.communication.pools.ReceiverPool;

import java.util.Properties;

public class NjamsReceiver extends NjamsConnectable {

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
