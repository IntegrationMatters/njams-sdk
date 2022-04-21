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

package com.im.njams.sdk;

import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.njams.mock.MockingNjamsFactory;
import com.im.njams.sdk.njams.mock.NjamsJobsMock;
import com.im.njams.sdk.settings.Settings;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class NjamsWithNjamsJobsStopTest {

    private Njams njams;
    private NjamsJobsMock njamsJobsMock;

    @BeforeClass
    public static void setNjamsFactory(){
        Njams.setNjamsFactory(new MockingNjamsFactory());
    }

    @AfterClass
    public static void cleanUp(){
        Njams.setNjamsFactory(null);
    }

    @Before
    public void setUp() {
        njams = new Njams(new Path(), "SDK", new Settings());

        njamsJobsMock = (NjamsJobsMock) njams.getNjamsJobs();
    }

    @Test
    public void callsStop_njamsJobsIsNotStoppedBeforeStarting(){
        try {
            njams.stop();
        }catch(Exception someException){
            //We don't care for the exception
        }
        njamsJobsMock.assertThatStopWasCalledTimes(0);
    }

    @Test
    public void callsStop_afterAStart_njamsJobsStopIsCalled(){
        njams.start();
        njams.stop();

        njamsJobsMock.assertThatStopWasCalledTimes(1);
    }

    @Test
    public void callsStopTwice_afterAStart_firstStopStopsNjamsJobs_secondOneIsNotExecuted(){
        njams.start();
        njams.stop();
        try {
            njams.stop();
        }catch(Exception someException){
            //We don't care for the exception
        }
        njamsJobsMock.assertThatStopWasCalledTimes(1);
    }

    @Test
    public void callsStart_andStop_alternating_afterEachStartAStopStopsNjamsJobs(){
        njams.start();
        njams.stop();
        njams.start();
        njams.stop();

        njamsJobsMock.assertThatStopWasCalledTimes(2);
    }

    @Test
    public void callsStartTwice_stopStillOnlyCallsOneStopOnNjamsJobs(){
        njams.start();
        njams.start();
        njams.stop();
        try {
            njams.stop();
        }catch(Exception someException){
            //We don't care for the exception
        }
        njamsJobsMock.assertThatStopWasCalledTimes(1);
    }
}
