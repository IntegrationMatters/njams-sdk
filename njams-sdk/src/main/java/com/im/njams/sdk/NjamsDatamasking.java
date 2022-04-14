package com.im.njams.sdk;

import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class NjamsDatamasking {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsDatamasking.class);

    /**
     * Initialize the datamasking feature
     * @param njamsSettings
     * @param njamsConfiguration
     */
    public static void initialize(Settings njamsSettings, Configuration njamsConfiguration) {
        Properties properties = njamsSettings.getAllProperties();
        boolean dataMaskingEnabled = true;
        if (properties != null) {
            dataMaskingEnabled = Boolean.parseBoolean(properties.getProperty(DataMasking.DATA_MASKING_ENABLED, "true"));
            if (dataMaskingEnabled) {
                DataMasking.addPatterns(properties);
            } else {
                LOG.info("DataMasking is disabled.");
            }
        }
        if (dataMaskingEnabled && !njamsConfiguration.getDataMasking().isEmpty()) {
            LOG.warn("DataMasking via the configuration is deprecated but will be used as well. Use settings " +
                     "with the properties \n{} = " +
                     "\"true\" \nand multiple \n{}<YOUR-REGEX-NAME> = <YOUR-REGEX> \nfor this.",
                DataMasking.DATA_MASKING_ENABLED, DataMasking.DATA_MASKING_REGEX_PREFIX);
            DataMasking.addPatterns(njamsConfiguration.getDataMasking());
        }
    }
}
