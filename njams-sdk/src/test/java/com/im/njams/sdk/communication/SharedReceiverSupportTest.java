package com.im.njams.sdk.communication;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;

/**
 * Unit tests for {@link SharedReceiverSupport#removeNjams(Njams)}'s usage-count-gated return value — the
 * mechanism {@link ShareableReceiver#removeNjams(Njams)} (changed {@code void} to {@code boolean} for SDK-375
 * Part 3, Task 5) exposes to {@code Njams.stop()} so it can decide whether the shared receiver was really
 * stopped (last registered instance) or merely decremented (other instances still use it).
 * <p>
 * Regression coverage for SDK-375 final-review finding #4: before this test, {@code removeNjams} had zero test
 * references anywhere in the repository.
 */
public class SharedReceiverSupportTest {

    private static Njams mockNjams(String... pathParts) {
        Njams njams = mock(Njams.class);
        when(njams.getClientPath()).thenReturn(Path.of(pathParts));
        return njams;
    }

    /** Minimal {@code AbstractReceiver & ShareableReceiver} satisfying {@link SharedReceiverSupport}'s bound. */
    private static class FakeShareableReceiver extends AbstractReceiver implements ShareableReceiver<Object> {
        @Override
        public String getName() {
            return "fake-shared-receiver";
        }

        @Override
        public void connect() {
            connectionStatus = ConnectionStatus.CONNECTED;
        }

        @Override
        public void stop() {
            connectionStatus = ConnectionStatus.DISCONNECTED;
        }

        @Override
        public boolean removeNjams(Njams njams) {
            throw new UnsupportedOperationException("not used directly in this test; call support.removeNjams()");
        }

        @Override
        public Path getReceiverPath(Object requestMessage, Instruction instruction) {
            return null;
        }

        @Override
        public String getClientId(Object requestMessage, Instruction instruction) {
            return null;
        }

        @Override
        public void sendReply(Object requestMessage, Instruction reply, String clientId) {
            // not used in this test
        }
    }

    @Test
    public void removeNjamsReturnsFalseWhileOtherInstancesStillUseTheSharedReceiver() {
        FakeShareableReceiver receiver = new FakeShareableReceiver();
        receiver.connect();
        SharedReceiverSupport<FakeShareableReceiver, Object> support = new SharedReceiverSupport<>(receiver);
        Njams first = mockNjams("test", "shared-a");
        Njams second = mockNjams("test", "shared-b");
        support.addNjams(first);
        support.addNjams(second);

        assertFalse("must not report 'really stopped' while a second instance still uses the shared receiver",
            support.removeNjams(first));
        assertTrue("the underlying receiver must stay connected for the remaining instance",
            receiver.isConnected());
    }

    @Test
    public void removeNjamsReturnsTrueAndStopsTheReceiverOnceTheLastInstanceIsRemoved() {
        FakeShareableReceiver receiver = new FakeShareableReceiver();
        receiver.connect();
        SharedReceiverSupport<FakeShareableReceiver, Object> support = new SharedReceiverSupport<>(receiver);
        Njams only = mockNjams("test", "shared-only");
        support.addNjams(only);

        assertTrue("must report 'really stopped' once the last registered instance is removed",
            support.removeNjams(only));
        assertTrue("receiver.stop() must actually run once the last instance is removed",
            receiver.isDisconnected());
    }

    @Test
    public void removeNjamsOfAnUnregisteredInstanceStillReportsReallyStoppedWhenNoneRemain() {
        // Defensive case: removing an instance that was never added still empties the (already-empty) map and
        // is treated as "really stopped" -- SharedReceiverSupport has no way to distinguish this from a normal
        // last-instance removal, and no caller relies on that distinction.
        FakeShareableReceiver receiver = new FakeShareableReceiver();
        SharedReceiverSupport<FakeShareableReceiver, Object> support = new SharedReceiverSupport<>(receiver);
        Njams neverAdded = mockNjams("test", "never-added");

        assertTrue(support.removeNjams(neverAdded));
    }
}
