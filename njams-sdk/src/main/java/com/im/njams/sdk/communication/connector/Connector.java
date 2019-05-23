/*
 */

package com.im.njams.sdk.communication.connector;


import com.im.njams.sdk.communication.validator.ClasspathValidatable;

public interface Connector extends AutoCloseable, ClasspathValidatable {

    public static final String SENDER_NAME_ENDING = "-Sender-Connector";

    public static final String RECEIVER_NAME_ENDING = "-Receiver-Connector";

    /**
     * This method should be used to create a connection, and if the startup
     * fails, close all resources. It will be called by the
     *  method. It should throw an
     * NjamsSdkRuntimeException if anything unexpected or unwanted happens. If you don't need to connect to anything,
     * leave this method empty.
     */
    void connect();

    void start();

    void close();

    void stop();

    NjamsConnection getNjamsConnection();
}
