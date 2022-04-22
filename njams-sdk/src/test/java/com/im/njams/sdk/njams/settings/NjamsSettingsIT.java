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

package com.im.njams.sdk.njams.settings;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsFactory;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class NjamsSettingsIT {

    private Settings settingsToFind;

    @Before
    public void setUp() throws Exception {
        settingsToFind = new Settings();
    }

    @Test
    public void settings_inConstructorOfNjams_withVersion_canBeRetrieved(){
        Njams njams = new Njams(new Path(), "Category", "Version", settingsToFind);

        assertThat(njams.getSettings(), is(equalTo(settingsToFind)));
    }

    @Test
    public void settings_inConstructorOfNjams_withoutVersion_canBeRetrieved(){
        Njams njams = new Njams(new Path(), "Category", settingsToFind);

        assertThat(njams.getSettings(), is(equalTo(settingsToFind)));
    }

    @Test
    public void settings_inConstructorOfNjams_withFactory_withVersion_canBeRetrieved(){
        NjamsFactory factory = new NjamsFactory(new Path(), "Category", settingsToFind, "Version");
        Njams njams = new Njams(factory);

        assertThat(njams.getSettings(), is(equalTo(settingsToFind)));
    }

    @Test
    public void settings_inConstructorOfNjams_withFactory_withoutVersion_canBeRetrieved(){
        NjamsFactory factory = new NjamsFactory(new Path(), "Category", settingsToFind, "Version");
        Njams njams = new Njams(factory);

        assertThat(njams.getSettings(), is(equalTo(settingsToFind)));
    }
}
