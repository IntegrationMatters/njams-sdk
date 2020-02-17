/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.logmessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DataMasking implementation
 *
 * @author pnientiedt
 */
public class DataMasking {

    /**
     * Property njams.sdk.datamasking.enabled
     */
    public static final String DATA_MASKING_ENABLED = "njams.sdk.datamasking.enabled";

    /**
     * Property njams.sdk.datamasking.regex.
     */
    public static final String DATA_MASKING_REGEX_PREFIX = "njams.sdk.datamasking.regex.";

    private static final Logger LOG = LoggerFactory.getLogger(DataMasking.class);

    private static final String MASK_CHAR = "*";

    private static final List<DataMaskingType> DATA_MASKING_TYPES = new ArrayList<>();

    /**
     * Mask a string by patterns given to this class
     *
     * @param inString String to apply datamasking to
     * @return String with applied datamasking
     */
    public static String maskString(String inString) {
        if (inString == null || inString.isEmpty() || DATA_MASKING_TYPES.isEmpty()) {
            return inString;
        }

        String maskedString = inString;
        boolean foundAtleastOneMatch = false;
        for (DataMaskingType dataMaskingType : DATA_MASKING_TYPES) {
            Matcher m = dataMaskingType.getPattern().matcher(inString);
            while (m.find()) {
                int startIdx = m.start();
                int endIdx = m.end();

                String patternMatch = inString.substring(startIdx, endIdx);
                String partToBeMasked = patternMatch.substring(0, patternMatch.length());
                String mask = "";
                for (int i = 0; i < partToBeMasked.length(); i++) {
                    mask = mask + MASK_CHAR;
                }

                String maskedNumber = mask + patternMatch.substring(patternMatch.length());
                maskedString = maskedString.replace(patternMatch, maskedNumber);
                foundAtleastOneMatch = true;
            }
            if (foundAtleastOneMatch && LOG.isDebugEnabled()) {
                LOG.debug("\nApplied masking of pattern: \"{}\". \nThe regex is: \"{}\"",
                        dataMaskingType.getNameOfPattern(), dataMaskingType.getRegex());
            }
        }
        if (foundAtleastOneMatch && LOG.isTraceEnabled()) {
            LOG.trace("Masked String: {}", maskedString);
        }
        return maskedString;
    }

    /**
     * This method adds all patterns to the pattern list
     *
     * @param patterns the patterns to add
     */
    public static void addPatterns(List<String> patterns) {
        patterns.forEach(DataMasking::addPattern);
    }

    /**
     * This method adds a pattern to the pattern list.
     *
     * @param pattern the pattern to add
     */
    public static void addPattern(String pattern) {
        addPattern(null, pattern);
    }

    /**
     * This method takes a reads all key-value pairs where the key starts with {@link #DATA_MASKING_REGEX_PREFIX} and
     * adds them to a data masking list.
     *
     * @param properties the properties to provide
     */
    public static void addPatterns(Properties properties) {
        properties.keySet().stream().filter((key) -> ((String) key).startsWith(DATA_MASKING_REGEX_PREFIX))
                .forEach((key) -> {
                    String name = ((String) key).substring(DATA_MASKING_REGEX_PREFIX.length());
                    addPattern(name, properties.getProperty((String) key));
                });
    }

    /**
     * Add a new pattern for masking data. If you don't provide a name for the pattern, it will be the index of
     * pattern in the list of masking patterns.
     *
     * @param nameOfPattern the name of the pattern
     * @param regexAsString the pattern to add
     */
    public static void addPattern(String nameOfPattern, String regexAsString) {
        try {
            String nameToAdd = (nameOfPattern != null && !nameOfPattern.isEmpty()) ? nameOfPattern :
                    "" + DATA_MASKING_TYPES.size();
            DataMaskingType dataMaskingTypeToAdd = new DataMaskingType(nameToAdd, regexAsString);
            DATA_MASKING_TYPES.add(dataMaskingTypeToAdd);
            LOG.info("Added masking pattern \"{}\" with regex: \"{}\"", dataMaskingTypeToAdd.getNameOfPattern(),
                    dataMaskingTypeToAdd.getRegex());
        } catch (Exception e) {
            LOG.error("Could not add pattern " + regexAsString, e);
        }
    }

    /**
     * Removes all patterns
     */
    public static void removePatterns() {
        DATA_MASKING_TYPES.clear();
    }

}
