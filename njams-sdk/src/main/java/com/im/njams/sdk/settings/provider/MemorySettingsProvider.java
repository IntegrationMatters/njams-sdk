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

import com.im.njams.sdk.settings.Settings;
import java.util.Properties;
import com.im.njams.sdk.settings.SettingsProvider;

/**
 * Implements a simple MemorySettingsProvider. It just holds the
 * settings in memory and returns it if load is called.
 *
 * @author pnientiedt
 */
public class MemorySettingsProvider implements SettingsProvider {

    /**
     * Name of the MemorySettingsProvider
     */
    public static final String NAME = "memory";
    private Settings settings;

    /**
     * Returns the value {@value #NAME} as name for this SettingsProvider.
     *
     * @return the name of this SettingsProvider
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * This does nothing because there is nothing to configure on this simple
     * SettingsProvider implementation.
     *
     * @param properties not needed here
     */
    @Override
    public void configure(Properties properties) {
        // nothing to do here
    }

    /**
     * Returns the Settings. If no settings exists yet, it creates a
     * new one.
     *
     * @return the {@link Settings} or an empty one
     */
    @Override
    public Settings loadSettings() {
        if (settings == null) {
            settings = new Settings();
        }
        return settings;
    }

    /**
     * Stores the given Settings in memory.
     *
     * @param settings to be saved
     */
    @Override
    public void saveSettings(Settings settings) {
        this.settings = settings;
    }

}
