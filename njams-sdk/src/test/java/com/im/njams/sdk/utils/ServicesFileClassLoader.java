package com.im.njams.sdk.utils;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Serves a {@code META-INF/services} file of a test's own making for one SPI type, so that
 * {@link ServiceLoaderSupport} — which resolves against the thread context classloader — sees exactly the
 * providers the test wants, without touching the shared services files in {@code src/test/resources}.
 * <p>
 * Whether the file replaces the entries the parent classloader already provides or is added to them is the
 * caller's choice: a test that wants only its own providers replaces, while one that adds a provider to the
 * regular set appends.
 */
class ServicesFileClassLoader extends ClassLoader {

    private final String resourceName;
    private final URL servicesFile;
    private final boolean replaceParentEntries;

    /**
     * @param parent               the classloader to delegate everything else to
     * @param serviceType          the SPI interface whose services file is served
     * @param servicesFile         the file to serve for it
     * @param replaceParentEntries {@code true} to serve only this file, {@code false} to append it to the
     *                             entries the parent already provides
     */
    ServicesFileClassLoader(ClassLoader parent, Class<?> serviceType, URL servicesFile,
        boolean replaceParentEntries) {
        super(parent);
        resourceName = "META-INF/services/" + serviceType.getName();
        this.servicesFile = servicesFile;
        this.replaceParentEntries = replaceParentEntries;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (!resourceName.equals(name)) {
            return super.getResources(name);
        }
        if (replaceParentEntries) {
            return Collections.enumeration(Collections.singletonList(servicesFile));
        }
        final List<URL> all = new ArrayList<>(Collections.list(super.getResources(name)));
        all.add(servicesFile);
        return Collections.enumeration(all);
    }
}
