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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * DataMasking implementation
 *
 * @author pnientiedt
 */
public class DataMasking {

    private static final Logger LOG = LoggerFactory.getLogger(DataMasking.class);

    private static final List<DataMaskingType> DATA_MASKING_TYPES = new ArrayList<>();
    private static final char MASK_CHAR = '*';
    private static char[] mask = new char[0];

    /**
     * Mask a string by patterns given to this class
     *
     * @param inString String to apply datamasking to
     * @return String with applied datamasking
     */
    public static String maskString(final String inString) {
        if (DATA_MASKING_TYPES.isEmpty() || StringUtils.isBlank(inString)) {
            return inString;
        }

        final StringBuilder maskedString = new StringBuilder(inString);
        for (DataMaskingType dataMaskingType : DATA_MASKING_TYPES) {
            final Matcher m = dataMaskingType.getPattern().matcher(inString);
            while (m.find()) {
                maskedString.replace(m.start(), m.end(), getMask(m.end() - m.start()));
            }
            LOG.trace("\nApplied {}, new result={}", dataMaskingType, maskedString);
        }
        LOG.debug("Masked string: {}", maskedString);

        return maskedString.toString();
    }

    /**
     * Efficient way for getting the a string containing only the masking character.
     * @param len The length of the string to return, respectively the number of masking characters.
     * @return A string of the given length containing only the masking character.
     */
    private static String getMask(int len) {
        if (mask.length < len) {
            synchronized (DataMasking.class) {
                if (mask.length < len) {
                    // make sure that concurrent executions only see the filled array
                    final char[] newMask = new char[len];
                    Arrays.fill(newMask, MASK_CHAR);
                    mask = newMask;
                }
            }
        }
        return String.valueOf(mask, 0, len);
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
     * This method reads all key-value pairs where the key starts with
     * {@link com.im.njams.sdk.NjamsSettings#PROPERTY_DATA_MASKING_REGEX_PREFIX} and
     * adds them to a data masking list.
     *
     * @param properties the properties to provide
     */
    public static void addPatterns(Properties properties) {
        properties.stringPropertyNames().stream()
                .filter(k -> k.startsWith(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX))
                .forEach(k -> addPattern(k.substring(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX.length()),
                        properties.getProperty(k)));
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
            String nameToAdd =
                    nameOfPattern != null && !nameOfPattern.isEmpty() ? nameOfPattern : "" + DATA_MASKING_TYPES.size();
            DataMaskingType dataMaskingTypeToAdd = new DataMaskingType(nameToAdd, regexAsString);
            DATA_MASKING_TYPES.add(dataMaskingTypeToAdd);
            LOG.info("Added masking pattern \"{}\" with regex: \"{}\"", dataMaskingTypeToAdd.getNameOfPattern(),
                    dataMaskingTypeToAdd.getRegex());
        } catch (Exception e) {
            LOG.error("Could not add pattern {}", regexAsString, e);
        }
    }

    /**
     * Removes all patterns
     */
    public static void removePatterns() {
        DATA_MASKING_TYPES.clear();
    }

}
