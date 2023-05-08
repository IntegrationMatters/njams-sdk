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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * pool for Sender sub-classes
 *
 * @author hsiegeln
 */
public class SenderPool {
    private static final Logger LOG = LoggerFactory.getLogger(SenderPool.class);

    private final CommunicationFactory factory;
    // TODO: SDK-356: value is never used
    private final Map<AbstractSender, Long> locked = new ConcurrentHashMap<>();
    // TODO: SDK-356: value is never used
    private final Map<AbstractSender, Long> unlocked = new ConcurrentHashMap<>();
    private final Collection<SenderExceptionListener> exceptionListeners =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public SenderPool(CommunicationFactory factory) {
        this.factory = factory;
    }

    /**
     * Add a listener that is called whenever an exception occurs on sending a message.
     * @param listener
     */
    public void addSenderExceptionListener(SenderExceptionListener listener) {
        exceptionListeners.add(listener);
        unlocked.keySet().forEach(s -> s.addExceptionListener(listener));
        locked.keySet().forEach(s -> s.addExceptionListener(listener));
    }

    protected AbstractSender create() {
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
        sender.close();
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
        long now = System.currentTimeMillis();
        // try to get an object, until timeout expires
        do {
            if (!unlocked.isEmpty()) {
                final Iterator<AbstractSender> it = unlocked.keySet().iterator();
                while (it.hasNext()) {
                    sender = it.next();
                    if (validate(sender)) {
                        it.remove();
                        locked.put(sender, now);
                        LOG.trace("Got Sender: {}", sender);
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
            sender = create();
            locked.put(sender, now);
        } while (sender == null && (timeout < 0 || System.currentTimeMillis() - now < timeout));
        LOG.trace("Created Sender: " + sender);
        return sender;
    }

    public synchronized void close(AbstractSender t) {
        locked.remove(t);
        unlocked.put(t, System.currentTimeMillis());
        LOG.trace("Close locked={}, unlocked={}", locked.size(), unlocked.size());
    }

    private synchronized void expireAll() {
        for (AbstractSender sender : locked.keySet()) {
            try {
                destroy(sender);
            } catch (Exception e) {
                LOG.error("Couldn't close {}", sender.getClass().getSimpleName());
            }
        }
        locked.clear();
        for (AbstractSender sender : unlocked.keySet()) {
            try {
                destroy(sender);
            } catch (Exception e) {
                LOG.error("Couldn't close {}", sender.getClass().getSimpleName());
            }
        }
        unlocked.clear();
    }
}
