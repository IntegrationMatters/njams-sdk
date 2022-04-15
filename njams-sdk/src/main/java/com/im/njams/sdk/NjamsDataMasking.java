package com.im.njams.sdk;

import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class NjamsDataMasking {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsDataMasking.class);

    private boolean isDataMaskingEnabled;

    /**
     * Initializes the data masking feature, but it will overwrite the dataMasking of other nJAMS instances that use
     * this method. Instead, you should use {@link NjamsDataMasking#createFor(Settings, Configuration))
     * @param settings The settings to read from if datamasking is enabled or not and to add pattern if there are any.
     * @param configuration The deprecated configuration where the patterns were saved before.
     */
    @Deprecated
    public static void start(Settings settings, Configuration configuration) {
        createFor(settings, configuration);
    }

    /**
     * Creates a NjamsDataMasking object that can be used to enable and disable data masking and allows adding data masking
     * patterns.
     * @param settings The settings to read from if data masking is enabled or not and to add pattern if there are any.
     * @param configuration The deprecated configuration where the patterns were saved before.
     * @return a NjamsDataMasking instance with patterns and en- or disabled masking.
     */
    public static NjamsDataMasking createFor(Settings settings, Configuration configuration) {
        NjamsDataMasking njamsDatamasking = new NjamsDataMasking();
        if (isDataMaskingEnabled(settings)) {
            njamsDatamasking.enable();
            njamsDatamasking.addPatternsFrom(settings);
            njamsDatamasking.addPatternsFrom(configuration);
        } else {
            njamsDatamasking.disable();
        }
        return njamsDatamasking;
    }

    private static boolean isDataMaskingEnabled(Settings settings) {
        boolean dataMaskingEnabled = true;
        if (settings != null) {
            dataMaskingEnabled = Boolean.parseBoolean(settings.getProperty(DataMasking.DATA_MASKING_ENABLED, "true"));
        }
        return dataMaskingEnabled;
    }

    /**
     * Creates a NjamsDataMasking instance with enabled masking and without any patterns.
     */
    public NjamsDataMasking(){
        isDataMaskingEnabled = true;
    }

    /**
     * Enables data masking which means that if you call {@link NjamsDataMasking#mask(String)} and added
     * patterns with {@link NjamsDataMasking#add(String, String)} before, the resulting String will be masked.
     */
    public void enable() {
        LOG.info("DataMasking is enabled.");
        isDataMaskingEnabled = true;
    }

    private void addPatternsFrom(Settings settings) {
        if(settings != null){
            Map<String, String> maskings = new HashMap<>();
            settings.getAllProperties().forEach((key, value) -> maskings.put((String) key, (String) value));
            putAll(maskings);
        }
    }

    @Deprecated
    private void addPatternsFrom(Configuration configuration) {
        if(!configuration.getDataMasking().isEmpty()) {
            printDeprecatedConfiguration();
            Map<String, String> maskings = new HashMap<>();
            configuration.getDataMasking().forEach((value) -> maskings.put(null, value));
            putAll(maskings);
        }
    }

    private void putAll(Map<String, String> maskings) {
        for (Map.Entry<String, String> entry : maskings.entrySet()) {
            this.add(entry.getKey(), entry.getValue());
        }
    }

    @Deprecated
    private void printDeprecatedConfiguration() {
        LOG.warn("DataMasking via the configuration is deprecated but will be used as well. Use settings " +
                 "with the properties \n{} = " +
                 "\"true\" \nand multiple \n{}<YOUR-REGEX-NAME> = <YOUR-REGEX> \nfor this.",
            DataMasking.DATA_MASKING_ENABLED, DataMasking.DATA_MASKING_REGEX_PREFIX);
    }

    /**
     * Disables data masking which means that if you call {@link NjamsDataMasking#mask(String)} and added
     * patterns with {@link NjamsDataMasking#add(String, String)} before, the resulting String will not be masked.
     */
    public void disable() {
        LOG.info("DataMasking is disabled.");
        isDataMaskingEnabled = false;
    }

    /**
     * Adds a masking pattern with a corresponding name.
     * The name can be used multiple times.
     * @param nameOfPattern the name of the pattern
     * @param pattern the actual pattern to mask the strings with
     */
    public void add(String nameOfPattern, String pattern) {
        DataMasking.addPattern(nameOfPattern, pattern);
    }

    /**
     * Tries to match with one of the patterns that were added with {@link NjamsDataMasking#add(String, String)} before.
     * If it matches, the match will be substituted with gibberish.
     *
     * It won't be masked if masking is disabled!
     * @param stringToMask the string that will be tried to mask.
     * @return the masked string if one or multiple matching patterns were found and data masking was enabled. Otherwise, it returns the input.
     */
    public String mask(String stringToMask) {
        if(isDataMaskingEnabled)
            return DataMasking.maskString(stringToMask);
        else
            return stringToMask;
    }
}
