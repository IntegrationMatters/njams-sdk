package com.im.njams.sdk.communication.factories;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.connectable.Connectable;
import com.im.njams.sdk.communication.factories.pools.ConnectablePool;

import java.util.Properties;

public abstract class NjamsCommunication {

    //The connectablePool where the connectable instances will be safed.
    protected ConnectablePool<Connectable> connectablePool = null;

    public NjamsCommunication(Njams njams, Properties properties){
        this.connectablePool = setConnectablePool(njams, properties);
        this.init(properties);
    }

    protected abstract <T extends Connectable> ConnectablePool<T> setConnectablePool(Njams njams, Properties properties);

    protected abstract void init(Properties properties);

    public void stop(){
        if(connectablePool != null){
            connectablePool.close();
        }
    }
}
