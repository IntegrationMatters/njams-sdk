package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsCommands;
import com.im.njams.sdk.NjamsFeatures;
import com.im.njams.sdk.NjamsMetadata;
import com.im.njams.sdk.Path;

/**
 * Routing of instructions through {@link SharedReceiverSupport#onInstruction(Object, Instruction, boolean)}
 * (SDK-499).
 */
public class SharedReceiverSupportRoutingTest {

    private RecordingReceiver receiver;
    private SharedReceiverSupport<RecordingReceiver, Object> support;
    private Client first;
    private Client second;

    @Before
    public void setUp() {
        receiver = new RecordingReceiver();
        support = new SharedReceiverSupport<>(receiver);
        first = new Client("first");
        second = new Client("second");
        support.addNjams(first.njams);
        support.addNjams(second.njams);
    }

    @Test
    public void instructionIsRoutedByReceiverPath() {
        receiver.path = second.path;
        Instruction instruction = instruction("ping");

        support.onInstruction("msg", instruction, true);

        assertTrue(first.received.isEmpty());
        assertEquals(1, second.received.size());
        assertEquals(1, receiver.replies.size());
        assertSame(instruction, receiver.replies.get(0).instruction);
        assertEquals("second-session", receiver.replies.get(0).clientId);
        assertEquals("msg", receiver.replies.get(0).message);
    }

    @Test
    public void clientIdTakesPrecedenceOverPath() {
        receiver.path = second.path;
        receiver.clientId = "first-session";

        support.onInstruction("msg", instruction("ping"), true);

        assertEquals(1, first.received.size());
        assertTrue(second.received.isEmpty());
        assertEquals("first-session", receiver.replies.get(0).clientId);
    }

    @Test
    public void unknownClientIsAnsweredWithResultCode99WhenRequired() {
        receiver.path = Path.of("SDK4", "unknown");
        Instruction instruction = instruction("ping");

        support.onInstruction("msg", instruction, true);

        assertEquals(1, receiver.replies.size());
        assertNull(receiver.replies.get(0).clientId);
        assertEquals(99, instruction.getResponse().getResultCode());
        assertEquals("Client instance not found.", instruction.getResponse().getResultMessage());
    }

    @Test
    public void unknownClientIsIgnoredWhenNotRequired() {
        receiver.path = Path.of("SDK4", "unknown");

        support.onInstruction("msg", instruction("ping"), false);

        assertTrue(receiver.replies.isEmpty());
    }

    @Test
    public void unknownClientIdIsTreatedAsUnknownClient() {
        receiver.path = first.path;
        receiver.clientId = "other-session";

        support.onInstruction("msg", instruction("ping"), false);

        assertTrue(first.received.isEmpty());
        assertTrue(receiver.replies.isEmpty());
    }

    @Test
    public void missingReceiverPathIsIgnored() {
        receiver.path = null;

        support.onInstruction("msg", instruction("ping"), true);

        assertTrue(first.received.isEmpty());
        assertTrue(second.received.isEmpty());
        assertTrue(receiver.replies.isEmpty());
    }

    @Test
    public void instructionWithoutCommandGetsResultCode1() {
        receiver.path = first.path;
        Instruction withoutCommand = new Instruction();
        withoutCommand.setRequest(new Request());

        support.onInstruction("msg", withoutCommand, true);

        assertEquals(1, withoutCommand.getResponse().getResultCode());
        assertTrue(first.received.isEmpty());
        assertEquals(1, receiver.replies.size());
    }

    @Ignore("MSG-32: Command.getFromInstruction throws an NPE for an instruction without a request")
    @Test
    public void instructionWithoutRequestGetsResultCode1() {
        receiver.path = first.path;
        Instruction withoutRequest = new Instruction();

        support.onInstruction("msg", withoutRequest, true);

        assertEquals(1, withoutRequest.getResponse().getResultCode());
        assertTrue(first.received.isEmpty());
        assertEquals(1, receiver.replies.size());
    }

