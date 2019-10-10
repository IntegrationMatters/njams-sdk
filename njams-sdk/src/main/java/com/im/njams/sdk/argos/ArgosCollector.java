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

    public ArgosCollector(ArgosComponent argosComponent) {
        this.argosComponent = argosComponent;
    }

    public ArgosComponent getArgosComponent() {
        return argosComponent;
    }

    public T collect() {
        T argosStatistics = create();
        addComponentFieldsToStatistics(argosStatistics);
        return argosStatistics;
    }

    private void addComponentFieldsToStatistics(T argosStatistics) {
        if (argosStatistics.getId().equals(ArgosMetric.DEFAULT)) {
            argosStatistics.setId(argosComponent.getId());
        }
        if (argosStatistics.getName().equals(ArgosMetric.DEFAULT)) {
            argosStatistics.setName(argosComponent.getName());
        }
        if (argosStatistics.getContainerId().equals(ArgosMetric.DEFAULT)) {
            argosStatistics.setContainerId(argosComponent.getContainerId());
        }
        if(argosStatistics.getMeasurement().equals(ArgosMetric.DEFAULT)){
            argosStatistics.setMeasurement(argosComponent.getMeasurement());
        }
        if (argosStatistics.getType().equals(ArgosMetric.DEFAULT)) {
            argosStatistics.setType(argosComponent.getType());
        }
    }

    protected abstract T create();
}
