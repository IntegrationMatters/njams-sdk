/*
 */

package com.im.njams.sdk.communication.connection;

import com.im.njams.sdk.utils.ClasspathValidator;

public interface Connector extends AutoCloseable, ClasspathValidator {

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

    NjamsConnection getNjamsConnection();
}
