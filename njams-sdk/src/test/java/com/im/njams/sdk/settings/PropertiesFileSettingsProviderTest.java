package com.im.njams.sdk.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.settings.provider.PropertiesFileSettingsProvider;
import org.junit.Assert;

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
        props.setProperty(PropertiesFileSettingsProvider.PARENT_CONFIGURATION, parentName);
        props.setProperty("a", "1");
        props.setProperty("b", "2");
        saveProps(testFile, props);

        Properties providerProps = new Properties();
        providerProps.setProperty(PropertiesFileSettingsProvider.FILE_CONFIGURATION, testFile.getAbsolutePath());
        provider = new PropertiesFileSettingsProvider();
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
        providerProps.setProperty(PropertiesFileSettingsProvider.FILE_CONFIGURATION, tmpdir.getAbsolutePath());
        provider = new PropertiesFileSettingsProvider();
        provider.configure(providerProps);
        assertEquals(new File(tmpdir, PropertiesFileSettingsProvider.DEFAULT_FILE), provider.getFile());
    }

    @Test
    public void testFile() {
        Properties providerProps = new Properties();
        providerProps.setProperty(PropertiesFileSettingsProvider.FILE_CONFIGURATION, testFile.getAbsolutePath());
        provider = new PropertiesFileSettingsProvider();
        provider.configure(providerProps);
        assertEquals(testFile, provider.getFile());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFileNotExists() {
        Properties providerProps = new Properties();
        providerProps.setProperty(PropertiesFileSettingsProvider.FILE_CONFIGURATION, "/testxy.properties");
        provider = new PropertiesFileSettingsProvider();
        provider.configure(providerProps);
    }

    @Test
    public void testModifiedParentKey() throws IOException {
        String parentKey = "parentFileKey";
        String parent = (String) properties.remove(PropertiesFileSettingsProvider.PARENT_CONFIGURATION);
        properties.setProperty(parentKey, parent);
        saveProps(testFile, properties);

        Properties providerProps = new Properties();
        providerProps.setProperty(PropertiesFileSettingsProvider.FILE_CONFIGURATION, testFile.getAbsolutePath());
        providerProps.setProperty(PropertiesFileSettingsProvider.PARENT_CONFIGURATION_KEY, parentKey);
        provider = new PropertiesFileSettingsProvider();
        provider.configure(providerProps);
        
        assertEquals("1", properties.getProperty("a"));
        assertEquals("2", properties.getProperty("b"));
        assertEquals("parentValue", properties.getProperty("parentKey"));

    }

}
