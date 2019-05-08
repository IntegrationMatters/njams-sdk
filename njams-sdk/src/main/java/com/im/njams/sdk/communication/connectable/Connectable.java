/*
 */

/*
 */

package com.im.njams.sdk.communication.connectable;

import java.util.Properties;

public interface Connectable {

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

    void stop();
}
