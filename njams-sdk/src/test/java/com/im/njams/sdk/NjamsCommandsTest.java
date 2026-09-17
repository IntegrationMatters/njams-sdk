package com.im.njams.sdk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.InstructionListener;

/**
 * Unit tests for {@link NjamsCommands}: the {@link InstructionListener} registry. The collaborator
 * arguments are not needed for the registry behaviour, so they are left null here.
 */
public class NjamsCommandsTest {

    private NjamsCommands commands;

    @Before
    public void setUp() {
        commands = new NjamsCommands(null, null, null, null);
    }

    @Test
    public void listIsEmptyInitially() {
        assertTrue(commands.list().isEmpty());
    }

    @Test
    public void addAndRemoveListener() {
        InstructionListener listener = mock(InstructionListener.class);
        commands.add(listener);
        assertTrue(commands.list().contains(listener));

        commands.remove(listener);
        assertFalse(commands.list().contains(listener));
    }

    @Test
    public void listReturnsDetachedCopy() {
        InstructionListener listener = mock(InstructionListener.class);
        commands.add(listener);
        // mutating the returned list must not affect the registry
        commands.list().clear();
        assertTrue(commands.list().contains(listener));
    }

    @Test
    public void clearRemovesAllListeners() {
        commands.add(mock(InstructionListener.class));
        commands.add(mock(InstructionListener.class));
        commands.clear();
        assertTrue(commands.list().isEmpty());
    }
}
