package com.im.njams.sdk.communication.connection.receiver;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.connection.NjamsConnectable;
import com.im.njams.sdk.communication.pools.ReceiverPool;

import java.util.Properties;

public class NjamsReceiver extends NjamsConnectable {

    public NjamsReceiver(Njams njams, Properties properties) {
        super();
        setConnectablePool(new ReceiverPool(njams, properties));
        //This initializes exactly one receiver for this njamsReceiver
        connectablePool.get();
    }
}
