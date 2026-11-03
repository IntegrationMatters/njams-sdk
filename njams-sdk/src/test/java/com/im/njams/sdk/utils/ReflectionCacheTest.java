package com.im.njams.sdk.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.log4j.Level;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.utils.hidden.HiddenApi;
import com.im.njams.sdk.utils.hidden.HiddenProviders;

public class ReflectionCacheTest {

    public static class Probe {
        public String value() {
            return "probe";
        }
    }

    private CapturingLogAppender log;

    @Before
    public void captureLog() {
        log = CapturingLogAppender.attach(ReflectionCache.class);
    }

    @After
    public void restoreLog() {
        log.detach();
    }

    // The cache and its one-shot failure flags are keyed by class and live for the JVM, so every test that observes
    // them subclasses Probe inline: one anonymous class per source location, and therefore one cache entry per test.
    // A shared helper would not do — all its calls return instances of the same anonymous class.

    @Test
    public void require_resolvesAPublicMethod() throws Exception {
        Probe probe = new Probe() {
        };
        Method method = ReflectionCache.require(probe.getClass(), "value");
        assertEquals("probe", method.invoke(probe));
    }

    @Test
    public void require_throwsForAnAbsentMethod() {
        Class<?> type = new Probe() {
        }.getClass();
        NoSuchMethodException e =
            assertThrows(NoSuchMethodException.class, () -> ReflectionCache.require(type, "absent"));
        assertTrue(e.getMessage().contains(type.getName()));
        assertTrue(e.getMessage().contains("absent()"));
    }

    @Test
    public void require_returnsTheSameInstanceOnRepeatedLookups() throws Exception {
        Class<?> type = new Probe() {
        }.getClass();
        assertSame(ReflectionCache.require(type, "value"), ReflectionCache.require(type, "value"));
    }

    @Test
    public void resolution_cachesTheAbsentCaseAsWell() {
        Class<?> type = new Probe() {
        }.getClass();
        assertFalse(ReflectionCache.isResolved(type, false, "alsoAbsent"));
        assertThrows(NoSuchMethodException.class, () -> ReflectionCache.require(type, "alsoAbsent"));
        assertTrue(ReflectionCache.isResolved(type, false, "alsoAbsent"));
        assertThrows(NoSuchMethodException.class, () -> ReflectionCache.require(type, "alsoAbsent"));
    }

    @Test
    public void resolution_distinguishesParameterTypes() throws Exception {
        assertNotNull(ReflectionCache.require(String.class, "indexOf", String.class));
        assertThrows(NoSuchMethodException.class,
            () -> ReflectionCache.require(String.class, "indexOf", StringBuilder.class));
    }

    @Test
    public void resolution_prefersThePublicInterfaceOverTheNonPublicDeclaringClass() throws Exception {
        Object provider = HiddenProviders.withPublicInterface();
        Method method = ReflectionCache.require(provider.getClass(), "value");
        assertSame(HiddenApi.class, method.getDeclaringClass());
        assertEquals("via-interface", method.invoke(provider));
    }

    @Test
    public void require_doesNotEscalateAccessibilityWithoutAPublicInterface() throws Exception {
        Object provider = HiddenProviders.withoutPublicInterface();
        Method method = ReflectionCache.require(provider.getClass(), "value");
        assertThrows(IllegalAccessException.class, () -> method.invoke(provider));
    }

    @Test
    public void findAccessible_makesANonPublicDeclaringClassInvocable() throws Exception {
        Object provider = HiddenProviders.withoutPublicInterface();
        Method method = ReflectionCache.findAccessible(provider.getClass(), "value");
        assertNotNull(method);
        assertEquals("direct", method.invoke(provider));
    }

    @Test
    public void findAccessible_returnsNullForAnAbsentMethod() {
        assertNull(ReflectionCache.findAccessible(new Probe() {
        }.getClass(), "absent"));
    }

    @Test
    public void reportFailure_logsTheCauseNotTheReflectionWrapper() {
        ReflectionCache.reportFailure(new Probe() {
        }, "do something", new InvocationTargetException(new IllegalStateException("provider failure")));
        assertEquals(1, log.events(Level.WARN).size());
        Throwable logged = log.events(Level.WARN).get(0).getThrowableInformation().getThrowable();
        assertEquals("provider failure", logged.getMessage());
        assertFalse(logged instanceof InvocationTargetException);
    }

    @Test
    public void reportFailure_isLoggedAtWarnOncePerClassThenAtDebugWithoutStackTrace() {
        Object target = new Probe() {
        };
        for (int i = 0; i < 3; i++) {
            ReflectionCache.reportFailure(target, "do something", new IllegalStateException("provider failure"));
        }
        assertEquals(1, log.events(Level.WARN).size());
        assertEquals(2, log.events(Level.DEBUG).size());
        assertNull(log.events(Level.DEBUG).get(0).getThrowableInformation());
    }
}
