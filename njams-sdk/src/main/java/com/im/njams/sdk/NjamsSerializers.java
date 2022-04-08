package com.im.njams.sdk;

import com.im.njams.sdk.serializer.Serializer;
import com.im.njams.sdk.serializer.StringSerializer;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class NjamsSerializers {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsSerializers.class);

    private static final Serializer<Object> DEFAULT_SERIALIZER = new StringSerializer<>();
    private static final Serializer<Object> NO_SERIALIZER = o -> null;

    // serializers
    private final HashMap<Class<?>, Serializer<?>> cachedSerializers = new HashMap<>();
    private final HashMap<Class<?>, Serializer<?>> serializers = new HashMap<>();

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
    public <T> Serializer<T> add(Class<T> key, Serializer<? super T> serializer) {
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
    public <T> Serializer<T> remove(Class<T> key) {
        synchronized (cachedSerializers) {
            if (key != null) {
                cachedSerializers.clear();
                return (Serializer) serializers.remove(key);
            }
            return null;
        }
    }

    /**
     * Gets the serialier with the given class key. If not serializer is
     * registered yet, <b>null</b> will be returned.
     *
     * @param <T> type of the class
     * @param key a class
     * @return Registered serializer or <b>null</b>
     */
    public <T> Serializer<T> get(Class<T> key) {
        if (key != null) {
            return (Serializer) serializers.get(key);
        }
        return null;
    }

    /**
     * Serializes a given object using {@link #find(java.lang.Class) }
     *
     * @param <T> type of the class
     * @param t   Object to be serialied.
     * @return a string representation of the object.
     */
    public <T> String serialize(T t) {
        if (t == null) {
            return null;
        }

        final Class<? super T> clazz = (Class) t.getClass();
        synchronized (cachedSerializers) {

            // search serializer
            Serializer<? super T> serializer = this.find(clazz);

            // user default serializer
            if (serializer == null) {
                serializer = DEFAULT_SERIALIZER;
                cachedSerializers.put(clazz, serializer);
            }

            try {
                return serializer.serialize(t);
            } catch (final Exception ex) {
                LOG.error("could not serialize object " + t, ex);
                return "";
            }
        }
    }

    /**
     * Gets the serialier with the given class key. If not serializer is
     * registered yet, the superclass hierarchy will be checked recursivly. If
     * neither the class nor any superclass if registered, the interface
     * hierarchy will be checked recursivly. if no (super) interface is
     * registed, <b>null</b> will be returned.
     *
     * @param <T>   Type of the class
     * @param clazz Class for which a serializer will be searched.
     * @return Serizalier of <b>null</b>.
     */
    public <T> Serializer<? super T> find(Class<T> clazz) {
        Serializer<? super T> serializer = get(clazz);
        if (serializer == null) {
            Serializer<?> cached = cachedSerializers.get(clazz);
            if (cached == NO_SERIALIZER) {
                return null;
            } else if (cached != null) {
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
