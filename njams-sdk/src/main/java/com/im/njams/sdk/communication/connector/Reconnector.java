/*
 */

package com.im.njams.sdk.communication.connector;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This abstract class is used for reconnecting Connectable instances.
 *
 * @author krautenberg
 * @version 4.0.6
 */
public class Reconnector {

    //The logger
    private static final Logger LOG = LoggerFactory.getLogger(Reconnector.class);

    //The time it needs before a new reconnection is tried after an exception throw.
    private long reconnectInterval;

    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);

    //The njamsConnection that should be reconnected
    private NjamsConnection njamsConnection;

    public Reconnector(NjamsConnection njamsConnection) {
        this(njamsConnection, 1000);
    }

    public Reconnector(NjamsConnection njamsConnection, long reconnectInterval) {
        this.njamsConnection = njamsConnection;
        this.reconnectInterval = reconnectInterval;
    }

    /**
     * This method tries to establish the njamsConnection over and over as long as it
     * not connected. If connect of the connectable throws an exception, the
     * reconnection threads sleeps for
     * {@link #reconnectInterval reconnect interval} seconds before trying again
     * to reconnect.
     *
     * @param ex the exception that initiated the reconnect
     */
    @SuppressWarnings({"squid:S2276", "squid:S2142"})
    public void reconnect(NjamsSdkRuntimeException ex) {
        synchronized (isReconnecting) {
            isReconnecting.set(true);
            njamsConnection.tryToClose();
            printReconnectExceptionMessage(ex);
            while (!njamsConnection.isConnected()) {
                try {
                    LOG.debug("Trying to reconnect {}", njamsConnection.getName());
                    njamsConnection.tryToConnect();
                    LOG.info("Reconnected {}", njamsConnection.getName());
                } catch (NjamsSdkRuntimeException e) {
                } finally{
                    try {
                        //Wait for reconnectInterval milliseconds, even if the njamsConnection has been established,
                        //to minimize the reconnect method calls by outdated njamsConnection errors. (They might be thrown
                        //right before the njamsConnection has been established again, so they may be obsolete by now)
                        Thread.sleep(reconnectInterval);
                    } catch (InterruptedException e1) {
                        LOG.error("The reconnecting thread was interrupted!", e1);
                        isReconnecting.set(false);
                        break;
                    }
                }
            }
            isReconnecting.set(false);
        }
    }

    private void printReconnectExceptionMessage(NjamsSdkRuntimeException ex) {
        if (LOG.isDebugEnabled() && ex != null) {
            if (ex.getCause() == null) {
                LOG.debug("Initialized reconnect, because of : {}", ex.toString());
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Stacktrace: {}", ex.getStackTrace());
                }
            } else {
                LOG.debug("Initialized reconnect, because of : {}, {}", ex.toString(), ex.getCause().toString());
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Stacktrace: {}", ex.getStackTrace(), ex.getCause().getStackTrace());
                }
            }
        }
    }

    AtomicBoolean isReconnecting() {
        return isReconnecting;
    }

    public long getReconnectInterval() {
        return reconnectInterval;
    }
}
