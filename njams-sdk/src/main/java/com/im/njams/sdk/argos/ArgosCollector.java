/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.argos;

import java.util.Collection;
import java.util.Collections;

/**
 * Abstract base class for an ArgosCollector
 * <p>
 * Extend this class, implement the collect method and register it in {@link ArgosSender}
 *
 * @param <T> The type of the metric that this collector creates.
 * @see ArgosMultiCollector
 */
public abstract class ArgosCollector<T extends ArgosMetric> extends ArgosMultiCollector<T> {

    /**
     * Sets the given ArgosComponent.
     *
     * @param argosComponent the argosComponent to set.
     */
    public ArgosCollector(ArgosComponent argosComponent) {
        super(argosComponent);
    }

    /**
     * Overwrite this method in your implementation of this class.
     * <p>
     * Create {@link ArgosMetric} and return it.
     *
     * @return the created {@link ArgosMetric}
     */
    protected abstract T create();

    /**
     * Wraps the metric created by {@link #create()} into a collection.
     * @see com.im.njams.sdk.argos.ArgosMultiCollector#createAll()
     */
    @Override
    protected Collection<T> createAll() {
        return Collections.singletonList(create());
    }

    /**
     * This gets called by {@link ArgosSender} in periodic manner.
     * <p>
     * It will create a new {@link ArgosMetric} with the correct implementation
     * and return it so that it can be send via UDP.
     *
     * @deprecated Replaced by {@link ArgosMultiCollector#collectAll()}.
     * @return the collected {@link ArgosMetric}
     */
    @Deprecated
    public T collect() {
        return create();
    }
}
