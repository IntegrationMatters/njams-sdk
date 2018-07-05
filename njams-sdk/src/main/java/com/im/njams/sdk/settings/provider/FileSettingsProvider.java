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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.Settings;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import com.im.njams.sdk.settings.SettingsProvider;

/**
 * Implements a simple FileSettingsProvider. It loads and saves a
 * Settings to a specified file.
 *
 * @author pnientiedt
 */
public class FileSettingsProvider implements SettingsProvider {

    /**
     * Property key for settings properties. Specifies the path to the
     * settings file.
     */
    public static final String FILE_CONFIGURATION = "njams.sdk.settings.file";
    /**
     * Name of the FileSettingsProvider
     */
    public static final String NAME = "file";

    private final ObjectMapper objectMapper;
    private final ObjectWriter objectWriter;
    private File file;

    /**
     * Create new instance
     */
    public FileSettingsProvider() {
        file = new File("config.json");
        this.objectMapper = JsonSerializerFactory.getDefaultMapper();
        this.objectWriter = this.objectMapper.writer();
    }

    /**
     * Configures this FileSettingsProvider via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value #FILE_CONFIGURATION}
     * </ul>
     *
     * @param properties to configure
     */
    @Override
    public void configure(Properties properties) {
        if (properties.containsKey(FILE_CONFIGURATION)) {
            file = new File(properties.getProperty(FILE_CONFIGURATION));
        }
    }

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
     * Loads the Settings from the configured file, or returns a new empty
     * Settings if the file does not exist.
     *
     * @return the{@link Settings} or {@link NjamsSdkRuntimeException} if
     * file is not loadable
     */
    @Override
    public Settings loadSettings() {
        Settings settings;
        if (!file.exists()) {
            settings = new Settings();
        } else {
            try {
                settings = objectMapper.readValue(new FileInputStream(file), Settings.class);

            } catch (Exception e) {
                throw new NjamsSdkRuntimeException("Unable to load file", e);
            }
        }
        return settings;
    }

    /**
     * Save the given Settings to the configured File.
     *
     * @param settings to be saved
     */
    @Override
    public void saveSettings(Settings settings) {
        try {
            objectWriter.writeValue(file, settings);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to save file", e);
        }
    }

    /**
     * Get the File where Settings is stored.
     *
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * Set the File from where Settings should be loaded.
     *
     * @param file the file to load from
     */
    public void setFile(File file) {
        this.file = file;
    }

}
