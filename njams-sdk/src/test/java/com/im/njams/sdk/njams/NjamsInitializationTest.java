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

package com.im.njams.sdk.njams;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

public class NjamsInitializationTest {
    @Test
    public void njams_withoutPath_throwsAnNjamsSdkRuntimeException(){
        NjamsSdkRuntimeException njamsSdkRuntimeException = assertThrows(NjamsSdkRuntimeException.class,
            () -> new Njams(null, "SDK", new Settings()));
        assertThat(njamsSdkRuntimeException.getMessage(), is(equalTo("Client path need to be provided!")));
    }

    @Test
    public void njams_withoutCategory_throwsAnNjamsSdkRuntimeException(){
        NjamsSdkRuntimeException njamsSdkRuntimeException = assertThrows(NjamsSdkRuntimeException.class,
            () -> new Njams(new Path(), null, new Settings()));
        assertThat(njamsSdkRuntimeException.getMessage(), is(equalTo("Category need to be provided!")));
    }

    @Test
    public void njams_withoutSettings_throwsAnNjamsSdkRuntimeException(){
        NjamsSdkRuntimeException njamsSdkRuntimeException = assertThrows(NjamsSdkRuntimeException.class,
            () -> new Njams(new Path(), "SDK", null));
        assertThat(njamsSdkRuntimeException.getMessage(), is(equalTo("Settings need to be provided!")));
    }

    @Test
    public void njamsFactory_withoutPath_throwsAnNjamsSdkRuntimeException(){
        NjamsSdkRuntimeException njamsSdkRuntimeException = assertThrows(NjamsSdkRuntimeException.class,
            () -> new NjamsFactory(null, "SDK", new Settings()));
        assertThat(njamsSdkRuntimeException.getMessage(), is(equalTo("Client path need to be provided!")));
    }

    @Test
    public void njamsFactory_withoutCategory_throwsAnNjamsSdkRuntimeException(){
        NjamsSdkRuntimeException njamsSdkRuntimeException = assertThrows(NjamsSdkRuntimeException.class,
            () -> new NjamsFactory(new Path(), null, new Settings()));
        assertThat(njamsSdkRuntimeException.getMessage(), is(equalTo("Category need to be provided!")));
    }

    @Test
    public void njamsFactory_withoutSettings_throwsAnNjamsSdkRuntimeException(){
        NjamsSdkRuntimeException njamsSdkRuntimeException = assertThrows(NjamsSdkRuntimeException.class,
            () -> new NjamsFactory(new Path(), "SDK", null));
        assertThat(njamsSdkRuntimeException.getMessage(), is(equalTo("Settings need to be provided!")));
    }
}
