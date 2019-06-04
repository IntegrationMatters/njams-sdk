package com.im.njams.sdk.service.factories;

import com.im.njams.sdk.communication.connectable.sender.Sender;

import java.util.Properties;

public class SenderFactory extends ConnectableFactory {

    //Todo: Use the ObjectPool here
    private Sender senderInstance;

    public SenderFactory(Properties properties) {
        super(properties, Sender.class);
    }

    public Sender getInstance(){
        if(senderInstance == null){
            senderInstance = super.getInstance();
            //Todo: Validate here classpath
            senderInstance.init(properties);
        }
        return senderInstance;
    }
}
