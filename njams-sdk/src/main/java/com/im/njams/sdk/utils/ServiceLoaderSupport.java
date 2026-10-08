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
package com.im.njams.sdk.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for SPI lookup.
 * @param <S> The service type to load.
 */
public class ServiceLoaderSupport<S> implements Iterable<S> {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceLoaderSupport.class);
    /**
     * Hint to append to the message of an exception that is thrown because a lookup failed and that has been
     * reported in detail (see {@link #describeAvailability(Function)}) to the log.
     */
    public static final String SEE_LOG_HINT = "See the log for the available and unavailable implementations.";
    // safeguard against an underlying iterator that fails repeatedly without making any progress
    private static final int MAX_CONSECUTIVE_FAILURES = 100;
    private static final int MAX_CAUSE_DEPTH = 10;
    private static final String NEW_LINE = System.lineSeparator();
    private final Class<S> serviceType;
    private final ServiceLoader<S> serviceLoader;
    // descriptions of the implementations that could not be loaded; filled when they are first encountered
    private final Set<String> unavailable = Collections.synchronizedSet(new LinkedHashSet<>());

    /**
     * Iterates over all implementations that can be loaded. Implementations that cannot be loaded are skipped, but
     * remembered in {@link ServiceLoaderSupport#unavailable}.
     */
    private class SaveIterator implements Iterator<S> {
        private final Iterator<S> it;
        private S prefetched;

        private SaveIterator(final Iterator<S> originalIterator) {
            it = originalIterator;
        }

        @Override
        public boolean hasNext() {
            int failures = 0;
            while (prefetched == null) {
                try {
                    if (!it.hasNext()) {
                        return false;
                    }
                    prefetched = it.next();
                } catch (final Throwable t) {
                    // this can be class loading errors or actually ServiceConfigurationError
                    // both hasNext() and next() can fail; the underlying iterator continues with the next entry
                    LOG.debug("Failed to load next entry", t);
                    addUnavailable(t);
                    if (++failures >= MAX_CONSECUTIVE_FAILURES) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public S next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            final S next = prefetched;
            prefetched = null;
            return next;
        }

    }

    private void addUnavailable(final Throwable failure) {
        String text = "Unidentified provider";
        Throwable cause = failure;
        if (failure instanceof ServiceConfigurationError) {
            // the message is "<service type>: Provider <class> not found | could not be instantiated"
            final String prefix = serviceType.getName() + ": ";
            final String message = String.valueOf(failure.getMessage());
            text = message.startsWith(prefix) ? message.substring(prefix.length()) : message;
            cause = failure.getCause();
        }
        final Throwable root = rootCause(cause);
        unavailable.add(root == null ? text : text + " - " + root.getClass().getSimpleName()
            + (root.getMessage() == null ? "" : ": " + root.getMessage()));
    }

    private static Throwable rootCause(final Throwable throwable) {
        Throwable root = throwable;
        for (int i = 0; root != null && root.getCause() != null && i < MAX_CAUSE_DEPTH; i++) {
            root = root.getCause();
        }
        return root;
    }

    /**
     * Constructor that initializes this instance with the given SPI interface.
     * @param serviceInterface The interface (or class) for that services should be looked up.
     */
    public ServiceLoaderSupport(final Class<S> serviceInterface) {
        serviceType = serviceInterface;
        serviceLoader = ServiceLoader.load(serviceInterface);
        debugLog();
    }

    private void debugLog() {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        final Collection<String> list = stream().map(this::getDebugLabel).collect(Collectors.toList());
        LOG.debug("{} available instance for {}: {}", list.size(), serviceType, list);

    }

    private String getDebugLabel(final S s) {
        Class<?> c = s.getClass();
        while (c != null && c != Object.class) {
            try {
                s.getClass().getDeclaredMethod("toString");
                return s.toString();
            } catch (final Exception e) {
                // not found or other error
            }
            c = c.getSuperclass();
        }
        return s.getClass().getName();
    }

    /**
     * Returns a {@link Iterator} over all found and accessible instances.
     * @return A {@link Iterator} over all found and accessible instances.
     */
    @Override
    public Iterator<S> iterator() {
        return new SaveIterator(serviceLoader.iterator());
    }

    /**
     * Returns a {@link Stream} over all found and accessible instances.
     * @return A {@link Stream} over all found and accessible instances.
     */
    public Stream<S> stream() {
        return StreamSupport.stream(spliterator(), false).filter(Objects::nonNull);
    }

    /**
     * Returns whether implementations have been encountered that could not be loaded. Implementations that cannot
     * be loaded are skipped silently, so this only covers the part of the lookup that has been done so far.
     * @return <code>true</code> if at least one implementation could not be loaded.
     */
    public boolean hasUnavailable() {
        return !unavailable.isEmpty();
    }

    /**
     * Describes which implementations are available (can be used) and which implementations have been found but
     * could not be loaded, and why. The causes are given by their type and message only, without a stack trace.
     * <br>
     * Implementations that cannot be loaded are skipped silently when looking up. This description is intended for
     * being logged when a lookup fails, so that it is visible whether the wanted implementation does not exist, or
     * could not be loaded. Since failing implementations are encountered only when iterating, call this
     * <em>after</em> the lookup that failed.
     * @param nameOf Provides the name of an available implementation.
     * @return A multi-line description.
     */
    public String describeAvailability(final Function<? super S, String> nameOf) {
        final String available = stream().map(nameOf).filter(Objects::nonNull).sorted()
            .collect(Collectors.joining(", ", "[", "]"));
        final List<String> failed;
        synchronized (unavailable) {
            failed = new ArrayList<>(unavailable);
        }
        final StringBuilder description = new StringBuilder("Available: ").append(available).append(NEW_LINE);
        if (failed.isEmpty()) {
            description.append("Unavailable: none");
        } else {
            description.append("Unavailable (could not be loaded):");
            failed.forEach(f -> description.append(NEW_LINE).append("  - ").append(f));
        }
        return description.toString();
    }

    /**
     * The interface type given on initialization.
     * @return The service type being looked up by this instance
     */
    public Class<S> getServiceType() {
        return serviceType;
    }

    /**
     * Returns a {@link Collection} of all found and accessible instances.
     * @return A {@link Collection} of all found and accessible instances.
     */
    public Collection<S> getAll() {
        return stream().collect(Collectors.toList());
    }

    /**
     * Tries to find an instance using the given filter.
     * @param filter The filter used for matching.
     * @return The found instance or <code>null</code>.
     */
    public S find(final Predicate<S> filter) {
        final S found = stream().filter(filter).findAny().orElse(null);
        if (LOG.isDebugEnabled()) {
            if (found != null) {
                LOG.debug("Found matching instance '{}' for service type {}", getDebugLabel(found), serviceType);
            } else {
                LOG.debug("Did not find a matching instance for service type {}", serviceType);
            }
        }
        return found;
    }

    /**
     * Tries to find an instance by class name (simple, or fully qualified).
     * @param className The class name to search for (simple, or fully qualified).
     * @return The found instance or <code>null</code>.
     */
    public S findByClassName(final String className) {
        return find(s -> s.getClass().getName().equals(className) || s.getClass().getSimpleName().equals(className));
    }

    /**
     * Tries to find an instance that is of the given type.
     * @param <T> The type of the instance to return.
     * @param type The type of the instance to return.
     * @return The found instance or <code>null</code>.
     */
    @SuppressWarnings("unchecked")
    public <T extends S> T findByClass(final Class<T> type) {
        return (T) find(type::isInstance);
    }

}
