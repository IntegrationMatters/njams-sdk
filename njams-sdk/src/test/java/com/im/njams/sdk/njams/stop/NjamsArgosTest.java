/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 *
 */

package com.im.njams.sdk.njams.stop;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.njams.util.NjamsFactoryUtils;
import com.im.njams.sdk.njams.util.mock.MockingNjamsFactory;
import com.im.njams.sdk.njams.util.mock.NjamsArgosMock;
import org.junit.Before;
import org.junit.Test;

public class NjamsArgosTest {
    private Njams njams;
    private NjamsArgosMock njamsArgosMock;

    @Before
    public void setUp() {
        final MockingNjamsFactory mockingNjamsFactory = NjamsFactoryUtils.createMockedNjamsFactory();
        njams = new Njams(mockingNjamsFactory);

        njamsArgosMock = mockingNjamsFactory.getNjamsArgos();
    }

    @Test
    public void stopIs_notCalled_ifNjamsTriesToStopBeforeStarting(){
        try {
            njams.stop();
        }catch(Exception someException){
            //We don't care for the exception
        }
        njamsArgosMock.assertThatStopWasCalledTimes(0);
    }

    @Test
    public void stopIs_onceCalled_ifNjamsStopsAfterAStart(){
        njams.start();
        njams.stop();

        njamsArgosMock.assertThatStopWasCalledTimes(1);
    }

    @Test
    public void stopIs_onceCalled_evenIfNjamsStopsTwice(){
        njams.start();
        njams.stop();
        try {
            njams.stop();
        }catch(Exception someException){
            //We don't care for the exception
        }
        njamsArgosMock.assertThatStopWasCalledTimes(1);
    }

    @Test
    public void stopIs_calledTwice_ifNjamsStartsAndStopsAlternating(){
        njams.start();
        njams.stop();
        njams.start();
        njams.stop();

        njamsArgosMock.assertThatStopWasCalledTimes(2);
    }

    @Test
    public void stopIs_calledOnce_evenIfStartWasCalledTwiceBefore_becauseItWasNotAlternating(){
        njams.start();
        njams.start();
        njams.stop();
        try {
            njams.stop();
        }catch(Exception someException){
            //We don't care for the exception
        }
        njamsArgosMock.assertThatStopWasCalledTimes(1);
    }
}
