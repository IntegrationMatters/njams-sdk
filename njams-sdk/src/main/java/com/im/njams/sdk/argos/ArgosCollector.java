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
 * This class will create statistics.
 *
 * @param <T> the type of the statistics that will be created.
 */
public abstract class ArgosCollector<T extends ArgosStatistics> {

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
     * Creates the ArgosStatistics of type {@link T}. Sets the fields of the abstract ArgosStatistics
     * with values of the ArgosComponent if they are not set while creating.
     *
     * @return the created and filled statistics.
     */
    public T collect() {
        T argosStatistics = create();
        addComponentFieldsToStatistics(argosStatistics);
        return argosStatistics;
    }

    private void addComponentFieldsToStatistics(T argosStatistics) {
        if (argosStatistics.getId().equals(ArgosStatistics.DEFAULT)) {
            argosStatistics.setId(argosComponent.getId());
        }
        if (argosStatistics.getName().equals(ArgosStatistics.DEFAULT)) {
            argosStatistics.setName(argosComponent.getName());
        }
        if (argosStatistics.getContainerId().equals(ArgosStatistics.DEFAULT)) {
            argosStatistics.setContainerId(argosComponent.getContainerId());
        }
        if (argosStatistics.getMeasurement().equals(ArgosStatistics.DEFAULT)) {
            argosStatistics.setMeasurement(argosComponent.getMeasurement());
        }
        if (argosStatistics.getType().equals(ArgosStatistics.DEFAULT)) {
            argosStatistics.setType(argosComponent.getType());
        }
    }

    /**
     * Creates the concrete statistics.
     *
     * @return the statistics
     */
    protected abstract T create();
}