    @Test
    public void extendRequestResponseIsSentWithoutListeners() {
        receiver.path = first.path;
        Response rejection = new Response();
        rejection.setResultCode(7);
        receiver.extendResponse = rejection;
        Instruction instruction = instruction("ping");

        support.onInstruction("msg", instruction, true);

        assertTrue(first.received.isEmpty());
        assertSame(rejection, instruction.getResponse());
        assertEquals(1, receiver.replies.size());
    }

    @Test
    public void failingListenerDoesNotStopOthers() {
        receiver.path = first.path;
        first.listeners.add(0, i -> {
            throw new IllegalStateException("listener fails");
        });
        Instruction instruction = instruction("ping");

        support.onInstruction("msg", instruction, true);

        assertEquals(1, first.received.size());
        assertEquals(0, instruction.getResponse().getResultCode());
        assertEquals("handled", instruction.getResponse().getResultMessage());
    }

    @Test
    public void noListenerResponseIsDefaulted() {
        receiver.path = first.path;
        first.listeners.clear();
        Instruction instruction = instruction("ping");

        support.onInstruction("msg", instruction, true);

        assertEquals(1, instruction.getResponse().getResultCode());
        assertEquals("No InstructionListener for ping found", instruction.getResponse().getResultMessage());
        assertEquals(1, receiver.replies.size());
    }

    @Test
    public void getRequestHandlerIsSuppressedOutsideContainerMode() {
        receiver.path = first.path;
        when(first.njams.features().isContainerMode()).thenReturn(false);

        support.onInstruction("msg", instruction("GetRequestHandler"), true);

        assertTrue(first.received.isEmpty());
        assertTrue(receiver.replies.isEmpty());
    }

    @Test
    public void getAllNjamsInstancesListsRegisteredClients() {
        assertEquals(2, support.getAllNjamsInstances().size());
        assertFalse(support.removeNjams(first.njams));
        assertEquals(1, support.getAllNjamsInstances().size());
    }

    private static Instruction instruction(String command) {
        Request request = new Request();
        request.setCommand(command);
        Instruction instruction = new Instruction();
        instruction.setRequest(request);
        return instruction;
    }

    /** A mocked client whose single listener records instructions and answers them. */
    private static final class Client {
        private final Path path;
        private final Njams njams = mock(Njams.class);
        private final List<Instruction> received = new ArrayList<>();
        private final List<InstructionListener> listeners = new ArrayList<>();

        private Client(String name) {
            path = Path.of("SDK4", name);
            NjamsMetadata metadata = mock(NjamsMetadata.class);
            when(metadata.getClientPath()).thenReturn(path);
            when(metadata.getClientSessionId()).thenReturn(name + "-session");
            NjamsCommands commands = mock(NjamsCommands.class);
            when(commands.list()).thenReturn(listeners);
            NjamsFeatures features = mock(NjamsFeatures.class);
            when(features.isContainerMode()).thenReturn(true);
            when(njams.metadata()).thenReturn(metadata);
            when(njams.commands()).thenReturn(commands);
            when(njams.features()).thenReturn(features);
            listeners.add(instruction -> {
                received.add(instruction);
                instruction.setResponseResultCode(0);
                instruction.setResponseResultMessage("handled");
            });
        }
    }

    private static final class Reply {
        private final Object message;
        private final Instruction instruction;
        private final String clientId;

        private Reply(Object message, Instruction instruction, String clientId) {
            this.message = message;
            this.instruction = instruction;
            this.clientId = clientId;
        }
    }

    private static final class RecordingReceiver extends AbstractReceiver implements ShareableReceiver<Object> {
        private Path path;
        private String clientId;
        private Response extendResponse;
        private final List<Reply> replies = new ArrayList<>();

        @Override
        public String getName() {
            return "recording-shared-receiver";
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
        protected Response extendRequest(Request request) {
            return extendResponse;
        }

        @Override
        public boolean removeNjams(Njams njams) {
            throw new UnsupportedOperationException("use the support directly");
        }

        @Override
        public Path getReceiverPath(Object requestMessage, Instruction instruction) {
            return path;
        }

        @Override
        public String getClientId(Object requestMessage, Instruction instruction) {
            return clientId;
        }

        @Override
        public void sendReply(Object requestMessage, Instruction reply, String clientId) {
            replies.add(new Reply(requestMessage, reply, clientId));
        }
    }
}
