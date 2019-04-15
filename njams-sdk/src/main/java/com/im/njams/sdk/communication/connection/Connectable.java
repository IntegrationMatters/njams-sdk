/*
 */

package com.im.njams.sdk.communication.connection;

import java.util.Properties;

public interface Connectable extends AutoCloseable {

    /**
     * This method should be used to create a connection, and if the startup
     * fails, close all resources. It will be called by the
     *  method. It should throw an
     * NjamsSdkRuntimeException if anything unexpected or unwanted happens. If you don't need to connect to anything,
     * leave this method empty.
     */
    void connect();

    /**
     * The implementation should return its name here, by which it can be
     * identified. This name will be used as value in the
     * CommunicationConfiguration via the Key
     * {@value com.im.njams.sdk.communication.CommunicationFactory#COMMUNICATION}
     *
     * @return the name of the connectable implementation
     */
    String getName();

    /**
     * This method should do all initialization with the given properties
     * @param properties
     */
    void init(Properties properties);
}
