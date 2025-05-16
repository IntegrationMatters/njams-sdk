package com.im.njams.sdk.configuration.provider;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.im.njams.sdk.configuration.ConfigurationProvider;
import com.im.njams.sdk.configuration.ConfigurationProviderFactory;
import com.im.njams.sdk.settings.Settings;

public class FileConfigurationProviderTest {

    private ConfigurationProviderFactory factory = null;
    private FileConfigurationProvider toTest = null;
    private File tmpDir = null;

    @Before
    public void setUp() throws Exception {
        toTest = new FileConfigurationProvider();
        tmpDir = Files.createTempDirectory("sdk-").toFile();
    }

    @After
    public void tearDown() throws IOException {
        if (tmpDir != null && tmpDir.isDirectory()) {
            try (Stream<Path> paths = Files.walk(tmpDir.toPath())) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    private void configure(File configFile) {
        Properties props = new Properties();
        props.setProperty(FileConfigurationProvider.FILE_CONFIGURATION, configFile.getAbsolutePath());
        toTest.configure(props, null);

        Settings settings = new Settings();
        settings.addAll(props);
        settings.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, FileConfigurationProvider.NAME);
        factory = new ConfigurationProviderFactory(settings, null);
    }

    @Test
    public void testValidate1() {
        assertTrue(tmpDir.isDirectory());
        configure(tmpDir);
        ConfigurationValidationResult validation = toTest.validate();
        assertTrue(validation.isReadable());
        assertTrue(validation.isWritable());
        assertFalse(validation.hasErrors());
    }

    @Test
    public void testValidate2() throws IOException {
        File config = new File(tmpDir, "test.json");
        config.createNewFile();
        assertTrue(config.isFile());
        configure(config);
        ConfigurationValidationResult validation = toTest.validate();
        assertTrue(validation.isReadable());
        assertTrue(validation.isWritable());
        assertFalse(validation.hasErrors());
    }

    @Test
    public void testValidate3() {
        File config = new File(tmpDir, "gibtsNicht.json");
        assertFalse(config.isFile());
        configure(config);
        ConfigurationValidationResult validation = toTest.validate();
        assertTrue(validation.isReadable());
        assertTrue(validation.isWritable());
        assertFalse(validation.hasErrors());
    }

    @Test
    public void testValidate4() {
        ConfigurationValidationResult validation = toTest.validate();
        assertTrue(validation.isReadable());
        assertTrue(validation.isWritable());
        assertFalse(validation.hasErrors());
    }

    @Test
    public void testValidateFail() {
        File config = new File(tmpDir, "xxx/gibtsNicht.json");
        assertFalse(config.isFile());
        configure(config);
        ConfigurationValidationResult validation = toTest.validate();
        assertFalse(validation.isReadable());
        assertFalse(validation.isWritable());
        assertTrue(validation.hasErrors());
        assertEquals(2, validation.getErrors().size());
        assertTrue(validation.getErrors().stream().allMatch(FileNotFoundException.class::isInstance));
        System.out.println(validation.getErrors());
    }

    @Test
    public void testValidateReadOnly() throws IOException {
        File config = new File(tmpDir, "config.json");
        config.createNewFile();
        config.setReadOnly();
        assertTrue(config.isFile());
        configure(config);
        ConfigurationValidationResult validation = toTest.validate();
        assertTrue(validation.isReadable());
        assertFalse(validation.isWritable());
        assertTrue(validation.hasErrors());
        assertEquals(1, validation.getErrors().size());
        assertTrue(validation.getErrors().stream().allMatch(IOException.class::isInstance));
        System.out.println(validation.getErrors());
    }

    @Test
    @Ignore("Revoking read permission does not work on Windows")
    public void testValidateNoRead() throws IOException {
        File config = new File(tmpDir, "config.json");
        config.createNewFile();
        config.setReadable(false, false);
        assertTrue(config.isFile());
        // fails here already (for Windows)
        assertFalse(Files.isReadable(config.toPath()));
        configure(config);
        ConfigurationValidationResult validation = toTest.validate();
        assertFalse(validation.isReadable());
        assertFalse(validation.isWritable());
        assertTrue(validation.hasErrors());
        assertEquals(1, validation.getErrors().size());
        assertTrue(validation.getErrors().stream().allMatch(IOException.class::isInstance));
    }

    @Test
    public void testFactory() throws IOException {
        File config = new File(tmpDir, "test.json");
        config.createNewFile();
        assertTrue(config.isFile());
        configure(config);
        ConfigurationProvider provider = factory.getConfigurationProvider();
        assertNotNull(provider);
    }

    @Test
    public void testFactoryFail() {
        File config = new File(tmpDir, "/xxx/gibtsNicht.json");
        assertFalse(config.isFile());
        configure(config);
        assertThrows(IllegalStateException.class, () -> factory.getConfigurationProvider());
    }

}
