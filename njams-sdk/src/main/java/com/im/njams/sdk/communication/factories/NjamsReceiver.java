package com.im.njams.sdk.communication.factories;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.factories.pools.ReceiverPool;

import java.util.Properties;

public class NjamsReceiver extends NjamsCommunication{

    public NjamsReceiver(Njams njams, Properties properties) {
        super(njams, properties);
    }

    @Override
    protected ReceiverPool setConnectablePool(Njams njams, Properties properties) {
        return new ReceiverPool(njams, properties);
    }
}
