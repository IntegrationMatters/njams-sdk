package com.im.njams.sdk;

import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class NjamsDataMasking {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsDataMasking.class);

    private boolean isDatamaskingEnabled;

    public NjamsDataMasking(){
        isDatamaskingEnabled = true;
    }

    public static NjamsDataMasking createFor(Settings settings, Configuration configuration) {
        NjamsDataMasking njamsDatamasking = new NjamsDataMasking();
        Properties properties = settings.getAllProperties();
        boolean dataMaskingEnabled = true;
        if (properties != null) {
            dataMaskingEnabled = Boolean.parseBoolean(properties.getProperty(DataMasking.DATA_MASKING_ENABLED, "true"));
            if (dataMaskingEnabled) {
                njamsDatamasking.enableMasking();
                Map<String, String> maskings = new HashMap<>();
                properties.forEach((key, value) -> maskings.put((String) key, (String) value));
                njamsDatamasking.putAll(maskings);
            } else {
                LOG.info("DataMasking is disabled.");
            }
        }
        if (dataMaskingEnabled && !configuration.getDataMasking().isEmpty()) {
            LOG.warn("DataMasking via the configuration is deprecated but will be used as well. Use settings " +
                     "with the properties \n{} = " +
                     "\"true\" \nand multiple \n{}<YOUR-REGEX-NAME> = <YOUR-REGEX> \nfor this.",
                DataMasking.DATA_MASKING_ENABLED, DataMasking.DATA_MASKING_REGEX_PREFIX);
            Map<String, String> maskings = new HashMap<>();
            configuration.getDataMasking().forEach((value) -> maskings.put(null, value));
            njamsDatamasking.putAll(maskings);
        }
        return njamsDatamasking;
    }

    private void putAll(Map<String, String> maskings) {
        for (String key : maskings.keySet()) {
            this.put(key, maskings.get(key));
        }
    }

    /**
     * Initializes the data masking feature, but it will overwrite the dataMasking of other nJAMS instances that use
     * this method. Instead, you should use {@link NjamsDataMasking#createFor(Settings, Configuration))
     * @param njamsSettings
     * @param njamsConfiguration
     */
    @Deprecated
    public static void start(Settings njamsSettings, Configuration njamsConfiguration) {
        createFor(njamsSettings, njamsConfiguration);
    }

    public void enableMasking() {
        isDatamaskingEnabled = true;
    }

    public void disableMasking() {
        isDatamaskingEnabled = false;
    }

    public void put(String keyOfPattern, String pattern) {
        DataMasking.addPattern(keyOfPattern, pattern);
    }

    public String maskString(String stringToMask) {
        if(isDatamaskingEnabled)
            return DataMasking.maskString(stringToMask);
        else
            return stringToMask;
    }
}
