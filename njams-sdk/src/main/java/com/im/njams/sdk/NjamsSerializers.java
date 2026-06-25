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
package com.im.njams.sdk;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.serializer.Serializer;
import com.im.njams.sdk.serializer.SerializerResult;
import com.im.njams.sdk.serializer.StringSerializer;

/**
 * Owns the {@link Serializer} registry of an {@link Njams} client and serializes
 * arbitrary objects to strings for activity data. Obtain via {@code njams.serializers()}.
 */
public class NjamsSerializers {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsSerializers.class);

    private static final Serializer<Object> DEFAULT_SERIALIZER = new StringSerializer<>();
    private static final Serializer<Object> NO_SERIALIZER = (o, sizeLimit) -> null;

    private final HashMap<Class<?>, Serializer<?>> serializers = new HashMap<>();
    private final HashMap<Class<?>, Serializer<?>> cachedSerializers = new HashMap<>();

    NjamsSerializers() {
        // created by Njams only
    }

    /**
     * Adds a {@link Serializer} for serializing a given class. <br>
     * Uses {@link #serialize(java.lang.Object) } to serialize instances of this
     * class with the registered serializer. If a serializer is already
     * registered, it will be replaced with the new serializer.
     *
     * @param <T>        Type that the given instance serializes
     * @param key        Class for which the serializer should be registered
     * @param serializer A serializer that can serialize instances of class key
     *                   to strings.
     * @return If a serializer for the same type was already registered before,
     * the former registered serializer is returned. Otherwise <code>null</code> is returned.
     */
    public <T> Serializer<T> add(final Class<T> key, final Serializer<? super T> serializer) {
        synchronized (cachedSerializers) {
            if (key != null && serializer != null) {
                cachedSerializers.clear();
                return (Serializer) serializers.put(key, serializer);
            }
            return null;
        }
    }

    /**
     * Removes the serialier with the given class key. If not serializer is
     * registered yet, <b>null</b> will be returned.
     *
     * @param <T> type of the class
     * @param key a class
     * @return Registered serializer or <b>null</b>
     */
    public <T> Serializer<T> remove(final Class<T> key) {
        synchronized (cachedSerializers) {
            if (key != null) {
                cachedSerializers.clear();
                return (Serializer) serializers.remove(key);
            }
            return null;
        }
    }

    /**
     * Gets the serializer for exactly the given class key. If no serializer is
     * registered yet, <b>null</b> will be returned.
     * This implementation does not consider the class hierarchy. See also {@link #find(Class)}.
     *
     * @param <T> type of the class
     * @param key a class
     * @return Registered serializer or <b>null</b>
     * @see #find(Class)
     */
    public <T> Serializer<T> get(final Class<T> key) {
        if (key != null) {
            return (Serializer) serializers.get(key);
        }
        return null;
    }

    /**
     * Serializes a given object using {@link #find(java.lang.Class)} with no effective
     * size limit.
     *
     * @param <T> type of the class
     * @param t   Object to be serialized
     * @return a string representation of the object, or {@code null} if {@code t} is {@code null},
     *         or {@code ""} when the serializer threw
     */
    public <T> String serialize(final T t) {
        final SerializerResult result = serialize(t, Integer.MAX_VALUE);
        return result == null ? null : result.value();
    }

    /**
     * Serializes a given object using {@link #find(java.lang.Class)}, passing
     * {@code sizeLimit} through to the resolved {@link Serializer}, and reporting whether the
     * output was truncated.
     *
     * <p>The value may slightly exceed {@code sizeLimit} due to serializer-specific buffering.
     * {@code sizeLimit <= 0} or {@link Integer#MAX_VALUE} mean "no limit" (never truncated).</p>
     *
     * @param <T>       type of the class
     * @param t         Object to be serialized
     * @param sizeLimit Approximate maximum length of the produced string
     * @return the serialized value and its truncation flag, {@code null} if {@code t} is
     *         {@code null}, or an empty, non-truncated result when the serializer threw
     */
    public <T> SerializerResult serialize(final T t, final int sizeLimit) {
        if (t == null) {
            return null;
        }
        final Class<? super T> clazz = (Class) t.getClass();
        synchronized (cachedSerializers) {
            Serializer<? super T> serializer = this.find(clazz);
            if (serializer == null) {
                serializer = DEFAULT_SERIALIZER;
                cachedSerializers.put(clazz, serializer);
            }
            try {
                return serializer.serialize(t, sizeLimit);
            } catch (final Exception ex) {
                LOG.error("could not serialize object " + t, ex);
                return new SerializerResult("", false);
            }
        }
    }

    /**
     * Gets the serializer with the given class key. If no serializer is
     * registered yet, the superclass hierarchy will be checked recursively. If
     * neither the class nor any superclass is registered, the interface
     * hierarchy will be checked recursively. if no (super) interface is
     * registered, <b>null</b> will be returned.
     *
     * @param <T>   Type of the class
     * @param clazz Class for which a serializer will be searched.
     * @return Serializer or <b>null</b>.
     */
    public <T> Serializer<? super T> find(final Class<T> clazz) {
        Serializer<? super T> serializer = get(clazz);
        if (serializer == null) {
            Serializer<?> cached = cachedSerializers.get(clazz);
            if (cached == NO_SERIALIZER) {
                return null;
            }
            if (cached != null) {
                return (Serializer) cached;
            }
            final Class<? super T> superclass = clazz.getSuperclass();
            if (superclass != null) {
                serializer = find(superclass);
            }
        }
        if (serializer == null) {
            final Class<?>[] interfaces = clazz.getInterfaces();
            for (int i = 0; i < interfaces.length && serializer == null; i++) {
                final Class<? super T> anInterface = (Class) interfaces[i];
                serializer = find(anInterface);
            }
        }
        if (serializer != null) {
            if (!cachedSerializers.containsKey(clazz)) {
                cachedSerializers.put(clazz, serializer);
            }
        } else {
            cachedSerializers.put(clazz, NO_SERIALIZER);
        }
        return serializer;
    }
}
