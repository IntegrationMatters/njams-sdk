/*
 */

package com.im.njams.sdk.communication.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public abstract class AbstractConnector implements Connector {

    //The Logger
    private static final Logger LOG = LoggerFactory.getLogger(AbstractConnector.class);

    protected Properties properties;

    protected NjamsConnection njamsConnection;

    public AbstractConnector(Properties properties, String name) {
        this.properties = properties;
        this.njamsConnection = new NjamsConnection(this, name);
    }

    public final void start() {
        if (properties == null) {
            LOG.error("Couldn't start the AbstractConnector, because the properties are null");
        } else if (properties.isEmpty()) {
            LOG.error("Couldn't start the AbstractConnector, because the properties are empty");
        } else if (njamsConnection == null) {
            LOG.error("Couldn't start the AbstractConnector, because the njamsConnection is null");
        } else {
            njamsConnection.initialConnect();
        }
    }

    @Override
    public final NjamsConnection getNjamsConnection(){
        return njamsConnection;
    }
}
