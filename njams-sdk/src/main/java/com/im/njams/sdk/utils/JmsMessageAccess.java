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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Reflective access to the parts of a JMS message the SDK needs outside the namespace-specific JMS code, so that
 * the same code serves {@code javax.jms} and {@code jakarta.jms} messages. It names no JMS type.
 * <p>
 * Internal SDK infrastructure: {@code public} only because {@code communication/fragments/} needs cross-package
 * access. Not an extension point; client code must not use it.
 */
public final class JmsMessageAccess {

    private static final ClassValue<Descriptor> DESCRIPTORS = new ClassValue<Descriptor>() {
        @Override
        protected Descriptor computeValue(Class<?> type) {
            return new Descriptor(type);
        }
    };

    /**
     * The accessors of one message class, resolved on first contact so that reading one is a field access. A
     * missing accessor is recorded as {@code null}.
     */
    private static final class Descriptor {
        private final Method getPropertyNames;
        private final Method getObjectProperty;
        private final Method setStringProperty;
        private final Method getText;

        private Descriptor(Class<?> type) {
            getPropertyNames = ReflectionCache.findAccessible(type, "getPropertyNames");
            getObjectProperty = ReflectionCache.findAccessible(type, "getObjectProperty", String.class);
            setStringProperty = ReflectionCache.findAccessible(type, "setStringProperty", String.class, String.class);
            getText = ReflectionCache.findAccessible(type, "getText");
        }
    }

    private JmsMessageAccess() {
        // static access only
    }

    /**
     * @param msg the candidate message
     * @return <code>true</code> if the given object looks like a JMS message (has property names)
     */
    public static boolean isMessage(Object msg) {
        return msg != null && descriptor(msg.getClass()).getPropertyNames != null;
    }

    /**
     * @param msg the candidate message
     * @return <code>true</code> if the given object looks like a JMS text message (has a text body)
     */
    public static boolean isTextMessage(Object msg) {
        return msg != null && descriptor(msg.getClass()).getText != null;
    }

    /**
     * @param msg the message
     * @return The names of the message's properties; never <code>null</code>, empty on any failure.
     */
    public static Collection<String> propertyNames(Object msg) {
        final Method method = msg == null ? null : descriptor(msg.getClass()).getPropertyNames;
        if (method == null) {
            return Collections.emptyList();
        }
        try {
            final Enumeration<?> names = (Enumeration<?>) method.invoke(msg);
            final Collection<String> result = new ArrayList<>();
            while (names != null && names.hasMoreElements()) {
                result.add(String.valueOf(names.nextElement()));
            }
            return result;
        } catch (Exception e) {
            ReflectionCache.reportFailure(msg, "read property names", e);
            return Collections.emptyList();
        }
    }

    /**
     * @param msg the message
     * @param key the property name
     * @return The property value, or <code>null</code> if absent or on any failure.
     */
    public static Object objectProperty(Object msg, String key) {
        final Method method = msg == null ? null : descriptor(msg.getClass()).getObjectProperty;
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(msg, key);
        } catch (Exception e) {
            ReflectionCache.reportFailure(msg, "read property '" + key + "'", e);
            return null;
        }
    }

    /**
     * @param msg the message
     * @return The text body, or <code>null</code> if the message has none or on any failure.
     */
    public static String text(Object msg) {
        final Method method = msg == null ? null : descriptor(msg.getClass()).getText;
        if (method == null) {
            return null;
        }
        try {
            return (String) method.invoke(msg);
        } catch (Exception e) {
            ReflectionCache.reportFailure(msg, "read text", e);
            return null;
        }
    }

    /**
     * Sets a string property; failures are logged and otherwise ignored.
     *
     * @param msg the message
     * @param key the property name
     * @param value the property value
     */
    public static void setStringProperty(Object msg, String key, String value) {
        final Method method = msg == null ? null : descriptor(msg.getClass()).setStringProperty;
        if (method == null) {
            return;
        }
        try {
            method.invoke(msg, key, value);
        } catch (Exception e) {
            ReflectionCache.reportFailure(msg, "set property '" + key + "'", e);
        }
    }

    private static Descriptor descriptor(Class<?> type) {
        return DESCRIPTORS.get(type);
    }

    // package-private so that the test can verify the one-shot probing
    static Object descriptorFor(Class<?> type) {
        return descriptor(type);
    }
}
