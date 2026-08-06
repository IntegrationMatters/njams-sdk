package com.im.njams.sdk.communication;

import static org.junit.Assert.assertFalse;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

/**
 * Guards that AbstractReceiver keeps NO JVM-global (static) connection lifecycle state — that state must live in
 * the per-group ConnectionCoordinator so unrelated Njams instances (and unrelated sender groups) do not couple.
 * RED while the statics still exist. Mirrors AbstractSenderStaticStateTest (Part 1).
 */
public class AbstractReceiverStaticStateTest {

    @Test
    public void abstractReceiverDeclaresNoStaticConnectionState() {
        Set<String> staticFieldNames = Arrays.stream(AbstractReceiver.class.getDeclaredFields())
            .filter(f -> Modifier.isStatic(f.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());
        assertFalse("AbstractReceiver must not keep 'hasConnected' as static (it belongs to ConnectionCoordinator); "
            + "static fields found: " + staticFieldNames, staticFieldNames.contains("hasConnected"));
        assertFalse("AbstractReceiver must not keep 'connecting' as static (it belongs to ConnectionCoordinator); "
            + "static fields found: " + staticFieldNames, staticFieldNames.contains("connecting"));
    }
}
