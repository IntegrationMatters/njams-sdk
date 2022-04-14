package com.im.njams.sdk;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.communication.Sender;

public class NjamsSender {

    private Sender sender;

    public NjamsSender(Sender sender){
        this.sender = sender;
    }

    /**
     * Returns the a Sender implementation, which is configured as specified in
     * the settings.
     *
     * @return the Sender
     */
    public Sender getSender() {
        return sender;
    }

    public void stop() {
        sender.close();
    }

    public void send(CommonMessage message) {
        sender.send(message);
    }
}
