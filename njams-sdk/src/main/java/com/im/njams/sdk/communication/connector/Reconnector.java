/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.communication.connector;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final AtomicBoolean isStoppingOrStopped = new AtomicBoolean(false);

    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);

    private static final AtomicInteger connecting = new AtomicInteger(0);

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
        if (!isStoppingOrStopped.get()) {
            synchronized (isReconnecting) {
                if (!isStoppingOrStopped.get()) {
                    isReconnecting.set(true);
                    njamsConnection.tryToClose();
                    printReconnectExceptionMessage(ex);
                    LOG.info("{} connectors are reconnecting now", connecting.incrementAndGet());
                    do {
                        try {
                            LOG.debug("Trying to reconnect {}", njamsConnection.getName());
                            njamsConnection.tryToConnect();
                            LOG.info("Connection can be established again!");
                            LOG.info("Reconnected {}", njamsConnection.getName());
                            LOG.debug("{} connectors still need to reconnect.", connecting.decrementAndGet());
                        } catch (NjamsSdkRuntimeException e) {
                        } finally {
                            try {
                                //Wait for #reconnectInterval milliseconds, even if the njamsConnection has been established,
                                //to minimize the reconnect method calls by outdated njamsConnection errors. (They might be thrown
                                //right before the njamsConnection has been established again, so they may be obsolete by now)
                                Thread.sleep(reconnectInterval);
                            } catch (InterruptedException e1) {
                                LOG.error("The reconnecting thread was interrupted!", e1);
                                isReconnecting.set(false);
                                break;
                            }
                        }
                    } while (!njamsConnection.isConnected() && !isStoppingOrStopped.get());
                    isReconnecting.set(false);
                }
            }
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

    public void stopReconnecting() {
        isStoppingOrStopped.set(true);
    }
}
