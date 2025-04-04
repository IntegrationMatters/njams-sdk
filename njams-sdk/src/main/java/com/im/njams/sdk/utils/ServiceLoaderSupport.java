package com.im.njams.sdk.utils;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceLoader;
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
    private final Class<S> serviceType;
    private final ServiceLoader<S> serviceLoader;

    private static class SaveIterator<S> implements Iterator<S> {
        private final Iterator<S> it;

        private SaveIterator(final Iterator<S> originalIterator) {
            it = originalIterator;
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public S next() {
            try {
                return it.next();
            } catch (final Throwable t) {
                // this can be class loading errors or actually ServiceConfigurationError
                LOG.debug("Failed to load next entry", t);

            }
            return null;
        }

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
        return new SaveIterator<>(serviceLoader.iterator());
    }

    /**
     * Returns a {@link Stream} over all found and accessible instances.
     * @return A {@link Stream} over all found and accessible instances.
     */
    public Stream<S> stream() {
        return StreamSupport.stream(spliterator(), false).filter(Objects::nonNull);
    }

    /**
     * The interface type given on initialization.
     * @return
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
     * @param type The type of the instance to return.
     * @return The found instance or <code>null</code>.
     */
    @SuppressWarnings("unchecked")
    public <T extends S> T findByClass(final Class<T> type) {
        return (T) find(type::isInstance);
    }

}
