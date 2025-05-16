/*
 * Copyright (c) 2025 Integration Matters GmbH
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
package com.im.njams.sdk.configuration.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import com.im.njams.sdk.configuration.Configuration;

/**
 * Specifies whether or not a {@link Configuration} is accessible.
 */
public class ConfigurationValidationResult {

    /**
     * Result indicating success, i.e., the according {@link Configuration} is fully accessible (readable and writable).
     */
    public static final ConfigurationValidationResult SUCCESS = new ConfigurationValidationResult(true, true);
    private final boolean readable;
    private final boolean writable;

    private final Collection<Exception> errors = new ArrayList<>();

    /**
     * Sole constructor to be used in non-success case. For success, use the {@link #SUCCESS} instance.
     * @param readable Whether the {@link Configuration} is readable
     * @param writable Whether the {@link Configuration} is writable (and readable)
     * @param errors Any error details describing the validation result.
     */
    public ConfigurationValidationResult(boolean readable, boolean writable, Exception... errors) {
        this.readable = readable;
        this.writable = writable;
        if (errors != null) {
            Collections.addAll(this.errors, errors);
        }
    }

    /**
     * Returns whether the according {@link Configuration} is readable.
     * @return Whether the according {@link Configuration} is readable.
     */
    public boolean isReadable() {
        return readable;
    }

    /**
     * Returns whether the according {@link Configuration} is writable (and readable).
     * @return Whether the according {@link Configuration} is writable (and readable).
     */
    public boolean isWritable() {
        return writable;
    }

    /**
     * Provides any errors that occurred on validation or that better describe why the {@link Configuration} is
     * not readable or writable.
     * @return List of errors.
     */
    public Collection<Exception> getErrors() {
        return errors;
    }

    /**
     * Whether errors are available for this result.
     * @return Whether errors are available for this result.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
