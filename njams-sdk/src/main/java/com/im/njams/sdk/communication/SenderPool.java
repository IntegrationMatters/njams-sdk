/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pool for {@link AbstractSender} implementations.
 *
 * @author hsiegeln
 */
public class SenderPool {
    private static final Logger LOG = LoggerFactory.getLogger(SenderPool.class);

    private final CommunicationFactory factory;
    private final Set<AbstractSender> locked = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<AbstractSender> unlocked = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Collection<SenderExceptionListener> exceptionListeners =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean shutdown = false;

    public SenderPool(CommunicationFactory factory) {
        this.factory = factory;
    }

    /**
     * Add a listener that is called whenever an exception occurs on sending a message.
     * @param listener The listener to add
     */
    public void addSenderExceptionListener(SenderExceptionListener listener) {
        exceptionListeners.add(listener);
        streamAll().forEach(s -> s.addExceptionListener(listener));
    }

    public boolean isConnectionFailure() {
        return streamAll().anyMatch(AbstractSender::hasConnectionFailure);
    }

    protected AbstractSender create() {
        if (shutdown) {
            return null;
        }
        final AbstractSender sender = factory.getSender();
        if (!exceptionListeners.isEmpty()) {
            exceptionListeners.forEach(sender::addExceptionListener);
        }
        return sender;
    }

    public boolean validate(AbstractSender sender) {
        // TODO: there must be a better solution!
        return true;
    }

    private void destroy(AbstractSender sender) {
        sender.setShouldShutdown(true);
        try {
            sender.close();
        } catch (Exception e) {
            LOG.error("Couldn't close {}", sender.getClass().getSimpleName());
        }
    }

    public void shutdown() {
        expireAll();
    }

    public synchronized AbstractSender get() {
        return get(-1);
    }

    /**
     * TODO: SDK-355: revision required!
     *
     * get an object from the pool. Timeout controls for how long to wait for an object becoming available.
     * a timeout is less than 0 results in an endless wait
     * a timeout equals 0 results in a singly try; if no object is available, null is returned
     *
     * @param timeout timeout in milliseconds
     * @return T T
     */
    private synchronized AbstractSender get(long timeout) {
        LOG.trace("Get locked={}, unlocked={}", locked.size(), unlocked.size());
        AbstractSender sender = null;
        final long now = System.currentTimeMillis();
        // try to get an object, until timeout expires
        do {
            if (!unlocked.isEmpty()) {
                final Iterator<AbstractSender> it = unlocked.iterator();
                while (it.hasNext()) {
                    sender = it.next();
                    if (validate(sender)) {
                        it.remove();
                        locked.add(sender);
                        LOG.trace("Got sender: {}", sender);
                        return sender;
                    }
                    // object failed validation
                    LOG.debug("Sender {} failed validation!", sender);
                    it.remove();
                    destroy(sender);
                    sender = null;
                }
            }
            // no objects available, create a new one
            LOG.trace("Creating new sender, locked={}, unlocked={}", locked.size(), unlocked.size());
            sender = create();
            if (sender != null) {
                if (shutdown) {
                    sender.setShouldShutdown(true);
                }
                locked.add(sender);
            }
        } while (sender == null && !shutdown && (timeout < 0 || System.currentTimeMillis() - now < timeout));
        LOG.debug("Created sender: {} (shouldShutdown={})", sender, shutdown);
        LOG.trace("Create locked={}, unlocked={}", locked.size(), unlocked.size());
        return sender;
    }

    public synchronized void close(AbstractSender t) {
        locked.remove(t);
        unlocked.add(t);
        LOG.trace("Close locked={}, unlocked={}", locked.size(), unlocked.size());
    }

    public void declareShutdown() {
        shutdown = true;
        streamAll().forEach(s -> s.setShouldShutdown(true));
    }

    private synchronized void expireAll() {
        streamAll().forEach(this::destroy);
        locked.clear();
        unlocked.clear();
    }

    private Stream<AbstractSender> streamAll() {
        return Stream.concat(unlocked.stream(), locked.stream());
    }
}
