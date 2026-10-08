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
package com.im.njams.sdk.configuration.provider;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.configuration.ConfigurationProvider;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A base implementation for {@link ConfigurationProvider} that manages common default that are provided via
 * {@link ClientSettings}.
 */
public abstract class AbstractConfigurationProvider implements ConfigurationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractConfigurationProvider.class);
    private static final String CONFIG_PREFIX = "$$" + AbstractConfigurationProvider.class.getSimpleName() + ".";
    /** Key used for passing default setting for <code>recording</code> into this provider. */
    public static final String DEFAULT_RECORDING_CONFIG = CONFIG_PREFIX + "recording.default.";
    /** Key used for passing default setting for <code>logMode</code> into this provider. */
    public static final String DEFAULT_LOG_MODE_CONFIG = CONFIG_PREFIX + "logMode.default";
    /** Key used for passing default setting for <code>logLevel</code> into this provider. */
    public static final String DEFAULT_LOG_LEVEL_CONFIG = CONFIG_PREFIX + "logLevel.default";

    private Njams njams = null;
    private boolean defaultRecording = true;
    private LogMode defaultLogMode = LogMode.COMPLETE;
    private LogLevel defaultLogLevel = LogLevel.INFO;
    private Collection<Pattern> processExcludePatterns = Collections.emptyList();

    @Override
    public void configure(Properties properties, Njams njams) {
        this.njams = njams;
        processExcludePatterns = compileProcessExcludePatterns(getSettings());
        if (properties.containsKey(DEFAULT_RECORDING_CONFIG)) {
            initRecording(properties.getProperty(DEFAULT_RECORDING_CONFIG));
        }
        if (properties.containsKey(DEFAULT_LOG_MODE_CONFIG)) {
            initLogMode(properties.getProperty(DEFAULT_LOG_MODE_CONFIG));
        }
        if (properties.containsKey(DEFAULT_LOG_LEVEL_CONFIG)) {
            initLogLevel(properties.getProperty(DEFAULT_LOG_LEVEL_CONFIG));
        }
        LOG.debug("Initialized: defaultRecording{}, defaultLogMode={}, defaultLogLevel={}", defaultRecording,
            defaultLogMode, defaultLogLevel);
    }

    private static Collection<Pattern> compileProcessExcludePatterns(ClientSettings settings) {
        if (settings == null) {
            return Collections.emptyList();
        }
        final List<Pattern> patterns = new ArrayList<>();
        for (final Entry<String, String> entry : settings) {
            if (entry.getKey().startsWith(NjamsSettings.PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX)
                && StringUtils.isNotBlank(entry.getValue())) {
                try {
                    patterns.add(Pattern.compile(entry.getValue().trim()));
                } catch (PatternSyntaxException e) {
                    LOG.warn("Ignoring illegal process match pattern {}: {}", entry.getValue(), e.getMessage());
                }
            }
        }
        return Collections.unmodifiableList(patterns);
    }

    private void initRecording(String val) {
        if (StringUtils.isBlank(val)) {
            return;
        }
        defaultRecording = !"false".equalsIgnoreCase(val);
    }

    private void initLogMode(String val) {
        if (StringUtils.isBlank(val)) {
            return;
        }
        for (LogMode l : LogMode.values()) {
            if (l.name().equalsIgnoreCase(val)) {
                defaultLogMode = l;
                return;
            }
        }
        LOG.warn("Could not initialize default log-mode. Unsupported value: {}", val);
    }

    private void initLogLevel(String val) {
        if (StringUtils.isBlank(val)) {
            return;
        }
        for (LogLevel l : LogLevel.values()) {
            if (l.name().equalsIgnoreCase(val)) {
                defaultLogLevel = l;
                return;
            }
        }
        LOG.warn("Could not initialize default log-level. Unsupported value: {}", val);

    }

    @Override
    public ProcessConfiguration newProcesConfiguration() {
        final ProcessConfiguration c = new ProcessConfiguration();
        c.setRecording(defaultRecording);
        c.setLogLevel(defaultLogLevel);
        return c;
    }

    protected Njams getNjams() {
        return njams;
    }

    /**
     * Returns the client settings of the associated {@link Njams} instance, or <code>null</code> if
     * this provider was not configured with one.
     * @return the client settings, or <code>null</code>.
     */
    protected ClientSettings getSettings() {
        return njams == null ? null : njams.getSettings();
    }

    /**
     * Returns the process-exclude patterns compiled from the client settings once, when this provider was
     * {@link #configure(Properties, Njams) configured}.
     * @return the compiled process-exclude patterns, never <code>null</code>.
     */
    @Override
    public Collection<Pattern> getProcessExcludePatterns() {
        return processExcludePatterns;
    }

    protected boolean getDefaultRecording() {
        return defaultRecording;
    }

    protected LogMode getDefaultLogMode() {
        return defaultLogMode;
    }

    protected LogLevel getDefaultLogLevel() {
        return defaultLogLevel;
    }

}
