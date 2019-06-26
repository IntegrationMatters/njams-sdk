/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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

import com.im.njams.sdk.settings.proxy.PropertiesFileSettingsProxy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PropertiesFileSettingsProviderTest {

    private final File tmpdir = new File(System.getProperty("java.io.tmpdir", ""));
    private File testFile = null;
    private File parentFile = null;
    private Properties properties = null;
    private PropertiesFileSettingsProxy provider = null;

    @Before
    public void setup() throws IOException {
        clear();
        String parentName = "njams-parent.properties";
        parentFile = getTmpFile(parentName);
        Properties props = new Properties();
        props.setProperty("a", "a");
        props.setProperty("b", "b");
        props.setProperty("parentKey", "parentValue");
        saveProps(parentFile, props);

        testFile = getTmpFile("njams-test-config.properties");
        props = new Properties();
        props.setProperty(PropertiesFileSettingsProxy.PARENT_CONFIGURATION, parentName);
        props.setProperty("a", "1");
        props.setProperty("b", "2");
        saveProps(testFile, props);

        Properties providerProps = new Properties();
        providerProps.setProperty(PropertiesFileSettingsProxy.FILE_CONFIGURATION, testFile.getAbsolutePath());
        provider = new PropertiesFileSettingsProxy();
        provider.configure(providerProps);
        properties = provider.loadSettings().getProperties();
    }

    private File getTmpFile(String name) {
        return new File(tmpdir, name);
    }

    private void saveProps(File outFile, Properties props) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            props.store(fos, null);
        }
    }

    @After
    public void teardown() {
        clear();
    }

    private void clear() {
        if (testFile != null) {
            testFile.delete();
            testFile = null;
        }
        if (parentFile != null) {
            parentFile.delete();
            parentFile = null;
        }

        File defaultFile = new File(tmpdir, PropertiesFileSettingsProxy.DEFAULT_FILE);
        if (defaultFile.exists()) {
            defaultFile.delete();
        }
    }

    @Test
    public void testProperties() throws IOException {
        assertFalse(properties.isEmpty());
        assertEquals("1", properties.getProperty("a"));
        assertEquals("2", properties.getProperty("b"));
        assertEquals("parentValue", properties.getProperty("parentKey"));
    }

    @Test
    public void testParent() {
        assertEquals("1", properties.getProperty("a"));
        assertEquals("2", properties.getProperty("b"));
        assertEquals("parentValue", properties.getProperty("parentKey"));
        properties.remove("a");
        //This can't be done anymore, instead the property with key 'a' is deleted.
        //assertEquals("a", properties.getProperty("a"));
        Assert.assertNull(properties.getProperty("a"));
        assertEquals("2", properties.getProperty("b"));
        assertEquals("parentValue", properties.getProperty("parentKey"));
    }

    @Test
    public void testPath() throws IOException {
        File defaultFile = new File(tmpdir, PropertiesFileSettingsProxy.DEFAULT_FILE);
        defaultFile.createNewFile();

        Properties providerProps = new Properties();
        providerProps.setProperty(PropertiesFileSettingsProxy.FILE_CONFIGURATION, tmpdir.getAbsolutePath());
        provider = new PropertiesFileSettingsProxy();
        provider.configure(providerProps);
        assertEquals(new File(tmpdir, PropertiesFileSettingsProxy.DEFAULT_FILE), provider.getFile());
    }

    @Test
    public void testFile() {
        Properties providerProps = new Properties();
        providerProps.setProperty(PropertiesFileSettingsProxy.FILE_CONFIGURATION, testFile.getAbsolutePath());
        provider = new PropertiesFileSettingsProxy();
        provider.configure(providerProps);
        assertEquals(testFile, provider.getFile());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFileNotExists() {
        Properties providerProps = new Properties();
        providerProps.setProperty(PropertiesFileSettingsProxy.FILE_CONFIGURATION, "/testxy.properties");
        provider = new PropertiesFileSettingsProxy();
        provider.configure(providerProps);
    }

    @Test
    public void testModifiedParentKey() throws IOException {
        String parentKey = "parentFileKey";
        String parent = (String) properties.remove(PropertiesFileSettingsProxy.PARENT_CONFIGURATION);
        properties.setProperty(parentKey, parent);
        saveProps(testFile, properties);

        Properties providerProps = new Properties();
        providerProps.setProperty(PropertiesFileSettingsProxy.FILE_CONFIGURATION, testFile.getAbsolutePath());
        providerProps.setProperty(PropertiesFileSettingsProxy.PARENT_CONFIGURATION_KEY, parentKey);
        provider = new PropertiesFileSettingsProxy();
        provider.configure(providerProps);
        
        assertEquals("1", properties.getProperty("a"));
        assertEquals("2", properties.getProperty("b"));
        assertEquals("parentValue", properties.getProperty("parentKey"));

    }

    /**
     * This test checks if it is possible to create a circle between properties.
     * It shouldn't be possible, so if it throws an StackOverflowError, a circle
     * can be created!
     *
     * @throws IOException
     */
    /**
    @Test
    public void testCircleShouldNotBePossible() throws IOException {
        String circle1 = "circle1";
        String circle2 = "circle2";
        File circle2File = getTmpFile(circle2);
        File circle1File = getTmpFile(circle1);
        try {          
            Properties props = new Properties();
            props.setProperty(PropertiesFileSettingsProvider.PARENT_CONFIGURATION, circle1);
            saveProps(circle2File, props);

            props = new Properties();
            props.setProperty(PropertiesFileSettingsProvider.PARENT_CONFIGURATION, circle2);
            saveProps(circle1File, props);

            Properties providerProps = new Properties();
            providerProps.setProperty(PropertiesFileSettingsProvider.FILE_CONFIGURATION, circle1File.getAbsolutePath());
            proxy = new PropertiesFileSettingsProvider();
            proxy.configure(providerProps);
            proxy.loadSettings();
        } catch (StackOverflowError e) {
            Assert.fail("Circular dependencies possible!");
        } finally {
            circle1File.delete();
            circle2File.delete();
        }
    }
    */

}
