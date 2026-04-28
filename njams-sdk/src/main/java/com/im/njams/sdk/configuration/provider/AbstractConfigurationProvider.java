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

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.configuration.ConfigurationProvider;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * A base implementation for {@link ConfigurationProvider} that manages common default that are provided via
 * {@link Settings}.
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

    @Override
    public void configure(Properties properties, Njams njams) {
        this.njams = njams;
        if (properties.containsKey(DEFAULT_RECORDING_CONFIG)) {
            initRecording(properties.getProperty(DEFAULT_RECORDING_CONFIG));
        }
        if (properties.containsKey(DEFAULT_LOG_MODE_CONFIG)) {
            initLogMode(properties.getProperty(DEFAULT_LOG_MODE_CONFIG));
        }
        if (properties.containsKey(DEFAULT_LOG_LEVEL_CONFIG)) {
            initLogLevel(properties.getProperty(DEFAULT_LOG_LEVEL_CONFIG));
        }
        LOG.debug("Initialized: defaultRecording{}, defailtLogMode={}, defaultLogLevel={}", defaultRecording,
            defaultLogMode, defaultLogLevel);
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
