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

        njams = new Njams(new Path(), "SDK", settings);

        instructionListeningReceiverMock = new InstructionListeningReceiverMock();
        instructionListeningReceiverMock.addRequestParameters(requestParameters);
        instructionListeningReceiverMock.setNjamsReceiver(njams.getNjamsReceiver());
        instructionListeningReceiverMock.setExpectedResponse(expectedResponse);
        TestReceiver.setReceiverMock(instructionListeningReceiverMock);
    }

    @Test
    public void instructionListenerFor_getLogLevel_isAddedBeforeRealReceiver_isStarted(){
        requestParameters.put("processPath", "Test");

        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Success");

        instructionListeningReceiverMock.commandToCheck(Command.GET_LOG_LEVEL);

        njams.start();
    }

    @Test
    public void instructionListenerFor_getLogLevel_withoutProcessPath_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("missing parameter(s) [processPath]");

        instructionListeningReceiverMock.commandToCheck(Command.GET_LOG_LEVEL);

        njams.start();
    }

    @Test
    public void instructionListenerFor_setLogLevel_withoutProcessPathAndLogLevel_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("missing parameter(s) [processPath, logLevel]");

        instructionListeningReceiverMock.commandToCheck(Command.SET_LOG_LEVEL);

        njams.start();
    }

    @Test
    public void instructionListenerFor_setLogLevel_withoutProcessPath_isAddedBeforeRealReceiver_isStarted(){
        requestParameters.put("logLevel", "info");

        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("missing parameter(s) [processPath]");

        instructionListeningReceiverMock.commandToCheck(Command.SET_LOG_LEVEL);

        njams.start();
    }

    @Test
    public void instructionListenerFor_setLogLevel_withoutLogLevel_isAddedBeforeRealReceiver_isStarted(){
        requestParameters.put("processPath", "Test");

        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("missing parameter(s) [logLevel]");

        instructionListeningReceiverMock.commandToCheck(Command.SET_LOG_LEVEL);

        njams.start();
    }

    @Test
    public void instructionListenerFor_setLogLevel_withoutWrongLogLevel_isAddedBeforeRealReceiver_isStarted(){
        requestParameters.put("processPath", "Test");
        requestParameters.put("logLevel", "WrongLogLevel");

        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("could not parse value of parameter [logLevel] value=WrongLogLevel");

        instructionListeningReceiverMock.commandToCheck(Command.SET_LOG_LEVEL);

        njams.start();
    }

    @Test
    public void instructionListenerFor_setLogLevel_info_isAddedBeforeRealReceiver_isStarted(){
        requestParameters.put("processPath", "Test");
        requestParameters.put("logLevel", "info");

        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Success");

        instructionListeningReceiverMock.commandToCheck(Command.SET_LOG_LEVEL);

        njams.start();
    }

    @Test
    public void instructionListenerFor_setLogLevel_success_isAddedBeforeRealReceiver_isStarted(){
        requestParameters.put("processPath", "Test");
        requestParameters.put("logLevel", "success");

        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Success");

        instructionListeningReceiverMock.commandToCheck(Command.SET_LOG_LEVEL);

        njams.start();
    }

    @Test
    public void instructionListenerFor_setLogLevel_warn_isAddedBeforeRealReceiver_isStarted(){
        requestParameters.put("processPath", "Test");
        requestParameters.put("logLevel", "warning");

        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Success");

        instructionListeningReceiverMock.commandToCheck(Command.SET_LOG_LEVEL);

        njams.start();
    }

    @Test
    public void instructionListenerFor_setLogLevel_error_isAddedBeforeRealReceiver_isStarted(){
        requestParameters.put("processPath", "Test");
        requestParameters.put("logLevel", "error");

        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Success");

        instructionListeningReceiverMock.commandToCheck(Command.SET_LOG_LEVEL);

        njams.start();
    }

    @Test
    public void instructionListenerFor_ping_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Pong");

        instructionListeningReceiverMock.commandToCheck(Command.PING);

        njams.start();
    }

    @Test
    public void instructionListenerFor_sendProjectMessage_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Successfully sent ProjectMessage via NjamsClient");

        instructionListeningReceiverMock.commandToCheck(Command.SEND_PROJECTMESSAGE);

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

        public void commandToCheck(Command command) {
            this.commandToListenFor = command;
        }

        public void setExpectedResponse(Response expectedResponse) {
            this.expectedResponse = expectedResponse;
        }

        public void addRequestParameters(Map<String, String> requestParameters) {
            this.requestParameters = requestParameters;
        }
    }
}
