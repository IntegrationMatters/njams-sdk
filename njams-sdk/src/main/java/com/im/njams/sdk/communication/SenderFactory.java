package com.im.njams.sdk.communication;

/**
 * implemented by NjamsSender to return the correct sender implementation
 * must not be used by any other class
 * 
 * @author hsiegeln
 *
 */
interface SenderFactory {

    public abstract Sender getSenderImpl();

}
