/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.logmessage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DataMasking implementation
 *
 * @author pnientiedt
 */
public class DataMasking {

    private static final Logger LOG = LoggerFactory.getLogger(DataMasking.class);

    private final static String MASK_CHAR = "*";
    private final static List<Pattern> PATTERNS = new ArrayList<>();

    /**
     * Mask a string by patterns given to this class
     *
     * @param inString String to apply datamasking to
     * @return String with applied datamasking
     */
    public static String maskString(String inString) {
        if (inString == null || inString.isEmpty() || PATTERNS.isEmpty()) {
            return inString;
        }
        int inVisibleCharacters = 0;
        if (inVisibleCharacters < 0) {
            inVisibleCharacters = 0;
        }

        String stringToMask = inString;
        String maskedString = inString;

        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(stringToMask);
            while (m.find()) {
                int startIdx = m.start();
                int endIdx = m.end();

                String patternMatch = stringToMask.substring(startIdx, endIdx);

                String partToBeMasked = patternMatch.substring(0, patternMatch.length() - inVisibleCharacters);
                String mask = "";
                for (int i = 0; i < partToBeMasked.length(); i++) {
                    mask = mask + MASK_CHAR;
                }

                String maskedNumber = mask + patternMatch.substring(patternMatch.length() - inVisibleCharacters);

                maskedString = maskedString.replace(patternMatch, maskedNumber);
            }
        }
        return maskedString;
    }

    /**
     * Add new patterns for datamasking
     *
     * @param patterns the patterns to add
     */
    public static void addPatterns(List<String> patterns) {
        patterns.forEach(DataMasking::addPattern);
    }

    /**
     * Add a new pattern for datamasking
     *
     * @param pattern the pattern to add
     */
    public static void addPattern(String pattern) {
        try {
            Pattern p = Pattern.compile(pattern);
            PATTERNS.add(p);
            LOG.info("added masking pattern " + pattern);
        } catch (Exception e) {
            LOG.error("could not add pattern " + pattern, e);
        }
    }
}
