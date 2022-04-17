package com.im.njams.sdk;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class NjamsInstructionListenerAddedBeforeReceiverStartIT {

    private Response expectedResponse;
    private Settings settings;
    private InstructionListeningReceiverMock instructionListeningReceiverMock;
    private Njams njams;
    private Map<String, String> requestParameters;

    @Before
    public void setUp() throws Exception {
        requestParameters = new HashMap<>();
        expectedResponse = new Response();

        settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestReceiver.NAME);

        njams = new Njams(new Path("NjamsInstructionListenerAddedBeforeReceiverStartIT"), "SDK", settings);

        instructionListeningReceiverMock = new InstructionListeningReceiverMock();
        instructionListeningReceiverMock.addRequestParameters(requestParameters);
        instructionListeningReceiverMock.setNjamsReceiver(njams.getNjamsReceiver());

        TestReceiver.setReceiverMock(instructionListeningReceiverMock);
    }

    @Test
    public void instructionListenerFor_getLogLeve_isAddedBeforeRealReceiver_isStarted(){
        requestParameters.put("processPath", "Test");

        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Success");

        instructionListeningReceiverMock.forCommand(Command.GET_LOG_LEVEL).checkResponse(expectedResponse);

        njams.start();
    }

    @Test
    public void instructionListenerFor_ping_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Pong");

        instructionListeningReceiverMock.forCommand(Command.PING).checkResponse(expectedResponse);

        njams.start();
    }

    @Test
    public void instructionListenerFor_sendProjectMessage_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Successfully sent ProjectMessage via NjamsClient");

        instructionListeningReceiverMock.forCommand(Command.SEND_PROJECTMESSAGE).checkResponse(expectedResponse);

        njams.start();
    }

    private static class InstructionListeningReceiverMock extends AbstractReceiver {
        private Command commandToListenFor;
        private Response expectedResponse;
        private Response actualResponse;
        private Map<String, String> requestParameters;

        @Override
        public void connect() {

        }

        @Override
        public String getName() {
            return "InstructionListenerReceiverMock";
        }

        @Override
        public void init(Properties properties) {

        }

        @Override
        public void start() {
            Request request = new Request();
            request.setCommand(commandToListenFor.commandString());

            request.setParameters(requestParameters);

            Instruction instruction = new Instruction();
            instruction.setRequest(request);

            getNjamsReceiver().distribute(instruction);

            actualResponse = instruction.getResponse();
            checkResponses();
        }

        @Override
        public void stop() {

        }

        private void checkResponses() {
            assertThat(actualResponse.getResultCode(), is(equalTo(expectedResponse.getResultCode())));
            assertThat(actualResponse.getResultMessage(), is(equalTo(expectedResponse.getResultMessage())));
        }

        public InstructionListeningReceiverMock forCommand(Command command) {
            this.commandToListenFor = command;
            return this;
        }

        public void checkResponse(Response expectedResponse) {
            this.expectedResponse = expectedResponse;
        }

        public void addRequestParameters(Map<String, String> requestParameters) {
            this.requestParameters = requestParameters;
        }
    }
}
