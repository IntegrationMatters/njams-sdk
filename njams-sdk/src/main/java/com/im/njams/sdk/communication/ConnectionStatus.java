package com.im.njams.sdk.communication;

/**
 * Enum for possible connection states
 * 
 * @author hsiegeln
 *
 */
public enum ConnectionStatus {
    /**
     * connection is disconnected
     */
    DISCONNECTED,

    /**
     * connection is being established
     */
    CONNECTING,

    /**
     * connection has been established
     */
    CONNECTED
}
