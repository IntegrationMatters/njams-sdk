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

import org.slf4j.LoggerFactory;

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * pool for Sender sub-classes
 *
 * @author hsiegeln
 */
public class SenderPool {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SenderPool.class);

    private final CommunicationFactory factory;
    private final Hashtable<AbstractSender, Long> unlocked, locked;

    public SenderPool(CommunicationFactory factory) {
        super();
        this.factory = factory;
        unlocked = new Hashtable<>();
        locked = new Hashtable<>();
    }

    protected AbstractSender create() {
        return factory.getSender();
    }

    public boolean validate(AbstractSender sender) {
        // TODO: there must be a better solution!
        return true;
    }

    public void expire(AbstractSender sender) {
        sender.close();
    }

    public void shutdown() {
        expireAll();
    }

    public synchronized AbstractSender get() {
        return get(-1);
    }

    /**
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
            if (unlocked.size() > 0) {
                Enumeration<AbstractSender> e = unlocked.keys();
                while (e.hasMoreElements()) {
                    sender = e.nextElement();
                    if (validate(sender)) {
                        unlocked.remove(sender);
                        locked.put(sender, now);
                        LOG.trace("Got Sender: " + sender);
                        return sender;
                    } else {
                        // object failed validation
                        LOG.debug("Object failed validation!");
                        unlocked.remove(sender);
                        expire(sender);
                        sender = null;
                    }

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

    public synchronized void expireAll() {
        for (AbstractSender sender : locked.keySet()) {
            try {
                sender.setShouldShutdown(true);
                sender.close();
            } catch (Exception e) {
                LOG.error("Couldn't close {}", sender.getClass().getSimpleName());
            }
        }
        locked.clear();
        for (AbstractSender sender : unlocked.keySet()) {
            try {
                sender.setShouldShutdown(true);
                sender.close();
            } catch (Exception e) {
                LOG.error("Couldn't close {}", sender.getClass().getSimpleName());
            }
        }
        unlocked.clear();
    }
}
