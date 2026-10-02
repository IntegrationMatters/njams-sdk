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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.utils;

import java.util.Optional;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plain utility without any dependencies on SDK state for running code whose exceptions shall be ignored. Suppressed
 * exceptions are logged at debug level only. Clients may use it as well.
 *
 * @since 6.1.0
 */
public class ExceptionSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionSupport.class);

    private ExceptionSupport() {
        // utility class
    }

    /**
     * A supplier that may throw a checked exception.
     *
     * @param <T> The type of the supplied value.
     * @since 6.1.0
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        /**
         * Supplies a value.
         *
         * @return The supplied value.
         * @throws Exception If the value cannot be supplied.
         */
        T get() throws Exception;
    }

    /**
     * Wraps a supplier that may throw an exception into an {@link Optional}. If the supplier throws an exception, it is
     * logged at debug level and an empty {@link Optional} is returned.
     *
     * @param supplier The supplier to wrap.
     * @param <T>      The type of the value supplied.
     * @return An {@link Optional} containing the value supplied by the supplier, or empty if an exception was thrown.
     */
    public static <T> Optional<T> optionalOf(ThrowingSupplier<T> supplier) {
        try {
            return Optional.ofNullable(supplier.get());
        } catch (Exception e) {
            LOG.debug("Exception suppressed", e);
            return Optional.empty();
        }
    }

    /**
     * Runs a {@link Runnable} and suppresses any exceptions thrown, logging them at debug level.
     *
     * @param runnable The {@link Runnable} to run.
     */
    public static void suppressException(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            LOG.debug("Exception suppressed", e);
        }
    }

    /**
     * Runs a {@link Callable} and suppresses any exceptions thrown, logging them at debug level.
     *
     * @param callable The {@link Callable} to run.
     * @param <T>      The type of the value returned by the {@link Callable}.
     * @return The value returned by the {@link Callable}, or null if an exception was thrown.
     */
    public static <T> T suppressException(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            LOG.debug("Exception suppressed", e);
            return null;
        }
    }

}
