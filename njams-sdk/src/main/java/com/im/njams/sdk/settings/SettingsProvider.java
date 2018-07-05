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
package com.im.njams.sdk.settings;

import java.util.Properties;

/**
 * This interface must be implemented for creating new SettingsProvider
 *
 * @author pnientiedt
 */
public interface SettingsProvider {

    /**
     * Should return the name by which the new SettingsProvider could be identified.
     *
     * @return the name of the {@link SettingsProvider}
     */
    public String getName();

    /**
     * Configure the new SettingsProvider via given Properties
     *
     * @param properties
     *            to configure {@link SettingsProvider}
     */
    public void configure(Properties properties);

    /**
     * This function should load the Settings from the underlying storage of the new SettingsProvider. If the
     * storage does not contain a Settings, it should return a new empty Settings
     *
     * @return the Settings
     */
    public Settings loadSettings();

    /**
     * This function should save the Settings to the underlying storage.
     *
     * @param settings
     *            to save
     */
    public void saveSettings(Settings settings);
}
