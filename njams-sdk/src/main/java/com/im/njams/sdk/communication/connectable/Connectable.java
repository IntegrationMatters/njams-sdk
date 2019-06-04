/*
 */

/*
 */

package com.im.njams.sdk.communication.connectable;

import com.im.njams.sdk.communication.connector.Connector;
import com.im.njams.sdk.service.NjamsService;

import java.util.Properties;

public interface Connectable extends NjamsService {

    /**
     * This method should do all initialization with the given properties
     * @param properties
     */
    void init(Properties properties);

    /**
     * This method should stop all processing and close all used resources.
     */
    void stop();

    /**
     * This method return the connector that is used for this connectable
     * @return the connector that is used for this connectable
     */
    Connector getConnector();
}
