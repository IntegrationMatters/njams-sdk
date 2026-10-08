package com.im.njams.sdk.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that {@link ServiceLoaderSupport} suppresses implementations that cannot be loaded, but remembers why,
 * so that a failed lookup can report them.
 */
public class ServiceLoaderSupportUnavailableTest {

    /** The service type used for the lookups in this test. */
    public interface Spi {
        String name();
    }

    /** A working implementation. */
    public static class Working implements Spi {
        @Override
        public String name() {
            return "working";
        }
    }

    /** An implementation whose constructor fails. */
    public static class CtorFails implements Spi {
        public CtorFails() {
            throw new IllegalStateException("constructor failed");
        }

        @Override
        public String name() {
            return "ctorFails";
        }
    }

    private static final String MISSING = "does.not.Exist";
    private static final String BROKEN_LINKAGE = "broken.Linkage";

    /** Serves a services file for {@link Spi} and fails loading of BROKEN_LINKAGE like a missing dependency. */
    private static class TestClassLoader extends ClassLoader {
        private final URL servicesFile;

        TestClassLoader(ClassLoader parent, URL servicesFile) {
            super(parent);
            this.servicesFile = servicesFile;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (name.equals("META-INF/services/" + Spi.class.getName())) {
                return Collections.enumeration(Collections.singletonList(servicesFile));
            }
            return super.getResources(name);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.equals(BROKEN_LINKAGE)) {
                throw new NoClassDefFoundError("missing/Base");
            }
            return super.loadClass(name);
        }
    }

    private ClassLoader originalContextLoader;
    private File servicesFile;

    @Before
    public void setUp() throws IOException {
        originalContextLoader = Thread.currentThread().getContextClassLoader();
        servicesFile = File.createTempFile("spi", ".services");
    }

    @After
    public void tearDown() {
        Thread.currentThread().setContextClassLoader(originalContextLoader);
        servicesFile.delete();
    }

    private ServiceLoaderSupport<Spi> loaderFor(String... entries) throws IOException {
        Files.write(servicesFile.toPath(), String.join("\n", entries).getBytes(StandardCharsets.UTF_8));
        Thread.currentThread().setContextClassLoader(
            new TestClassLoader(getClass().getClassLoader(), servicesFile.toURI().toURL()));
        return new ServiceLoaderSupport<>(Spi.class);
    }

    @Test
    public void unusableImplementationsAreSkippedAndUsableOnesFound() throws IOException {
        ServiceLoaderSupport<Spi> loader =
            loaderFor(MISSING, CtorFails.class.getName(), BROKEN_LINKAGE, Working.class.getName());

        Set<String> names = loader.stream().map(Spi::name).collect(Collectors.toSet());
        assertEquals(Collections.singleton("working"), names);
        assertNotNull(loader.find(s -> s.name().equals("working")));
    }

    @Test
    public void unavailableImplementationsAreDescribedWithTheirCause() throws IOException {
        ServiceLoaderSupport<Spi> loader =
            loaderFor(MISSING, CtorFails.class.getName(), BROKEN_LINKAGE, Working.class.getName());
        loader.getAll();

        String description = loader.describeAvailability(Spi::name);

        assertTrue(description, description.contains("Available: [working]"));
        assertTrue(description, description.contains("Provider " + MISSING + " not found"));
        assertTrue(description, description.contains(
            "Provider " + CtorFails.class.getName() + " could not be instantiated"));
        assertTrue(description, description.contains("IllegalStateException: constructor failed"));
        assertTrue(description, description.contains("NoClassDefFoundError: missing/Base"));
    }

    @Test
    public void descriptionContainsNoStackTraces() throws IOException {
        ServiceLoaderSupport<Spi> loader = loaderFor(CtorFails.class.getName(), Working.class.getName());
        loader.getAll();

        String description = loader.describeAvailability(Spi::name);

        assertFalse(description, description.contains("\tat "));
        assertFalse(description, description.contains("java.lang.IllegalStateException"));
    }

    @Test
    public void descriptionWithoutFailuresSaysNoneUnavailable() throws IOException {
        ServiceLoaderSupport<Spi> loader = loaderFor(Working.class.getName());
        loader.getAll();

        String description = loader.describeAvailability(Spi::name);

        assertTrue(description, description.contains("Available: [working]"));
        assertTrue(description, description.contains("Unavailable: none"));
    }

    @Test
    public void failuresAreRememberedAfterTheFirstPass() throws IOException {
        ServiceLoaderSupport<Spi> loader = loaderFor(CtorFails.class.getName(), Working.class.getName());
        loader.getAll();
        // a second pass over the same loader does not see the failed entry any more
        loader.getAll();

        String description = loader.describeAvailability(Spi::name);

        assertTrue(description, description.contains("constructor failed"));
    }

    @Test
    public void iteratorSkipsFailuresInHasNext() throws IOException {
        ServiceLoaderSupport<Spi> loader = loaderFor(BROKEN_LINKAGE, Working.class.getName());
        Iterator<Spi> it = loader.iterator();

        assertTrue(it.hasNext());
        assertEquals("working", it.next().name());
        assertFalse(it.hasNext());
    }

    @Test
    public void syntaxErrorInServicesFileIsReported() throws IOException {
        ServiceLoaderSupport<Spi> loader = loaderFor("bad name!", Working.class.getName());
        loader.getAll();

        String description = loader.describeAvailability(Spi::name);

        assertTrue(description, description.contains("Illegal configuration-file syntax"));
    }
}
