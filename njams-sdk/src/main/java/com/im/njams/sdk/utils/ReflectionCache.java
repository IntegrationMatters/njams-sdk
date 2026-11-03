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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared method resolution for the reflective accessors in this package. Resolved methods are cached per class,
 * including the ones that turned out to be absent, and the first invocation failure per class is reported once.
 * <p>
 * Not part of any API: package-private infrastructure behind {@link ReflectionWrapper} and {@link JmsMessageAccess}.
 */
final class ReflectionCache {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectionCache.class);

    private static final ClassValue<ClassEntry> ENTRIES = new ClassValue<ClassEntry>() {
        @Override
        protected ClassEntry computeValue(Class<?> type) {
            return new ClassEntry(type);
        }
    };

    /** Everything cached for one class: its resolved methods, its public interfaces, and its failure flag. */
    private static final class ClassEntry {
        private final Class<?> type;
        private final Map<Signature, Optional<Method>> methods = new ConcurrentHashMap<>();
        private final AtomicBoolean failureReported = new AtomicBoolean();
        private volatile Collection<Class<?>> publicInterfaces;

        private ClassEntry(Class<?> type) {
            this.type = type;
        }

        private Method resolve(Signature signature) {
            return methods.computeIfAbsent(signature, this::lookup).orElse(null);
        }

        private Optional<Method> lookup(Signature signature) {
            for (Class<?> candidate : publicInterfaces()) {
                try {
                    return Optional.of(candidate.getMethod(signature.name, signature.parameterTypes));
                } catch (NoSuchMethodException e) {
                    // not declared here; try the next interface
                }
            }
            final Method method;
            try {
                method = type.getMethod(signature.name, signature.parameterTypes);
            } catch (NoSuchMethodException | RuntimeException e) {
                // a method that cannot be looked up counts as absent, so that callers fall back instead of failing
                return Optional.empty();
            }
            if (signature.forceAccessible) {
                try {
                    method.setAccessible(true);
                } catch (RuntimeException e) {
                    // e.g. a provider in a named module that does not open its packages
                    return Optional.empty();
                }
            }
            return Optional.of(method);
        }

        private Collection<Class<?>> publicInterfaces() {
            Collection<Class<?>> result = publicInterfaces;
            if (result == null) {
                final Collection<Class<?>> collected = new LinkedHashSet<>();
                collectInterfaces(type, collected);
                collected.removeIf(iface -> !Modifier.isPublic(iface.getModifiers()));
                publicInterfaces = collected;
                result = collected;
            }
            return result;
        }

        private static void collectInterfaces(Class<?> type, Collection<Class<?>> result) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Class<?> iface : current.getInterfaces()) {
                    if (result.add(iface)) {
                        collectInterfaces(iface, result);
                    }
                }
            }
        }
    }

    /** A method name plus the exact parameter types it is looked up with, and whether accessibility may be forced. */
    private static final class Signature {
        private final String name;
        private final Class<?>[] parameterTypes;
        private final boolean forceAccessible;
        private final int hash;

        private Signature(String name, boolean forceAccessible, Class<?>[] parameterTypes) {
            this.name = name;
            this.forceAccessible = forceAccessible;
            this.parameterTypes = parameterTypes == null ? new Class<?>[0] : parameterTypes;
            hash = 31 * (31 * name.hashCode() + Arrays.hashCode(this.parameterTypes)) + (forceAccessible ? 1 : 0);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Signature)) {
                return false;
            }
            final Signature other = (Signature) obj;
            return forceAccessible == other.forceAccessible && name.equals(other.name)
                && Arrays.equals(parameterTypes, other.parameterTypes);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            final StringJoiner joiner = new StringJoiner(", ", name + "(", ")");
            for (Class<?> parameterType : parameterTypes) {
                joiner.add(parameterType.getName());
            }
            return joiner.toString();
        }
    }

    private ReflectionCache() {
        // static access only
    }

    /**
     * Resolves a method, making it accessible when it could only be resolved against a class that is not public.
     *
     * @param type the class to resolve against
     * @param name the method name
     * @param parameterTypes the exact parameter types
     * @return the resolved method, or <code>null</code> if the class does not declare it, or if it could not be made
     * accessible
     */
    static Method findAccessible(Class<?> type, String name, Class<?>... parameterTypes) {
        return ENTRIES.get(type).resolve(new Signature(name, true, parameterTypes));
    }

    /**
     * Resolves a method without escalating accessibility, for callers that report an absent method to their own
     * caller.
     *
     * @param type the class to resolve against
     * @param name the method name
     * @param parameterTypes the exact parameter types
     * @return the resolved method, never <code>null</code>
     * @throws NoSuchMethodException if the class does not declare the method
     */
    static Method require(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        final Signature signature = new Signature(name, false, parameterTypes);
        final Method method = ENTRIES.get(type).resolve(signature);
        if (method == null) {
            throw new NoSuchMethodException(type.getName() + "." + signature);
        }
        return method;
    }

    // package-private so that the test can verify that a resolution is cached, including the absent case
    static boolean isResolved(Class<?> type, boolean forceAccessible, String name, Class<?>... parameterTypes) {
        return ENTRIES.get(type).methods.containsKey(new Signature(name, forceAccessible, parameterTypes));
    }

    /**
     * Reports a failed invocation once per target class at WARN, including the stack trace; repeats are logged at
     * DEBUG without it. Callers recover with a fallback value, so the failure itself is recoverable.
     *
     * @param target the object the failed call was made on
     * @param action what the caller was trying to do, for the log message
     * @param failure the failure, unwrapped if it is an {@link InvocationTargetException}
     */
    static void reportFailure(Object target, String action, Exception failure) {
        final Throwable cause = failure instanceof InvocationTargetException && failure.getCause() != null
            ? failure.getCause() : failure;
        final Class<?> type = target.getClass();
        if (ENTRIES.get(type).failureReported.compareAndSet(false, true)) {
            LOG.warn("Failed to {} on {}; using fallback. Further failures for this type are logged at debug level.",
                action, type.getName(), cause);
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("Failed to {} on {}: {}", action, type.getName(), cause.toString());
        }
    }
}
