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

package com.im.njams.sdk.njams.start;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.njams.util.NjamsFactoryUtils;
import com.im.njams.sdk.njams.util.mock.MockingNjamsFactory;
import com.im.njams.sdk.njams.util.mock.NjamsConfigurationMock;
import org.junit.Before;
import org.junit.Test;

public class NjamsConfigurationTest {

    private Njams njams;
    private NjamsConfigurationMock njamsConfigurationMock;

    @Before
    public void setUp() {
        final MockingNjamsFactory mockingNjamsFactory = NjamsFactoryUtils.createMockedNjamsFactory();
        njams = new Njams(mockingNjamsFactory);

        njamsConfigurationMock = mockingNjamsFactory.getNjamsConfiguration();
    }

    @Test
    public void startIsCalled_whenNjamsStartIsCalled(){
        njams.start();

        njamsConfigurationMock.assertThatStartWasCalledTimes(1);
    }

    @Test
    public void startIsCalled_onlyOnce_evenIfNjamsCallsStart_twice(){
        njams.start();
        njams.start();

        njamsConfigurationMock.assertThatStartWasCalledTimes(1);
    }

    @Test
    public void startIsCalled_aSecondTime_ifNjamsDoesAStopInBetween(){
        njams.start();
        njams.stop();
        njams.start();

        njamsConfigurationMock.assertThatStartWasCalledTimes(2);
    }
}
