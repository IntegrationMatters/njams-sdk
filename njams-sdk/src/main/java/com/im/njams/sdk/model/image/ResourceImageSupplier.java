/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.model.image;

import java.net.URL;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Image supplier implementation that loads an image as classpath resource.
 * @author cwinkler
 *
 */
public class ResourceImageSupplier extends URLImageSupplier {

    /**
     * Constructor taking a specific classloader for loading a resource file.
     * @param name The name that is used to access the image.
     * @param classLoader The classLoader used for loading the resource with the given resource path.
     * @param resourcePath The path of the resource file to load.
     * @throws NjamsSdkRuntimeException if the resource cannot be found on the classpath
     */
    public ResourceImageSupplier(String name, ClassLoader classLoader, String resourcePath) {
        super(name, requireResource(classLoader, resourcePath));
    }

    /**
     * Constructor for loading a resource file using the default classloader.
     * @param name The name that is used to access the image.
     * @param resourcePath The path of the resource file to load.
     * @throws NjamsSdkRuntimeException if the resource cannot be found on the classpath
     */
    public ResourceImageSupplier(String name, String resourcePath) {
        this(name, Thread.currentThread().getContextClassLoader(), resourcePath);
    }

    /**
     * Resolves the classpath resource and fails with a descriptive exception when it is missing.
     * A missing resource is a packaging/programming error (the resource is expected to be shipped
     * with the product), so it is reported clearly rather than failing later with an opaque
     * {@link NullPointerException}.
     */
    private static URL requireResource(ClassLoader classLoader, String resourcePath) {
        final URL url = classLoader.getResource(resourcePath);
        if (url == null) {
            throw new NjamsSdkRuntimeException("Image resource not found on classpath: " + resourcePath);
        }
        return url;
    }

}
