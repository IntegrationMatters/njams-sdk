package com.im.njams.sdk.communication.factories;

import com.im.njams.sdk.Njams;

import java.util.Properties;

public abstract class NjamsCommunication {

    protected ConnectableFactory connectableFactory;

    public NjamsCommunication(Njams njams, Properties properties){
        this.connectableFactory = new ConnectableFactory(njams, properties);
    }

    public abstract void stop();
}
