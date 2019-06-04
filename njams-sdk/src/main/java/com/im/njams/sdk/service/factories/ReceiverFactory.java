package com.im.njams.sdk.service.factories;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.connectable.receiver.Receiver;

import java.util.Properties;

public class ReceiverFactory extends ConnectableFactory {

    private final Njams njams;

    private Receiver receiverInstance;

    public ReceiverFactory(Properties properties, Njams njams) {
        super(properties, Receiver.class);

        this.njams = njams;
    }

    public Receiver getInstance(){
        if(receiverInstance == null){
            receiverInstance = super.getInstance();
            //Todo: Validate here classpath
            receiverInstance.setNjams(njams);
            receiverInstance.init(properties);
        }
        return receiverInstance;
    }
}
