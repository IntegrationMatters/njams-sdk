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
 */
package com.im.njams.sdk.njams.datamasking;

import com.im.njams.sdk.njams.NjamsDataMasking;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NjamsDataMaskingStartWithSettingsTest {

    private Settings settings;
    private Configuration configuration;

    @Before
    public void init(){
        DataMasking.removePatterns();

        settings = new Settings();
        configuration = new Configuration();
    }

    @Test
    public void withDataMasking_enabled_perDefault_andMaskEverythingPattern_masksString() {

        settings.put(NjamsDataMasking.DATA_MASKING_REGEX_PREFIX + "MaskAll", ".*");
        NjamsDataMasking.start(settings, configuration);

        assertEquals("*****", DataMasking.maskString("Hello"));
    }

    @Test
    public void withDataMasking_enabled_perSettings_andMaskEverythingPattern_masksString() {
        settings.put(NjamsDataMasking.DATA_MASKING_ENABLED, "true");
        settings.put(NjamsDataMasking.DATA_MASKING_REGEX_PREFIX + "MaskAll", ".*");
        NjamsDataMasking.start(settings, configuration);

        assertEquals("*****", DataMasking.maskString("Hello"));
    }

    @Test
    public void withDataMasking_disabled_andMaskEverythingPattern_doesNotAffectTheString() {
        settings.put(NjamsDataMasking.DATA_MASKING_ENABLED, "false");
        settings.put(NjamsDataMasking.DATA_MASKING_REGEX_PREFIX + "MaskAll", ".*");

        NjamsDataMasking.start(settings, configuration);

        assertEquals("Hello", DataMasking.maskString("Hello"));
    }

    @Test
    public void withDataMasking_enabled_withoutAnyMaskingPattern_doesNotAffectTheString() {
        settings.put(NjamsDataMasking.DATA_MASKING_ENABLED, "true");

        NjamsDataMasking.start(settings, configuration);
        assertEquals("Hello", DataMasking.maskString("Hello"));
    }

    @Test
    public void withDataMasking_enabled_perDefault_theDataMaskingEnabledSetting_isNotUsed(){
        NjamsDataMasking.start(settings, configuration);

        assertEquals("true", DataMasking.maskString("true"));
    }

    @Test
    public void withDataMasking_enabled_theDataMaskingEnabledSetting_shouldNotBeUsedAsAPattern(){
        settings.put(NjamsDataMasking.DATA_MASKING_ENABLED, "true");

        NjamsDataMasking.start(settings, configuration);

        assertEquals("true", DataMasking.maskString("true"));
    }

    @Test
    public void withDataMasking_enabled_otherSettingsShouldNotBeUsedAsPatterns(){
        settings.put("This_should_not_be_a_pattern", "Not_changed");

        NjamsDataMasking.start(settings, configuration);

        assertEquals("Not_changed", DataMasking.maskString("Not_changed"));
    }
}
