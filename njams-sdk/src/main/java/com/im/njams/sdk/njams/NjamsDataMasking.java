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
package com.im.njams.sdk.njams;

import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.logmessage.DataMaskingType;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class NjamsDataMasking {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsDataMasking.class);

    /**
     * Property njams.sdk.datamasking.enabled
     */
    public static final String DATA_MASKING_ENABLED = "njams.sdk.datamasking.enabled";

    /**
     * Property njams.sdk.datamasking.regex.
     */
    public static final String DATA_MASKING_REGEX_PREFIX = "njams.sdk.datamasking.regex.";

    private final List<DataMaskingType> dataMaskingTypes = new ArrayList<>();

    private boolean isDataMaskingEnabled;

    /**
     * Initializes the data masking feature, but it will overwrite the dataMasking of other nJAMS instances that use
     * this method. Instead, you should use createFrom(Settings, Configuration)
     *
     * @param settings      The settings to read from if datamasking is enabled or not and to add pattern if there
     * are any.
     * @param configuration The deprecated configuration where the patterns were saved before.
     */
    @Deprecated
    public static void start(Settings settings, Configuration configuration) {
        DataMasking.setNjamsDataMaskingIfAbsentOrMerge(createFrom(settings, configuration));
    }

    /**
     * Creates a NjamsDataMasking object that can be used to enable and disable data masking and allows adding data
     * masking
     * patterns.
     *
     * @param settings      The settings to read from if data masking is enabled or not and to add pattern if there
     *                      are any.
     * @param configuration The deprecated configuration where the patterns were saved before.
     * @return a NjamsDataMasking instance with patterns and en- or disabled masking.
     */
    public static NjamsDataMasking createFrom(Settings settings, Configuration configuration) {
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
            dataMaskingEnabled = Boolean.parseBoolean(settings.getProperty(DATA_MASKING_ENABLED, "true"));
        }
        return dataMaskingEnabled;
    }

    /**
     * Creates a NjamsDataMasking instance with enabled masking and without any patterns.
     */
    public NjamsDataMasking() {
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
        if (settings != null) {
            Map<String, String> maskings = new HashMap<>();
            settings.getAllProperties().keySet().stream()
                .filter((key) -> ((String) key).startsWith(DATA_MASKING_REGEX_PREFIX)).forEach((key) -> {
                    String name = ((String) key).substring(DATA_MASKING_REGEX_PREFIX.length());
                    maskings.put(name, settings.getProperty((String) key));
                });
            putAll(maskings);
        }
    }

    @Deprecated
    private void addPatternsFrom(Configuration configuration) {
        if (!configuration.getDataMasking().isEmpty()) {
            printDeprecatedConfiguration();
            Map<String, String> maskings = new HashMap<>();
            configuration.getDataMasking().forEach((value) -> maskings.put(null, value));
            putAll(maskings);
        }
    }

    private void putAll(Map<String, String> maskings) {
        maskings.forEach(this::add);
    }

    @Deprecated
    private void printDeprecatedConfiguration() {
        LOG.warn("DataMasking via the configuration is deprecated but will be used as well. Use settings " +
                 "with the properties \n{} = " +
                 "\"true\" \nand multiple \n{}<YOUR-REGEX-NAME> = <YOUR-REGEX> \nfor this.", DATA_MASKING_ENABLED,
            DATA_MASKING_REGEX_PREFIX);
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
     *
     * @param nameOfPattern the name of the pattern
     * @param pattern       the actual pattern to mask the strings with
     */
    public void add(String nameOfPattern, String pattern) {
        try {
            String nameToAdd = (nameOfPattern != null && !nameOfPattern.isEmpty()) ? nameOfPattern :
                "" + dataMaskingTypes.size();
            DataMaskingType dataMaskingTypeToAdd = new DataMaskingType(nameToAdd, pattern);
            dataMaskingTypes.add(dataMaskingTypeToAdd);
            LOG.info("Added masking pattern \"{}\" with regex: \"{}\"", dataMaskingTypeToAdd.getNameOfPattern(),
                dataMaskingTypeToAdd.getRegex());
        } catch (Exception e) {
            LOG.error("Could not add pattern " + pattern, e);
        }
    }

    /**
     * Tries to match with one of the patterns that were added with {@link NjamsDataMasking#add(String, String)} before.
     * If it matches, the match will be substituted with gibberish.
     * <p>
     * It won't be masked if masking is disabled!
     *
     * @param stringToMask the string that will be tried to mask.
     * @return the masked string if one or multiple matching patterns were found and data masking was enabled.
     * Otherwise, it returns the input.
     */
    public String mask(String stringToMask) {
        if (isDataMaskingPossibleFor(stringToMask)) {
            return maskString(stringToMask);
        } else {
            return stringToMask;
        }
    }

    private boolean isDataMaskingPossibleFor(String stringToMask) {
        return isDataMaskingEnabled && stringToMask != null && !stringToMask.isEmpty() && !dataMaskingTypes.isEmpty();
    }

    private String maskString(String stringToMask) {
        StringMatcher stringMatcher = new StringMatcher(stringToMask);
        for (DataMaskingType dataMaskingType : dataMaskingTypes) {
            stringMatcher.tryToMatchWith(dataMaskingType);
        }
        logResultingString(stringMatcher);
        return stringMatcher.getMaskedString();
    }

    private void logResultingString(StringMatcher matcher) {
        if (matcher.hasMatchedAtLeastOnce() && LOG.isTraceEnabled()) {
            LOG.trace("Masked String: {}", matcher.getMaskedString());
        }
    }

    /**
     * Removes all the data masking patterns.
     */
    public void clear() {
        dataMaskingTypes.clear();
    }

    /**
     * Merges the data masking patterns from the given njamsDataMasking with the ones of this instance.
     *
     * @param njamsDataMasking the patterns of this instance will be merged with the ones of this instance.
     */
    public void mergeWith(NjamsDataMasking njamsDataMasking) {
        this.dataMaskingTypes.addAll(njamsDataMasking.dataMaskingTypes);
    }

    private static class StringMatcher {

        private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(StringMatcher.class);

        private static final String MASK_CHAR = "*";

        private final String original;
        private boolean hasBeenChanged;
        private String masked;

        private StringMatcher(String stringToMatch) {
            this.original = stringToMatch;
            this.hasBeenChanged = false;
            this.masked = stringToMatch;
        }

        private void tryToMatchWith(DataMaskingType dataMaskingType) {
            Matcher m = dataMaskingType.getPattern().matcher(original);
            while (m.find()) {
                int startIdx = m.start();
                int endIdx = m.end();

                String patternMatch = original.substring(startIdx, endIdx);
                String partToBeMasked = patternMatch;
                String mask = "";
                for (int i = 0; i < partToBeMasked.length(); i++) {
                    mask = mask + MASK_CHAR;
                }

                String maskedNumber = mask + patternMatch.substring(patternMatch.length());
                masked = masked.replace(patternMatch, maskedNumber);
                hasBeenChanged = true;
            }
            logUsedDataMaskingType(dataMaskingType);
        }

        private void logUsedDataMaskingType(DataMaskingType dataMaskingType) {
            if (hasBeenChanged) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("\nApplied masking of pattern: \"{}\". \nThe regex is: \"{}\"",
                        dataMaskingType.getNameOfPattern(), dataMaskingType.getRegex());
                }
            }
        }

        private boolean hasMatchedAtLeastOnce() {
            return hasBeenChanged;
        }

        private String getMaskedString() {
            return masked;
        }
    }
}