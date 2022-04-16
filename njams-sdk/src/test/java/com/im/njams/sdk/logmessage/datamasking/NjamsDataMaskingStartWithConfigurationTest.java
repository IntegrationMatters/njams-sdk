package com.im.njams.sdk.logmessage.datamasking;

import com.im.njams.sdk.NjamsDataMasking;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * This is the old version of setting data masking pattern, over the configuration.
 * It is still supported, so here are some tests for that.
 */
public class NjamsDataMaskingStartWithConfigurationTest {

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
    public void withDataMasking_enabled_perDefault_andMaskEverythingPattern_masksString() {
        configurationDataMasking.add(".*");
        NjamsDataMasking.start(settings, configuration);

        assertEquals("*****", DataMasking.maskString("Hello"));
    }

    @Test
    public void withDataMasking_enabled_perSettings_andMaskEverythingPattern_masksString() {
        settings.put(NjamsDataMasking.DATA_MASKING_ENABLED, "true");
        configurationDataMasking.add(".*");
        NjamsDataMasking.start(settings, configuration);

        assertEquals("*****", DataMasking.maskString("Hello"));
    }

    @Test
    public void withoutSettings_configurationWillAlwaysUseDataMasking() {
        configurationDataMasking.add(".*");
        NjamsDataMasking.start(null, configuration);

        assertEquals("*****", DataMasking.maskString("Hello"));
    }

    @Test
    public void withDataMasking_disabled_andMaskEverythingPattern_doesNotAffectTheString() {
        settings.put(DataMasking.DATA_MASKING_ENABLED, "false");
        configurationDataMasking.add(".*");

        NjamsDataMasking.start(settings, configuration);

        assertEquals("Hello", DataMasking.maskString("Hello"));
    }

    @Test
    public void withDataMasking_enabled_withoutAnyMaskingPattern_doesNotAffectTheString() {
        settings.put(DataMasking.DATA_MASKING_ENABLED, "true");

        NjamsDataMasking.start(settings, configuration);
        assertEquals("Hello", DataMasking.maskString("Hello"));
    }
}
