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
