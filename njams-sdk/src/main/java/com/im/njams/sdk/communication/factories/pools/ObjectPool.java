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
package com.im.njams.sdk.communication.factories.pools;

import org.slf4j.LoggerFactory;

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * a generic class for pooling unlimited objects of any kind
 * @author hsiegeln
 *
 * @param <T> class to store in this pool
 */
public abstract class ObjectPool<T extends AutoCloseable> {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ObjectPool.class);

    private final Hashtable<T, Long> unlocked, locked;

    public ObjectPool() {
        this.unlocked = new Hashtable<>();
        this.locked = new Hashtable<>();
    }

    protected abstract T create();

    public abstract void expire(T o);

    public synchronized T get() {
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
    public synchronized T get(long timeout) {
        LOG.trace("Get locked={}, unlocked={}", locked.size(), unlocked.size());
        T t = null;
        long now = System.currentTimeMillis();
        // try to get an object, until timeout expires
        do {
            if (unlocked.size() > 0) {
                Enumeration<T> e = unlocked.keys();
                while (e.hasMoreElements()) {
                    t = e.nextElement();
                    try{
                        unlocked.remove(t);
                        locked.put(t, now);
                        LOG.trace("Got Sender: " + t);
                        return t;
                    }catch(Exception ex){
                        // object failed validation
                        LOG.debug("Object failed validation!");
                        unlocked.remove(t);
                        expire(t);
                        t = null;
                    }

                }
            }
            // no objects available, create a new one
            t = create();
            locked.put(t, now);
        } while (t == null && (timeout < 0 || System.currentTimeMillis() - now < timeout));
        return t;
    }

    public synchronized void release(T t) {
        locked.remove(t);
        unlocked.put(t, System.currentTimeMillis());
        LOG.trace("Close locked={}, unlocked={}", locked.size(), unlocked.size());
    }

    public synchronized void expireAll(){
        for(T t : locked.keySet()){
            try {
                t.close();
            } catch (Exception e) {
                LOG.error("Couldn't close {}", t.getClass().getSimpleName());
            }
        }
        locked.clear();
        for(T t : unlocked.keySet()){
            try {
                t.close();
            } catch (Exception e) {
                LOG.error("Couldn't close {}", t.getClass().getSimpleName());
            }
        }
        unlocked.clear();
    }

}