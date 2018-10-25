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
package com.im.njams.sdk.settings.provider;

import java.util.Properties;

import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.settings.SettingsProvider;
import com.im.njams.sdk.settings.SettingsProviderFactory;

/**
 * Implements a simple {@link SettingsProvider} that uses Java system properties for configuration.
 *
 * @author cwinkler
 */
public class SystemPropertiesSettingsProvider implements SettingsProvider {

    /** Name of this implementation that has to be used to configure {@link SettingsProviderFactory}. */
    public static final String NAME = "systemProperties";

    /**
     * Returns the value {@value #NAME} as name for this {@link SettingsProvider}
     *
     * @return the name of this implementation
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Does nothing for this implementation.
     *
     * @param properties
     */
    @Override
    public void configure(Properties properties) {
        // nothing to do
    }

    /**
     * Returns the {@link Settings} initialized with a copy of the current system properties set.
     *
     * @return the {@link Settings}
     */
    @Override
    public Settings loadSettings() {
        Properties sysPropsCopy = new Properties();
        sysPropsCopy.putAll(System.getProperties());
        Settings settings = new Settings();
        settings.setProperties(sysPropsCopy);
        return settings;
    }

    /**
     * Not supported by this implementation.
     *
     * @param settings to be saved
     * @throws UnsupportedOperationException always.
     */
    @Override
    public void saveSettings(Settings settings) {
        throw new UnsupportedOperationException("Saving to system properties is not supported.");
    }

}
