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

import java.util.List;
import java.util.Properties;

import com.im.njams.sdk.njams.NjamsDataMasking;

/**
 * DataMasking implementation
 *
 * @author pnientiedt
 */
@Deprecated
public class DataMasking {

    /**
     * Property njams.sdk.datamasking.enabled
     */
    @Deprecated
    public static final String DATA_MASKING_ENABLED = NjamsDataMasking.DATA_MASKING_ENABLED;

    /**
     * Property njams.sdk.datamasking.regex.
     */
    @Deprecated
    public static final String DATA_MASKING_REGEX_PREFIX = NjamsDataMasking.DATA_MASKING_REGEX_PREFIX;

    private static NjamsDataMasking globalDataMaskingInstance;

    public static void setNjamsDataMaskingIfAbsentOrMerge(NjamsDataMasking globalDataMaskingInstance) {
        if(DataMasking.globalDataMaskingInstance == null){
            DataMasking.globalDataMaskingInstance = globalDataMaskingInstance;
        }else if(globalDataMaskingInstance != null){
            DataMasking.globalDataMaskingInstance.mergeWith(globalDataMaskingInstance);
        }
    }

    public static NjamsDataMasking getGlobalNjamsDataMasking(){
        return globalDataMaskingInstance;
    }

    /**
     * Mask a string by patterns given to this class
     *
     * @param inString String to apply datamasking to
     * @return String with applied datamasking
     */
    @Deprecated
    public static String maskString(String inString) {
        if(DataMasking.globalDataMaskingInstance != null)
            return globalDataMaskingInstance.mask(inString);
        else
            return inString;
    }

    /**
     * This method adds all patterns to the pattern list
     *
     * @param patterns the patterns to add
     */
    @Deprecated
    public static void addPatterns(List<String> patterns) {
        patterns.forEach(DataMasking::addPattern);
    }

    /**
     * This method adds a pattern to the pattern list.
     *
     * @param pattern the pattern to add
     */
    @Deprecated
    public static void addPattern(String pattern) {
        addPattern(null, pattern);
    }

    /**
     * This method takes a reads all key-value pairs where the key starts with {@link #DATA_MASKING_REGEX_PREFIX} and
     * adds them to a data masking list.
     *
     * @param properties the properties to provide
     */
    @Deprecated
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
    @Deprecated
    public static void addPattern(String nameOfPattern, String regexAsString) {
        if(DataMasking.globalDataMaskingInstance != null)
            globalDataMaskingInstance.add(nameOfPattern, regexAsString);
    }

    /**
     * Removes all patterns
     */
    @Deprecated
    public static void removePatterns() {
        if(DataMasking.globalDataMaskingInstance != null){
            globalDataMaskingInstance.clear();
        }
    }
}
