package com.im.njams.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.utils.PropertyUtil;

/**
 * Tests how {@link NjamsMetadata} resolves the client version from the version files on the classpath and from
 * the version argument passed to the {@link Njams} constructor.
 * <p>
 * The version files are read from the classpath, so each test writes its fixture into this test class' own
 * output directory (which is a classpath root) and removes it again afterwards.
 */
public class NjamsMetadataClientVersionTest {

    private static final String[] FIXTURE_FILES = { "client.version", "sdk.version", "msg.version" };

    private File classpathRoot;
    private final List<File> created = new ArrayList<>();
    private CapturingAppender appender;
    private Logger propertyUtilLogger;

    @Before
    public void setUp() throws Exception {
        classpathRoot = new File(
            NjamsMetadataClientVersionTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        // never rely on what a previous (possibly aborted) run left behind
        for (String name : FIXTURE_FILES) {
            new File(classpathRoot, name).delete();
        }
        appender = new CapturingAppender();
        propertyUtilLogger = Logger.getLogger(PropertyUtil.class);
        propertyUtilLogger.addAppender(appender);
    }

    @After
    public void tearDown() {
        propertyUtilLogger.removeAppender(appender);
        created.forEach(File::delete);
        created.clear();
    }

    private void writeVersionFile(String name, String content) throws Exception {
        File file = new File(classpathRoot, name);
        created.add(file);
        try (Writer writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private NjamsMetadata metadataWithVersion(String version) {
        return new NjamsMetadata(Path.of("SDK4", "TEST"), version, "sdk4", new LifecycleState());
    }

    @Test
    public void versionFileWithCurrentKeyIsUsed() throws Exception {
        writeVersionFile("client.version", "client.version=7.7.7\n");
        assertEquals("7.7.7", metadataWithVersion("1.0").getClientVersion());
    }

    @Test
    public void versionFileWithDeprecatedKeyIsUsed() throws Exception {
        writeVersionFile("client.version", "clientVersion=9.9.9\n");
        assertEquals("9.9.9", metadataWithVersion("1.0").getClientVersion());
    }

    @Test
    public void deprecatedKeyLogsDeprecationWarning() throws Exception {
        writeVersionFile("client.version", "clientVersion=9.9.9\n");
        metadataWithVersion("1.0");
        assertEquals(1, appender.warningsContaining("clientVersion").size());
    }

    @Test
    public void currentKeyLogsNoDeprecationWarning() throws Exception {
        writeVersionFile("client.version", "client.version=7.7.7\n");
        metadataWithVersion("1.0");
        assertTrue(appender.warningsContaining("clientVersion").isEmpty());
    }

    @Test
    public void currentKeyWinsOverDeprecatedKey() throws Exception {
        writeVersionFile("client.version", "client.version=7.7.7\nclientVersion=9.9.9\n");
        NjamsMetadata metadata = metadataWithVersion("1.0");
        assertEquals("7.7.7", metadata.getClientVersion());
        assertTrue(appender.warningsContaining("clientVersion").isEmpty());
    }

    @Test
    public void versionFileWinsOverConstructorArgument() throws Exception {
        writeVersionFile("client.version", "clientVersion=9.9.9\n");
        assertEquals("9.9.9", metadataWithVersion("1.0").getClientVersion());
    }

    @Test
    public void constructorArgumentIsUsedWhenNoVersionFileIsPresent() {
        assertEquals("4.2.0", metadataWithVersion("4.2.0").getClientVersion());
    }

    @Test
    public void constructorArgumentIsUsedWhenVersionFileHasBlankValue() throws Exception {
        writeVersionFile("client.version", "client.version=\n");
        assertEquals("4.2.0", metadataWithVersion("4.2.0").getClientVersion());
    }

    @Test
    public void blankCurrentKeyDoesNotShadowDeprecatedKey() throws Exception {
        writeVersionFile("client.version", "client.version=\nclientVersion=9.9.9\n");
        NjamsMetadata metadata = metadataWithVersion("4.2.0");
        assertEquals("9.9.9", metadata.getClientVersion());
        assertEquals(1, appender.warningsContaining("clientVersion").size());
    }

    @Test
    public void internalVersionFilesAreNoSourceOfTheClientVersion() throws Exception {
        // sdk.version and msg.version are shipped by the SDK and the message format; only the client's own
        // client.version announces the client version
        writeVersionFile("sdk.version", "client.version=6.6.6\n");
        writeVersionFile("msg.version", "clientVersion=5.5.5\n");
        assertEquals("4.2.0", metadataWithVersion("4.2.0").getClientVersion());
    }

    @Test
    public void blankConstructorArgumentWithoutVersionFileFails() {
        try {
            metadataWithVersion("  ");
            fail("expected NjamsSdkRuntimeException");
        } catch (NjamsSdkRuntimeException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(Njams.CLIENT_VERSION_KEY));
        }
    }

    @Test
    public void nullConstructorArgumentWithoutVersionFileFails() {
        try {
            metadataWithVersion(null);
            fail("expected NjamsSdkRuntimeException");
        } catch (NjamsSdkRuntimeException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(Njams.CLIENT_VERSION_KEY));
        }
    }

    @Test
    public void sdkVersionIsReadFromTheSdkVersionFile() {
        String sdkVersion = metadataWithVersion("1.0").getSdkVersion();
        assertNotNull(sdkVersion);
        assertFalse("SDK version was not read from the version file shipped with the SDK", "unknown".equals(sdkVersion));
    }

    /** Captures log4j events for assertions. */
    private static final class CapturingAppender extends AppenderSkeleton {
        private final List<LoggingEvent> events = new ArrayList<>();

        @Override
        protected synchronized void append(LoggingEvent event) {
            events.add(event);
        }

        synchronized List<String> warningsContaining(String needle) {
            List<String> result = new ArrayList<>();
            for (LoggingEvent event : events) {
                if (event.getLevel() == Level.WARN
                    && String.valueOf(event.getRenderedMessage()).contains(needle)) {
                    result.add(event.getRenderedMessage());
                }
            }
            return result;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
