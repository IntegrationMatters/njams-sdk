/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.model.image;

import java.util.Base64;
import java.util.Objects;

/**
 * Base class for supplying images to the SDK.
 *
 * @author cwinkler
 *
 */
public abstract class ImageSupplier {
    private final String name;
    private final int hash;

    /**
     * Sole constructor.
     *
     * @param name
     *            The name that is used to access the image.
     */
    public ImageSupplier(String name) {
        this.name = Objects.requireNonNull(name);
        hash = 13 + 47 * name.hashCode();
    }

    /**
     * Has to return the Base64 encoded image defined by this instance.
     *
     * @return the image
     */
    public abstract String getBase64Image();

    /**
     * The name that is used to access this image.
     *
     * @return the name of the image
     */
    public String getName() {
        return name;
    }

    /**
     * Utility method for Base64 encoding.
     *
     * @param bytes
     *            to encode as Base64
     * @return the encoded string
     */
    public static String encodeBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof ImageSupplier)) {
            return false;
        }
        return ((ImageSupplier) other).name.equals(name);
    }

    @Override
    public String toString() {
        return getName() + " [" + getClass().getSimpleName() + "]";
    }
}
