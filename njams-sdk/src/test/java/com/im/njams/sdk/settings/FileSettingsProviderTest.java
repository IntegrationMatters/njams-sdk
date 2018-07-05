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

import com.im.njams.sdk.settings.provider.FileSettingsProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import org.junit.Test;

/**
 *
 * @author pnientiedt
 */
public class FileSettingsProviderTest {

    @Test
    public void testProvider() throws IOException {
        File file = new File("config.json");
        file.delete();
        assertThat(file.exists(), is(false));

        Properties properties = new Properties();
        properties.put(SettingsProviderFactory.SETTINGS_PROVIDER, FileSettingsProvider.NAME);
        SettingsProvider provider = SettingsProviderFactory.getSettingsProvider(properties);

        Settings conf = provider.loadSettings();
        assertThat(conf, notNullValue());
        assertThat(file.exists(), is(false));

        conf.getProperties().put("key1", "value1");
        provider.saveSettings(conf);
        assertThat(file.exists(), is(true));

        List<String> lines = Arrays.asList("{", "\"properties\" : {", "\"test-key\" : \"test-value\"", "}", "}");
        Files.write(Paths.get(file.toString()), lines, Charset.forName("UTF-8"));
        conf = provider.loadSettings();
        assertThat(conf, notNullValue());
        assertThat(conf.getProperties(), notNullValue());
        assertThat(conf.getProperties().size(), is(1));
        assertThat(conf.getProperties().getProperty("test-key"), is("test-value"));

        file.delete();
        assertThat(file.exists(), is(false));
    }

}
