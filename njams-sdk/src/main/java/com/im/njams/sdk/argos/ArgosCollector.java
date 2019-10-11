/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.argos;

/**
 * Abstract base class for an ArgosCollector
 *
 * Extend this class, implement the collect method and register it in @see {@link ArgosSender}
 *
 * @param <T>
 */
public abstract class ArgosCollector<T extends ArgosMetric> {

    private ArgosComponent argosComponent;

    /**
     * Sets the given ArgosComponent.
     *
     * @param argosComponent the argosComponent to set.
     */
    public ArgosCollector(ArgosComponent argosComponent) {
        this.argosComponent = argosComponent;
    }

    /**
     * Returns the ArgosComponent for this collector.
     *
     * @return the argosComponent for this collector.
     */
    public ArgosComponent getArgosComponent() {
        return argosComponent;
    }

    /**
     * Overwrite this method in your implementation of this class.
     *
     * Create @see {@link ArgosMetric} and return it.
     *
     * @param argosComponent
     * @return the created @see {@link ArgosMetric}
     */
    protected abstract T create(ArgosComponent argosComponent);

    /**
     * This gets called by @see {@link ArgosSender} in periodic manner.
     *
     * It will create a new @see {@link ArgosMetric} with the correct implementation
     * and return it so that it can be send via UDP.
     *
     * @return the collected @see {@link ArgosMetric}
     */
    public T collect() {
        T argosStatistics = create(argosComponent);;
        return argosStatistics;
    }
}
