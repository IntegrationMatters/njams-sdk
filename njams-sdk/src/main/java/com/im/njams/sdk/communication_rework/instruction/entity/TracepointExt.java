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
package com.im.njams.sdk.communication_rework.instruction.entity;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.Tracepoint;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Extended version of the Tracepoint for storing the current iterations of a
 * tracepoint
 *
 * @author pnientiedt
 */
public class TracepointExt extends Tracepoint {

    private int currentIterations;

    /**
     * @return the currentIterations
     */
    @XmlTransient
    public int getCurrentIterations() {
        return currentIterations;
    }

    /**
     * @param currentIterations the currentIterations to set
     */
    public void setCurrentIterations(int currentIterations) {
        this.currentIterations = currentIterations;
    }

    /**
     * Increase current iterations for this tracepoint
     */
    public void increaseCurrentIterations() {
        currentIterations++;
    }

    /**
     * Returns if a TracePoint has exceeded his configured iterations
     *
     * @return if iterations exceeded
     */
    public boolean iterationsExceeded() {
        return getIterations() > 0 && currentIterations >= getIterations();
    }
}
