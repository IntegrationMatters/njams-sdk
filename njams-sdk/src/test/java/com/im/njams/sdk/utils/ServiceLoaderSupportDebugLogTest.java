package com.im.njams.sdk.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies the provider listing {@link ServiceLoaderSupport} logs at debug level: a provider whose class cannot be
 * fully linked must not break the lookup, and a provider is labelled by a {@code toString()} declared anywhere in its
 * class hierarchy.
 */
public class ServiceLoaderSupportDebugLogTest {

    /** The service type used for the lookups in this test. */
    public interface Spi {
    }

    /** A class the provider below depends on, hidden from the test class loader. */
    public static class Dependency {
    }

    /** A provider that can be instantiated, but whose method signatures cannot be resolved. */
    public static class NeedsDependency implements Spi {
        public void use(Dependency dependency) {
            // only its signature matters
        }
    }

    /** A provider declaring its own label. */
    public static class LabelledBase implements Spi {
        @Override
        public String toString() {
            return "inherited-label";
        }
    }

    /** A provider inheriting its label. */
    public static class InheritsLabel extends LabelledBase {
    }

    /**
     * Serves only this test's services file for {@link Spi}, defines {@link NeedsDependency} itself and fails to
     * load {@link Dependency} for it, like a provider whose dependency is missing from the classpath.
     */
    private static class TestClassLoader extends ServicesFileClassLoader {

        TestClassLoader(ClassLoader parent, URL servicesFile) {
            super(parent, Spi.class, servicesFile, true);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(Dependency.class.getName())) {
                throw new ClassNotFoundException(name);
            }
            if (!name.equals(NeedsDependency.class.getName())) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    byte[] bytes = readClassFile(name);
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                return loaded;
            }
        }

        private byte[] readClassFile(String name) throws ClassNotFoundException {
            try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                return in.readAllBytes();
            } catch (IOException | NullPointerException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    private static class CapturingAppender extends AppenderSkeleton {
        private final List<String> messages = new ArrayList<>();

        @Override
        protected void append(LoggingEvent event) {
            messages.add(event.getRenderedMessage());
        }

        @Override
        public void close() {
            // nothing to release
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }

    private final Logger logger = Logger.getLogger(ServiceLoaderSupport.class);
    private final CapturingAppender appender = new CapturingAppender();
    private Level originalLevel;
    private ClassLoader originalContextLoader;
    private File servicesFile;

    @Before
    public void setUp() throws IOException {
        originalContextLoader = Thread.currentThread().getContextClassLoader();
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        servicesFile = File.createTempFile("spi", ".services");
    }

    @After
    public void tearDown() {
        Thread.currentThread().setContextClassLoader(originalContextLoader);
        logger.removeAppender(appender);
        logger.setLevel(originalLevel);
        servicesFile.delete();
    }

    private ServiceLoaderSupport<Spi> loaderFor(Class<?>... providers) throws IOException {
        List<String> entries = new ArrayList<>();
        for (Class<?> provider : providers) {
            entries.add(provider.getName());
        }
        Files.write(servicesFile.toPath(), String.join("\n", entries).getBytes(StandardCharsets.UTF_8));
        Thread.currentThread().setContextClassLoader(
            new TestClassLoader(getClass().getClassLoader(), servicesFile.toURI().toURL()));
        return new ServiceLoaderSupport<>(Spi.class);
    }

    private String debugListing() {
        assertEquals(appender.messages.toString(), 1, appender.messages.size());
        return appender.messages.get(0);
    }

    @Test
    public void providerWithUnresolvableSignatureIsListedByClassName() throws IOException {
        ServiceLoaderSupport<Spi> loader = loaderFor(NeedsDependency.class, LabelledBase.class);

        assertEquals(2, loader.getAll().size());
        String listing = debugListing();
        assertTrue(listing, listing.contains(NeedsDependency.class.getName()));
        assertTrue(listing, listing.contains("inherited-label"));
    }

    @Test
    public void providerIsListedByInheritedToString() throws IOException {
        loaderFor(InheritsLabel.class);

        String listing = debugListing();
        assertTrue(listing, listing.contains("inherited-label"));
    }
}
