package com.im.njams.sdk.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that {@link ServiceLoaderSupport} tells a provider that is merely not applicable on this classpath (it
 * needs a JMS API that is absent) from one that is genuinely unavailable, and that {@code findAll} keeps order.
 */
public class ServiceLoaderSupportAvailabilityTest {

    /** The service type used for the lookups in this test. */
    public interface Spi {
        String name();
    }

    /** A working implementation. */
    public static class First implements Spi {
        @Override
        public String name() {
            return "first";
        }
    }

    /** Another working implementation. */
    public static class Second implements Spi {
        @Override
        public String name() {
            return "second";
        }
    }

    /** A second working implementation answering to the same name as {@link First}, as both JMS trees do. */
    public static class AlsoFirst implements Spi {
        @Override
        public String name() {
            return "first";
        }
    }

    /** Fails like a provider whose JMS API is missing, in the dotted spelling of a ClassNotFoundException. */
    public static class MissingJakartaDotted implements Spi {
        public MissingJakartaDotted() {
            throw new IllegalStateException("wrapped", new ClassNotFoundException("jakarta.jms.Message"));
        }

        @Override
        public String name() {
            return "missingJakartaDotted";
        }
    }

    /** Fails for a reason that has nothing to do with a JMS API. */
    public static class CtorFails implements Spi {
        public CtorFails() {
            throw new IllegalStateException("constructor failed");
        }

        @Override
        public String name() {
            return "ctorFails";
        }
    }

    private static final String MISSING_JAVAX_SLASH = "missing.JavaxSlash";
    private static final String MISSING_OTHER_TYPE = "missing.OtherType";

    private static class TestClassLoader extends ServicesFileClassLoader {

        TestClassLoader(ClassLoader parent, URL servicesFile) {
            super(parent, Spi.class, servicesFile, true);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.equals(MISSING_JAVAX_SLASH)) {
                // the JVM reports the internal name, with slashes
                throw new NoClassDefFoundError("javax/jms/Message");
            }
            if (name.equals(MISSING_OTHER_TYPE)) {
                throw new NoClassDefFoundError("some/other/Type");
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
    public void aProviderMissingAJmsApiTypeIsNotApplicable_andHasUnavailableStaysFalse() throws IOException {
        // slash spelling (NoClassDefFoundError) and dotted spelling (ClassNotFoundException)
        ServiceLoaderSupport<Spi> loader =
            loaderFor(MISSING_JAVAX_SLASH, MissingJakartaDotted.class.getName(), First.class.getName());
        List<String> loaded = loader.getAll().stream().map(Spi::name).collect(Collectors.toList());

        assertEquals(Collections.singletonList("first"), loaded);
        assertFalse(loader.hasUnavailable());
        String description = loader.describeAvailability(Spi::name);
        String notApplicable = description.substring(description.indexOf("Not applicable"));
        assertTrue(description, description.contains("Unavailable: none"));
        assertTrue(description, notApplicable.contains("javax/jms/Message"));
        assertTrue(description, notApplicable.contains("jakarta.jms.Message"));
    }

    @Test
    public void aProviderFailingForAnyOtherReasonIsUnavailable_andHasUnavailableIsTrue() throws IOException {
        for (String entry : Arrays.asList(CtorFails.class.getName(), MISSING_OTHER_TYPE, "does.not.Exist")) {
            ServiceLoaderSupport<Spi> loader = loaderFor(entry, First.class.getName());
            loader.getAll();

            assertTrue(entry, loader.hasUnavailable());
        }
    }

    @Test
    public void describeAvailability_namesTheMissingJmsApiEvenWhenNothingIsUnavailable() throws IOException {
        ServiceLoaderSupport<Spi> loader = loaderFor(MISSING_JAVAX_SLASH, MissingJakartaDotted.class.getName());
        loader.getAll();

        String description = loader.describeAvailability(Spi::name);

        assertFalse(loader.hasUnavailable());
        assertTrue(description, description.contains("javax/jms/Message"));
        assertTrue(description, description.contains("jakarta.jms.Message"));
        assertTrue(description, description.contains("Unavailable: none"));
    }

    @Test
    public void describeAvailability_listsEachNameOnce_whenImplementationsShareOne() throws IOException {
        ServiceLoaderSupport<Spi> loader =
            loaderFor(First.class.getName(), AlsoFirst.class.getName(), Second.class.getName());
        loader.getAll();

        String description = loader.describeAvailability(Spi::name);

        assertTrue(description, description.startsWith("Available: [first, second]"));
        // the de-duplication is for the report only: the lookup still sees both implementations
        assertEquals(2, loader.findAll(s -> "first".equals(s.name())).size());
    }

    @Test
    public void findAll_returnsEveryMatchInServicesFileOrder() throws IOException {
        ServiceLoaderSupport<Spi> loader =
            loaderFor(Second.class.getName(), CtorFails.class.getName(), First.class.getName());

        List<String> names = loader.findAll(s -> true).stream().map(Spi::name).collect(Collectors.toList());

        assertEquals(Arrays.asList("second", "first"), names);
    }

    @Test
    public void findAll_returnsEmptyWhenNothingMatches() throws IOException {
        ServiceLoaderSupport<Spi> loader = loaderFor(First.class.getName(), Second.class.getName());

        assertEquals(Collections.emptyList(), loader.findAll(s -> false));
    }
}
