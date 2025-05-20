/*
 * Copyright (c) 2025 Integration Matters GmbH
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

import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper that wraps an object for execution functionality on it via reflection.<br>
 * Though using reflection, this implementation does not break accessibility to the target object. I.e., the
 * functionality here can only be used to call functions on the target object that are directly accessible
 * (<code>public</code>).
 *
 * @author cwinkler
 *
 */
public class ReflectionWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectionWrapper.class);

    private static class Argument {
        private final Class<?> type;
        private final Object value;

        private Argument(final Class<?> type, final Object value) {
            this.type = type;
            this.value = value;
        }
    }

    /**
     * Builder for creating argument lists.
     */
    public static class ArgsBuilder {

        private final List<Argument> args = new ArrayList<>();

        private ArgsBuilder() {
            // use static factory
        }

        /**
         * Adds a (non-primitive) object to the argument list.
         * @param obj The non-primitive value to add. For primitives, use {@link #addPrimitive(Object)}.
         * For <code>null</code> use {@link #addNull(Class)}.
         * @return This builder for chaining.
         */
        public ArgsBuilder addObject(final Object obj) {
            args.add(new Argument(obj.getClass(), obj));
            // this does not work for primitives, because the wrapped type would be used which is not the same
            // when looking up a method with an arguments list
            return this;
        }

        /**
         * Adds a (non-primitive) object to the argument list. This is actually the most detailed method and can also
         * handle the <code>null</code> and primitive cases,, e.g., <code>addObject(null, Integer.class)</code> or
         * <code>addObject(new Integer(5), int.class)</code>
         * @param <T> The type of the given object.
         * @param obj The non-primitive value to add. For primitives, use {@link #addPrimitive(Object)}.
         * For <code>null</code> use {@link #addNull(Class)}.
         * @param asType The type expected by the method that is assignable from the object's type.
         * @return This builder for chaining.
         */
        public <T> ArgsBuilder addObject(final T obj, final Class<? super T> asType) {
            args.add(new Argument(Objects.requireNonNull(asType), obj));
            return this;
        }

        /**
         * Adds a primitive value to the argument list.
         * @param obj The primitive value to add. For non-primitives, use {@link #addObject(Object)}.
         * For <code>null</code> use {@link #addNull(Class)}.
         * @return This builder for chaining.
         */
        public ArgsBuilder addPrimitive(final Object obj) {
            args.add(new Argument(MethodType.methodType(obj.getClass()).unwrap().returnType(), obj));
            // this actually works also for non primitives but not for the wrapped types like Integer, or Long
            // so, it's better to keep this separate for primitives only
            return this;
        }

        /**
         * Adds <code>null</code> to the argument list.
         * @param type The exact type of the argument being set to <code>null</code>.
         * @return This builder for chaining.
         */
        public ArgsBuilder addNull(final Class<?> type) {
            args.add(new Argument(Objects.requireNonNull(type), null));
            return this;
        }

        private Entry<Class<?>[], Object[]> build() {
            final Class<?>[] types = new Class<?>[args.size()];
            final Object[] vals = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) {
                final Argument arg = args.get(i);
                types[i] = arg.type;
                vals[i] = arg.value;
            }
            LOG.trace("Build args: {}", Arrays.asList(types));
            return Map.entry(types, vals);
        }

    }

    private final Object target;

    /**
     * Returns a new {@link ArgsBuilder} instance for building an argument list.
     * @return An arguemnts builder.
     */
    public static ArgsBuilder argsBuilder() {
        return new ArgsBuilder();
    }

    /**
     * Constructor taking the object to modify as argument. The more common case is using one of the reflection
     * constructors that instantiate the actual object via reflection.
     * @param target Required. The object to be modified by this instance.
     */
    public ReflectionWrapper(final Object target) {
        this.target = Objects.requireNonNull(target);
    }

    /**
     * Same as {@link ReflectionWrapper#ReflectionWrapper(String, ClassLoader, ArgsBuilder)} using the current
     * context classloader.
     * @param className The full qualified name of the class for the object to create.
     * @param constructorArgs The argument list for the constructor to be called for creating the instance.
     * Set to <code>null</code> for using the default no-args constructors.
     * @throws ReflectiveOperationException On any error that occurred when creating the instance.
     */
    public ReflectionWrapper(final String className, final ArgsBuilder constructorArgs)
            throws ReflectiveOperationException {
        this(className, null, constructorArgs);
    }

    /**
     * Constructor using reflection for creating the object to modify. The created instance can be accessed
     * by {@link #getTarget()}.
     * @param className The full qualified name of the class for the object to create.
     * @param classLoader Optional. The {@link ClassLoader} to use for looking up the class to instantiate.
     * Defaults to current {@link Thread#getContextClassLoader()}.
     * @param constructorArgs The argument list for the constructor to be called for creating the instance.
     * Set to <code>null</code> for using the default no-args constructors.
     * @throws ReflectiveOperationException On any error that occurred when creating the instance.
     */
    public ReflectionWrapper(final String className, final ClassLoader classLoader, final ArgsBuilder constructorArgs)
            throws ReflectiveOperationException {

        final ClassLoader cl = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
        final Entry<Class<?>[], Object[]> args = constructorArgs == null ? null : constructorArgs.build();
        final Class<?> clazz = cl.loadClass(className);
        final Constructor<?> constructor = clazz.getConstructor(args == null ? null : args.getKey());
        LOG.debug("Creating new instance with {} from {}", constructor, cl);
        target = constructor.newInstance(args == null ? new Object[0] : args.getValue());

    }

    /**
     * Invokes a setter (a void method taking a single argument) on this instance's target object with <code>null</code>.
     * as argument.
     * Use {@link #setPrimitive(String, Object)} or {@link #setObject(String, Object)} for setting non-null values.
     * @param method The name of the setter method to call.
     * @param nullType The exact type of the setter's argument which is set to <code>null</code>.
     * @return This instance for chaining.
     * @throws ReflectiveOperationException On any error that occurred when creating the instance.
     */

    public ReflectionWrapper setNull(final String method, final Class<?> nullType) throws ReflectiveOperationException {
        invoke(method, argsBuilder().addNull(nullType));
        return this;
    }

    /**
     * Invokes a primitive setter (a void method taking a single primitive argument) on this instance's target object.
     * Use {@link #setObject(String, Object)} for setting a non-primitive value, or {@link #setNull(String, Class)}
     * for setting a <code>null</code> value.
     * @param method The name of the setter method to call.
     * @param primitive The primitive value to set.
     * @return This instance for chaining.
     * @throws ReflectiveOperationException On any error that occurred when creating the instance.
     */
    public ReflectionWrapper setPrimitive(final String method, final Object primitive)
            throws ReflectiveOperationException {
        invoke(method, argsBuilder().addPrimitive(primitive));
        return this;
    }

    /**
     * Invokes a setter (a void method taking a single argument) on this instance's target object.
     * Use {@link #setPrimitive(String, Object)} for setting a primitive value, or {@link #setNull(String, Class)}
     * for setting a <code>null</code> value.
     * @param method The name of the setter method to call.
     * @param object The value to set.
     * @return This instance for chaining.
     * @throws ReflectiveOperationException On any error that occurred when creating the instance.
     */
    public ReflectionWrapper setObject(final String method, final Object object)
            throws ReflectiveOperationException {
        invoke(method, argsBuilder().addObject(object));
        return this;
    }

    /**
     * Setter for setting an object whose's type extends the actual setter's argument type, i.e., the given
     * object's type is not exactly the one defined by the setter. This method is actually the most detailed one
     * and can also handle the <code>null</code> and primitive alternatives, e.g.,
     * <code>setObject("method", null, Integer.class)</code> or <code>setObject("method", new Integer(5), int.class)</code>
     * @param <T> The given object's type
     * @param method The name of the setter method to call.
     * @param object The value to set.
     * @param asType The type expected by the method that is assignable from the object's type.
     * @return This instance for chaining.
     * @throws ReflectiveOperationException On any error that occurred when creating the instance.
     */
    public <T> ReflectionWrapper setObject(final String method, final T object, final Class<? super T> asType)
            throws ReflectiveOperationException {
        invoke(method, argsBuilder().addObject(object, asType));
        return this;
    }

    /**
     * Invokes a method on this instance's target object and returns its value, if any.
     * @param method The name of the method to invoke.
     * @param callArgs The list of arguments for the method to invoke.
     * @return The method's return value if any.
     * @throws ReflectiveOperationException On any error that occurred on invocation.
     */
    public Object invoke(final String method, final ArgsBuilder callArgs) throws ReflectiveOperationException {
        final Entry<Class<?>[], Object[]> args = callArgs.build();
        final Method m = target.getClass().getMethod(method, args.getKey());
        LOG.debug("Invoking: {}", m);
        return m.invoke(target, args.getValue());
    }

    /**
     * Returns this instance's target object.
     * @return The object given or constructed on initialization.
     */
    public Object getTarget() {
        return target;
    }
}
