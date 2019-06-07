package com.im.njams.sdk.communication.connector;

public class NullConnector implements Connector {

    @Override
    public void connect() {

    }

    @Override
    public void start() {

    }

    @Override
    public void close() {

    }

    @Override
    public void stop() {

    }

    @Override
    public NjamsConnection getNjamsConnection() {
        return null;
    }
}
