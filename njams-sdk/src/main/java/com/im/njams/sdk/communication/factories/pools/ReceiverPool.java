package com.im.njams.sdk.communication.factories.pools;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.connectable.Receiver;

import java.util.Properties;

public class ReceiverPool extends ConnectablePool<Receiver> {

    public ReceiverPool(Njams njams, Properties properties) {
        super(njams, properties);
    }

    @Override
    protected Receiver create() {
        return connectableFactory.getReceiver();
    }
}
