package com.im.njams.sdk.settings;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.provider.PropertiesFileSettingsProvider;
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
    private PropertiesFileSettingsProvider provider = null;

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
        props.setProperty(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_FILE, parentName);
        props.setProperty("a", "1");
        props.setProperty("b", "2");
        saveProps(testFile, props);

        Properties providerProps = new Properties();
        providerProps.setProperty(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_FILE, testFile.getAbsolutePath());
        provider = new PropertiesFileSettingsProvider();
        provider.configure(providerProps);
        properties = provider.loadSettings().getAllProperties();
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

        File defaultFile = new File(tmpdir, PropertiesFileSettingsProvider.DEFAULT_FILE);
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
        File defaultFile = new File(tmpdir, PropertiesFileSettingsProvider.DEFAULT_FILE);
        defaultFile.createNewFile();

        Properties providerProps = new Properties();
        providerProps.setProperty(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_FILE, tmpdir.getAbsolutePath());
        provider = new PropertiesFileSettingsProvider();
        provider.configure(providerProps);
        assertEquals(new File(tmpdir, PropertiesFileSettingsProvider.DEFAULT_FILE), provider.getFile());
    }

    @Test
    public void testFile() {
        Properties providerProps = new Properties();
        providerProps.setProperty(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_FILE, testFile.getAbsolutePath());
        provider = new PropertiesFileSettingsProvider();
        provider.configure(providerProps);
        assertEquals(testFile, provider.getFile());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFileNotExists() {
        Properties providerProps = new Properties();
        providerProps.setProperty(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_FILE, "/testxy.properties");
        provider = new PropertiesFileSettingsProvider();
        provider.configure(providerProps);
    }

    @Test
    public void testModifiedParentKey() throws IOException {
        String parentKey = "parentFileKey";
        String parent = (String) properties.remove(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_FILE);
        properties.setProperty(parentKey, parent);
        saveProps(testFile, properties);

        Properties providerProps = new Properties();
        providerProps.setProperty(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_FILE, testFile.getAbsolutePath());
        providerProps.setProperty(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_KEY, parentKey);
        provider = new PropertiesFileSettingsProvider();
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
    @Test
    public void testCircleShouldNotBePossible() throws IOException {
        String circle1 = "circle1";
        String circle2 = "circle2";
        File circle2File = getTmpFile(circle2);
        File circle1File = getTmpFile(circle1);
        try {
            Properties props = new Properties();
            props.setProperty(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_FILE, circle1);
            saveProps(circle2File, props);

            props = new Properties();
            props.setProperty(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_FILE, circle2);
            saveProps(circle1File, props);

            Properties providerProps = new Properties();
            providerProps.setProperty(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_FILE, circle1File.getAbsolutePath());
            provider = new PropertiesFileSettingsProvider();
            provider.configure(providerProps);
            provider.loadSettings();
        } catch (StackOverflowError e) {
            Assert.fail("Circular dependencies possible!");
        } finally {
            circle1File.delete();
            circle2File.delete();
        }
    }
}
