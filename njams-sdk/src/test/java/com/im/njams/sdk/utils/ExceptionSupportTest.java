package com.im.njams.sdk.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class ExceptionSupportTest {

    private static void voidMethod(AtomicInteger counter) {
        counter.incrementAndGet();
    }

    private static void throwingVoidMethod() {
        throw new IllegalStateException("expected");
    }

    private static String valueMethod() {
        return "value";
    }

    private static String throwingValueMethod() {
        throw new IllegalStateException("expected");
    }

    @Test
    public void runnableSwallowsRuntimeException() {
        ExceptionSupport.suppressException(() -> throwingVoidMethod());
    }

    @Test
    public void lambdaCallingVoidMethodResolvesToRunnable() {
        AtomicInteger counter = new AtomicInteger();
        ExceptionSupport.suppressException(() -> voidMethod(counter));
        assertEquals(1, counter.get());
    }

    @Test
    public void callableReturnsValue() {
        String result = ExceptionSupport.suppressException(() -> valueMethod());
        assertEquals("value", result);
    }

    @Test
    public void callableReturnsNullOnException() {
        String result = ExceptionSupport.suppressException(() -> throwingValueMethod());
        assertNull(result);
    }

    @Test
    public void optionalOfReturnsValue() {
        Optional<String> result = ExceptionSupport.optionalOf(() -> valueMethod());
        assertTrue(result.isPresent());
        assertEquals("value", result.get());
    }

    @Test
    public void optionalOfReturnsEmptyOnException() {
        assertFalse(ExceptionSupport.optionalOf(() -> throwingValueMethod()).isPresent());
    }

    @Test
    public void optionalOfReturnsEmptyOnCheckedException() {
        assertFalse(ExceptionSupport.optionalOf(() -> {
            throw new Exception("expected");
        }).isPresent());
    }

    @Test
    public void optionalOfReturnsEmptyForNullValue() {
        assertFalse(ExceptionSupport.optionalOf(() -> null).isPresent());
    }
}
