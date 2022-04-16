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
 */
package com.im.njams.sdk.njams;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.TestReceiver;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.settings.Settings;

public class NjamsTest {

    private Njams njams;

    @Before
    public void createNewInstance() {
        final Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestReceiver.NAME);
        njams = new Njams(new Path(), "TestClientVersion", "TestCategory", settings);
    }

    @Test
    public void testStopBeforeStart() {
        NjamsSdkRuntimeException njamsSdkRuntimeException = assertThrows(NjamsSdkRuntimeException.class,
            njams::stop);
        assertThat(njamsSdkRuntimeException.getMessage(), is(equalTo("The instance needs to be started first!")));

    }

    @Ignore
    @Test
    public void testStartStopStart() {
        njams.start();
        njams.stop();
        njams.start();
    }
}
