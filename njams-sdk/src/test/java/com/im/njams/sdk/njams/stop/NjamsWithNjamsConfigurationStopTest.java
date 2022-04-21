/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
 *
 */

package com.im.njams.sdk.njams.stop;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.njams.util.NjamsFactoryUtils;
import com.im.njams.sdk.njams.util.mock.MockingNjamsFactory;
import com.im.njams.sdk.njams.util.mock.NjamsConfigurationMock;
import org.junit.Before;
import org.junit.Test;

public class NjamsWithNjamsConfigurationStopTest {
    private Njams njams;
    private NjamsConfigurationMock njamsConfigurationMock;

    @Before
    public void setUp() {
        final MockingNjamsFactory mockingNjamsFactory = NjamsFactoryUtils.createMockedNjamsFactory();
        njams = new Njams(mockingNjamsFactory);

        njamsConfigurationMock = mockingNjamsFactory.getNjamsConfiguration();
    }

    @Test
    public void callsStop_njamsConfigurationIsNotStoppedBeforeStarting(){
        try {
            njams.stop();
        }catch(Exception someException){
            //We don't care for the exception
        }
        njamsConfigurationMock.assertThatStopWasCalledTimes(0);
    }

    @Test
    public void callsStop_afterAStart_njamsConfigurationStopIsCalled(){
        njams.start();
        njams.stop();

        njamsConfigurationMock.assertThatStopWasCalledTimes(1);
    }

    @Test
    public void callsStopTwice_afterAStart_firstStopStopsNjamsConfiguration_secondOneIsNotExecuted(){
        njams.start();
        njams.stop();
        try {
            njams.stop();
        }catch(Exception someException){
            //We don't care for the exception
        }
        njamsConfigurationMock.assertThatStopWasCalledTimes(1);
    }

    @Test
    public void callsStart_andStop_alternating_afterEachStartAStopStopsNjamsConfiguration(){
        njams.start();
        njams.stop();
        njams.start();
        njams.stop();

        njamsConfigurationMock.assertThatStopWasCalledTimes(2);
    }

    @Test
    public void callsStartTwice_stopStillOnlyCallsOneStopOnNjamsConfiguration(){
        njams.start();
        njams.start();
        njams.stop();
        try {
            njams.stop();
        }catch(Exception someException){
            //We don't care for the exception
        }
        njamsConfigurationMock.assertThatStopWasCalledTimes(1);
    }
}
