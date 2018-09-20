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
package com.im.njams.sdk.pools;

import java.util.Enumeration;
import java.util.Hashtable;

import org.slf4j.LoggerFactory;

/**
 * a generic class for pooling objects of any kind
 * @author hsiegeln
 *
 * @param <T> class to store in this pool
 */
public abstract class ObjectPool<T> {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ObjectPool.class);

    private Hashtable<T, Long> unlocked, locked;

    public ObjectPool(int maxCapacity) {
        unlocked = new Hashtable<T, Long>();
        locked = new Hashtable<T, Long>();
    }

    protected abstract T create();

    public abstract boolean validate(T o);

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
        T t = null;
        long now = System.currentTimeMillis();
        // try to get an object, until timeout expires
        do {
            if (unlocked.size() > 0) {
                Enumeration<T> e = unlocked.keys();
                while (e.hasMoreElements()) {
                    t = e.nextElement();
                    if (validate(t)) {
                        unlocked.remove(t);
                        locked.put(t, now);
                        return (t);
                    } else {
                        // object failed validation
                        unlocked.remove(t);
                        expire(t);
                        t = null;
                    }

                }
            }
            // no objects available, create a new one
            t = create();
        } while (t == null && (timeout < 0 || System.currentTimeMillis() - now < timeout));
        return (t);
    }

    public synchronized void close(T t) {
        locked.remove(t);
        unlocked.put(t, System.currentTimeMillis());
    }
}