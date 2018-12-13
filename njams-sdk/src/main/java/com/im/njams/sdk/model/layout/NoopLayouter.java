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
package com.im.njams.sdk.model.layout;

import com.im.njams.sdk.model.ProcessModel;
import org.slf4j.LoggerFactory;

/**
 * This Layouter does nothing. It can be used if you want to set the
 * x and y coordinates by hand.
 * 
 * @author krautenberg@integrationmatters.com
 * @version 4.0.3
 */
public class NoopLayouter implements ProcessModelLayouter{

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NoopLayouter.class);
    
    /**
     * This method does nothing, it used prints that the NoopLayouter is used.
     * The ActivityModel coordinates have to be set by hand.
     * @param processModel the ProcessModel that won't be layouted by this method.
     */
    @Override
    public void layout(ProcessModel processModel) {
        LOG.debug("The NoopLayouter is used.");
    }
    
}
