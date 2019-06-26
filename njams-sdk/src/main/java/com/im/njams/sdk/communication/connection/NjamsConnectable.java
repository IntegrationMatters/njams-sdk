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
package com.im.njams.sdk.communication.connection;

import com.im.njams.sdk.communication.connectable.Connectable;
import com.im.njams.sdk.communication.pools.ConnectablePool;

public abstract class NjamsConnectable {

    //The connectablePool where the connectable instances will be safed.
    protected ConnectablePool<Connectable> connectablePool = null;

    protected <T extends Connectable> void setConnectablePool(ConnectablePool<T> pool) {
        this.connectablePool = (ConnectablePool<Connectable>) pool;
    }

    public void stop() {
        stopBeforeConnectablePoolStops();
        stopConnectablePool();
        stopAfterConnectablePoolStops();
    }

    protected void stopBeforeConnectablePoolStops() {
        //Do nothing as default
    }

    protected void stopConnectablePool(){
        if (connectablePool != null) {
            connectablePool.expireAll();
        }
    }

    protected void stopAfterConnectablePoolStops() {
        //Do nothing as default
    }
}
