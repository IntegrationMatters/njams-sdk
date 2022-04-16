package com.im.njams.sdk.logmessage.datamasking;

import com.im.njams.sdk.NjamsDataMasking;
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
