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

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class NjamsDataMaskingStartWithSettingsAndConfigurationTest {

    private Settings settings;
    private Configuration configuration;
    private List<String> configurationDataMasking;

    @Before
    public void init(){
        DataMasking.removePatterns();

        settings = new Settings();
        configurationDataMasking = new ArrayList<>();
        configuration = new Configuration();
        configuration.setDataMasking(configurationDataMasking);
    }

    @Test
    public void withDataMasking_disabled_configurationAndSettingsPatterns_areDisabled() {
        settings.put(NjamsDataMasking.DATA_MASKING_ENABLED, "false");
        settings.put(NjamsDataMasking.DATA_MASKING_REGEX_PREFIX + "MaskAll", ".*");
        configurationDataMasking.add(".*");

        NjamsDataMasking.start(settings, configuration);

        assertEquals("Hello", DataMasking.maskString("Hello"));
    }

    @Test
    public void withDataMasking_enabled_configurationAndSettingsPatterns_areBothUsed(){
        settings.put(NjamsDataMasking.DATA_MASKING_ENABLED, "true");
        settings.put(NjamsDataMasking.DATA_MASKING_REGEX_PREFIX + "MaskOnlyForSettings", "Settings");
        configurationDataMasking.add("configuration");

        NjamsDataMasking.start(settings, configuration);

        String expected = "******** work perfectly for data masking, but ************* also works.";
        String actual = "Settings work perfectly for data masking, but configuration also works.";
        assertEquals(expected, DataMasking.maskString(actual));
    }
}
