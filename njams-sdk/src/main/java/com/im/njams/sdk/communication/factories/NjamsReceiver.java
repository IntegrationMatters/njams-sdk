package com.im.njams.sdk.communication.factories;

import com.im.njams.sdk.Njams;

import java.util.Properties;

public class NjamsReceiver extends NjamsCommunication{

    //Have a pool with all receivers

    public NjamsReceiver(Njams njams, Properties properties) {
        super(njams, properties);
    }

    public void init(){
//        connectableFactory.getReceiver();
    }
    @Override
    public void stop() {
//    stop all receivers in the pool
    }
}
