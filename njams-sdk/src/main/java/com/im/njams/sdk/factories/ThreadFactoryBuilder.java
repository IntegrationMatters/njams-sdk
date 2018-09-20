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
package com.im.njams.sdk.factories;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * a custom thread builder, which allows controlling name and daemon nature of newly created threads
 * 
 * @author hsiegeln
 *
 */
public class ThreadFactoryBuilder {

    /**
     * the prefix to use when naming a thread
     */
    private String namePrefix = null;

    /**
     * controls, whether the new thread is a daemon thread, or not
     * defaults to false
     */
    private boolean daemon = false;

    /**
     * sets the new threads' name prefix
     * 
     * @param namePrefix the prefix to use when building the thread name
     * @return ThreadFactoryBuilder
     */
    public ThreadFactoryBuilder setNamePrefix(String namePrefix) {
        if (namePrefix == null) {
            throw new NullPointerException();
        }
        this.namePrefix = namePrefix;
        return this;
    }

    /**
     * set this to true to create a daemon thread
     * 
     * @param daemon set to true to create a daemon thread; defaults to false
     * @return ThreadFactoryBuilder
     */
    public ThreadFactoryBuilder setDaemon(boolean daemon) {
        this.daemon = daemon;
        return this;
    }

    /**
     * builds and returns the ThreadFactory. Call this as the last method when building ThreadFactory.
     * @return ThreadFactory
     */
    public ThreadFactory build() {
        return build(this);
    }

    /**
     * internal method that creates, configures and returns a new ThreadFactory.
     *  
     * @param builder the ThreadFactoryBuilder to use
     * @return ThreadFactory
     */
    private static ThreadFactory build(ThreadFactoryBuilder builder) {
        final String namePrefix = builder.namePrefix;
        final Boolean daemon = builder.daemon;
        final AtomicLong count = new AtomicLong(0);
        return new ThreadFactory() {

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                if (namePrefix != null) {
                    thread.setName(namePrefix + "-" + count.getAndIncrement());
                }
                if (daemon != null) {
                    thread.setDaemon(daemon);
                }
                return thread;
            }
        };
    }
}