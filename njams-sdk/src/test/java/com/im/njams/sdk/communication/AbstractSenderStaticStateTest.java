package com.im.njams.sdk.communication;

import static org.junit.Assert.assertFalse;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

/**
 * Guards that AbstractSender keeps NO JVM-global (static) connection lifecycle state — that state must live in the
 * per-group ConnectionCoordinator so unrelated Njams instances do not couple. RED while the statics still exist.
 */
public class AbstractSenderStaticStateTest {

    @Test
    public void abstractSenderDeclaresNoStaticConnectionState() {
        Set<String> staticFieldNames = Arrays.stream(AbstractSender.class.getDeclaredFields())
            .filter(f -> Modifier.isStatic(f.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());
        assertFalse("AbstractSender must not keep 'hasConnected' as static (it belongs to ConnectionCoordinator); "
            + "static fields found: " + staticFieldNames, staticFieldNames.contains("hasConnected"));
        assertFalse("AbstractSender must not keep 'connecting' as static (it belongs to ConnectionCoordinator); "
            + "static fields found: " + staticFieldNames, staticFieldNames.contains("connecting"));
    }
}
