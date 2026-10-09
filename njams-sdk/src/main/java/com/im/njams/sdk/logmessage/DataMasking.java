/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
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
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.logmessage;

import java.util.List;

import com.im.njams.sdk.DataMasker;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * JVM-wide data masking, shared by all client instances. Registering a pattern whose regex is already registered has
 * no effect. All methods are thread-safe.
 *
 * @author pnientiedt
 * @deprecated Masking is scoped to the {@link com.im.njams.sdk.Njams} instance since 6.1.0: use the masker returned
 *             by {@code njams.configuration().dataMasking()} ({@link DataMasker}). Patterns registered here still
 *             apply to every instance in the JVM until this class is removed. The SDK no longer registers the
 *             patterns from settings or configuration here, so {@link #maskString(String)} and
 *             {@link #getPatterns()} only cover patterns registered through this class.
 */
@Deprecated(since = "6.1.0", forRemoval = true)
public class DataMasking {

    private static final DataMasker JVM_WIDE = new DataMasker();

    /**
     * Mask a string by the JVM-wide patterns given to this class
     *
     * @param inString String to apply datamasking to
     * @return String with applied datamasking
     * @deprecated See {@link DataMasking}; use {@link DataMasker#maskString(String)}.
     */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static String maskString(final String inString) {
        return JVM_WIDE.maskString(inString);
    }

    /**
     * This method adds all patterns to the pattern list
     *
     * @param patterns the patterns to add
     * @deprecated See {@link DataMasking}; use {@link DataMasker#addPatterns(List)}.
     */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void addPatterns(List<String> patterns) {
        JVM_WIDE.addPatterns(patterns);
    }

    /**
     * This method adds a pattern to the pattern list.
     *
     * @param pattern the pattern to add
     * @deprecated See {@link DataMasking}; use {@link DataMasker#addPattern(String)}.
     */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void addPattern(String pattern) {
        JVM_WIDE.addPattern(pattern);
    }

    /**
     * Reads all properties whose key starts with
     * {@value com.im.njams.sdk.NjamsSettings#PROPERTY_DATA_MASKING_REGEX_PREFIX}
     * from the given settings and adds them to the data masking list.
     *
     * @param settings the settings to read masking patterns from
     * @deprecated See {@link DataMasking}; use {@link DataMasker#addPatterns(ClientSettings)}.
     */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void addPatterns(ClientSettings settings) {
        JVM_WIDE.addPatterns(settings);
    }

    /**
     * Returns an immutable snapshot of the currently registered JVM-wide data masking patterns.
     *
     * @return the list of registered masking patterns
     * @deprecated See {@link DataMasking}; use {@link DataMasker#getPatterns()}.
     */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static List<DataMaskingType> getPatterns() {
        return JVM_WIDE.getPatterns();
    }

    /**
     * Add a new pattern for masking data. If you don't provide a name for the pattern, it will be the index of
     * pattern in the list of masking patterns.
     * <p>
     * A pattern is ignored if a pattern with the same regex is already registered (regardless of its name), since
     * applying it again would not change the masking result.
     *
     * @param nameOfPattern the name of the pattern
     * @param regexAsString the pattern to add
     * @deprecated See {@link DataMasking}; use {@link DataMasker#addPattern(String, String)}.
     */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void addPattern(String nameOfPattern, String regexAsString) {
        JVM_WIDE.addPattern(nameOfPattern, regexAsString);
    }

    /**
     * Removes all JVM-wide patterns
     *
     * @deprecated See {@link DataMasking}; use {@link DataMasker#removePatterns()}.
     */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void removePatterns() {
        JVM_WIDE.removePatterns();
    }

}
