package com.im.njams.sdk.communication.connection;

import com.im.njams.sdk.communication.connectable.Connectable;
import com.im.njams.sdk.communication.pools.ConnectablePool;

public abstract class NjamsConnectable {

    //The connectablePool where the connectable instances will be safed.
    protected ConnectablePool<Connectable> connectablePool = null;

    protected <T extends Connectable> void setConnectablePool(ConnectablePool<T> pool) {
        this.connectablePool = (ConnectablePool<Connectable>) pool;
    }

    public void stop() {
        stopBeforeConnectablePoolStops();
        if (connectablePool != null) {
            connectablePool.expireAll();
        }
        stopAfterConnectablePoolStops();
    }

    protected void stopBeforeConnectablePoolStops() {
        //Do nothing as default
    }

    protected void stopAfterConnectablePoolStops() {
        //Do nothing as default
    }
}
