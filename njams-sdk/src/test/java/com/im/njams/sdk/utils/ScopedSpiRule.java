package com.im.njams.sdk.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.rules.ExternalResource;

/**
 * Registers additional SPI providers for the duration of a single test, by serving an extra
 * {@code META-INF/services} entry through the thread context classloader that
 * {@link ServiceLoaderSupport} looks up against.
 * <p>
 * Deliberately broken fixtures are registered this way instead of in the shared
 * {@code src/test/resources/META-INF/services} files, so that they are instantiated only where a test
 * actually wants the failure — rather than on every SPI lookup in every test and every integration test,
 * each one logging a stack trace.
 * <p>
 * The extra entry is appended to whatever the parent classloader already provides, so the regular
 * providers stay visible. The lookup must happen on the thread running the test.
 */
public class ScopedSpiRule extends ExternalResource {

    private final Class<?> serviceType;
    private final List<String> providerClassNames;

    private ClassLoader originalContextLoader;
    private Path servicesFile;

    /**
     * @param serviceType the SPI interface the providers are registered for
     * @param providers   the provider classes to register for the duration of the test
     */
    public ScopedSpiRule(Class<?> serviceType, Class<?>... providers) {
        this.serviceType = serviceType;
        providerClassNames = Arrays.stream(providers).map(Class::getName).collect(Collectors.toList());
    }

    @Override
    protected void before() throws Throwable {
        servicesFile = Files.createTempFile("scoped-spi", ".services");
        Files.write(servicesFile, String.join("\n", providerClassNames).getBytes(StandardCharsets.UTF_8));
        originalContextLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ServicesFileClassLoader(originalContextLoader,
            serviceType, servicesFile.toUri().toURL(), false));
    }

    @Override
    protected void after() {
        Thread.currentThread().setContextClassLoader(originalContextLoader);
        try {
            Files.deleteIfExists(servicesFile);
        } catch (IOException e) {
            // a leftover temp file is harmless
        }
    }
}
