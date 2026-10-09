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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.logmessage.DataMaskingType;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Masks data with a set of regex patterns: every substring matching a pattern is replaced with asterisks.
 * <p>
 * Each {@link Njams} instance has its own masker, obtained via {@code njams.configuration().dataMasking()}. It masks
 * with the patterns from the instance's settings and configuration, which the SDK applies on every start, and with
 * the patterns client code adds through the {@code add*} methods, which stay for the instance's lifetime. Patterns
 * of one instance never affect another instance.
 * <p>
 * Registering a pattern whose regex is already registered has no effect. All methods are thread-safe; masking does
 * not lock.
 */
public class DataMasker {

    private static final Logger LOG = LoggerFactory.getLogger(DataMasker.class);

    private static final char MASK_CHAR = '*';
    private static volatile char[] mask = new char[0];

    private final boolean applyJvmWidePatterns;
    // guarded by this
    private final List<DataMaskingType> configuredPatterns = new ArrayList<>();
    // guarded by this
    private final List<DataMaskingType> clientPatterns = new ArrayList<>();
    // configured followed by client patterns; replaced as a whole on every change so masking reads lock-free
    private volatile List<DataMaskingType> patterns = Collections.emptyList();

    /**
     * Creates a masker without any patterns.
     */
    public DataMasker() {
        this(false);
    }

    /**
     * @param applyJvmWidePatterns whether {@link #maskString(String)} also applies the patterns registered through
     *        the deprecated static {@link DataMasking} API
     */
    DataMasker(boolean applyJvmWidePatterns) {
        this.applyJvmWidePatterns = applyJvmWidePatterns;
    }

    /**
     * Masks the given string with the registered patterns.
     *
     * @param inString the string to mask
     * @return the masked string; <code>null</code> or blank input is returned unchanged
     */
    @SuppressWarnings("removal")
    public String maskString(final String inString) {
        final String masked = applyPatterns(inString);
        return applyJvmWidePatterns ? DataMasking.maskString(masked) : masked;
    }

    private String applyPatterns(final String inString) {
        final List<DataMaskingType> current = patterns;
        if (current.isEmpty() || StringUtils.isBlank(inString)) {
            return inString;
        }
        final StringBuilder maskedString = new StringBuilder(inString);
        for (DataMaskingType dataMaskingType : current) {
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
     * Efficient way for getting a string containing only the masking character.
     */
    private static String getMask(int len) {
        char[] current = mask;
        if (current.length < len) {
            synchronized (DataMasker.class) {
                current = mask;
                if (current.length < len) {
                    // extend by multiples of 100 chars
                    final char[] newMask = new char[(len / 100 + 1) * 100];
                    Arrays.fill(newMask, MASK_CHAR);
                    // the field is volatile, so that concurrent executions only see the completely filled array
                    mask = newMask;
                    current = newMask;
                }
            }
        }
        return String.valueOf(current, 0, len);
    }

    /**
     * Adds the given patterns.
     *
     * @param regexes the regexes to add
     */
    public void addPatterns(List<String> regexes) {
        regexes.forEach(this::addPattern);
    }

    /**
     * Adds a pattern, named by its index in {@link #getPatterns()}.
     *
     * @param regex the regex to add
     */
    public void addPattern(String regex) {
        addPattern(null, regex);
    }

    /**
     * Adds all patterns from the given settings whose key starts with
     * {@value com.im.njams.sdk.NjamsSettings#PROPERTY_DATA_MASKING_REGEX_PREFIX}; the rest of the key is the
     * pattern's name.
     *
     * @param settings the settings to read patterns from
     */
    public void addPatterns(ClientSettings settings) {
        forEachSettingsRegex(settings, this::addPattern);
    }

    /**
     * Adds a pattern. A pattern is ignored if its regex is blank, invalid, or already registered (regardless of its
     * name).
     *
     * @param nameOfPattern the name of the pattern; if <code>null</code> or empty, its index in
     *        {@link #getPatterns()} is used
     * @param regex the regex to add
     */
    public void addPattern(String nameOfPattern, String regex) {
        if (StringUtils.isBlank(regex)) {
            LOG.debug("Skipping empty regex for pattern \"{}\"", nameOfPattern);
            return;
        }
        synchronized (this) {
            if (containsRegex(patterns, regex)) {
                LOG.debug("Skipping masking pattern \"{}\": regex \"{}\" is already registered", nameOfPattern, regex);
                return;
            }
            final DataMaskingType added = newPattern(nameOfPattern, regex, patterns.size());
            if (added != null) {
                clientPatterns.add(added);
                rebuild();
            }
        }
    }

    /**
     * Returns an immutable snapshot of the registered patterns.
     *
     * @return the registered patterns
     */
    public List<DataMaskingType> getPatterns() {
        return patterns;
    }

    /**
     * Removes all patterns added through the {@code add*} methods. Patterns the SDK applies from the instance's
     * settings and configuration stay.
     */
    public synchronized void removePatterns() {
        clientPatterns.clear();
        rebuild();
    }

    /**
     * Replaces the patterns taken from the instance's settings and configuration; client-added patterns stay.
     */
    synchronized void replaceConfiguredPatterns(ClientSettings settings, List<String> configurationRegexes) {
        configuredPatterns.clear();
        forEachSettingsRegex(settings, this::addConfigured);
        configurationRegexes.forEach(regex -> addConfigured(null, regex));
        rebuild();
    }

    /**
     * Removes the patterns taken from the instance's settings and configuration; client-added patterns stay.
     */
    synchronized void clearConfiguredPatterns() {
        configuredPatterns.clear();
        rebuild();
    }

    // caller holds the lock
    private void addConfigured(String nameOfPattern, String regex) {
        if (StringUtils.isBlank(regex) || containsRegex(configuredPatterns, regex)) {
            return;
        }
        final DataMaskingType added = newPattern(nameOfPattern, regex, configuredPatterns.size());
        if (added != null) {
            configuredPatterns.add(added);
        }
    }

    // caller holds the lock
    private void rebuild() {
        final List<DataMaskingType> all = new ArrayList<>(configuredPatterns);
        for (DataMaskingType clientPattern : clientPatterns) {
            if (!containsRegex(all, clientPattern.getRegex())) {
                all.add(clientPattern);
            }
        }
        patterns = Collections.unmodifiableList(all);
    }

    private static DataMaskingType newPattern(String nameOfPattern, String regex, int index) {
        try {
            final String name = nameOfPattern != null && !nameOfPattern.isEmpty() ? nameOfPattern : "" + index;
            final DataMaskingType pattern = new DataMaskingType(name, regex);
            LOG.info("Added masking pattern \"{}\" with regex: \"{}\"", pattern.getNameOfPattern(),
                pattern.getRegex());
            return pattern;
        } catch (Exception e) {
            LOG.error("Could not add pattern {}", regex, e);
            return null;
        }
    }

    private static boolean containsRegex(List<DataMaskingType> types, String regex) {
        for (DataMaskingType type : types) {
            if (type.getRegex().equals(regex)) {
                return true;
            }
        }
        return false;
    }

    private static void forEachSettingsRegex(ClientSettings settings, BiConsumer<String, String> consumer) {
        for (Map.Entry<String, String> entry : settings) {
            if (entry.getKey().startsWith(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX)) {
                consumer.accept(entry.getKey().substring(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX.length()),
                    entry.getValue());
            }
        }
    }
}
