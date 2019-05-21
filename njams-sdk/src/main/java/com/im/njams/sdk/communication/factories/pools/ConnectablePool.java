package com.im.njams.sdk.communication.factories.pools;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.connectable.Connectable;
import com.im.njams.sdk.communication.factories.ConnectableFactory;

import java.util.Properties;

public abstract class ConnectablePool<T extends Connectable> extends ObjectPool<T>{

    protected ConnectableFactory connectableFactory;

    public ConnectablePool(Njams njams, Properties properties) {
        super();
        this.connectableFactory = new ConnectableFactory(njams, properties);
    }

    @Override
    public void expire(T connectable) {
        connectable.stop();
    }
}
