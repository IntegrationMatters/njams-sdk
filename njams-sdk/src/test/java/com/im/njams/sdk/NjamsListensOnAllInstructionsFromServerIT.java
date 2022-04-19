/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
 *
 */

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

import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class NjamsListensOnAllInstructionsFromServerIT {

    private Response expectedResponse;
    private InstructionListeningReceiverMock instructionListeningReceiverMock;
    private Njams njams;


    @Before
    public void setUp() throws Exception {
        expectedResponse = new Response();

        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestReceiver.NAME);

        njams = new Njams(new Path(), "SDK", settings);

        instructionListeningReceiverMock = new InstructionListeningReceiverMock();
        instructionListeningReceiverMock.setNjamsReceiver(njams.getNjamsReceiver());
        instructionListeningReceiverMock.setExpectedResponse(expectedResponse);
        TestReceiver.setReceiverMock(instructionListeningReceiverMock);
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
    public void instructionListenerFor_getLogMode_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Success");

        instructionListeningReceiverMock.commandToCheck(Command.GET_LOG_MODE);

        njams.start();
    }

    @Test
    public void instructionListenerFor_setLogMode_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("missing parameter(s) [logMode]");

        instructionListeningReceiverMock.commandToCheck(Command.SET_LOG_MODE);

        njams.start();
    }

    @Test
    public void instructionListenerFor_setTracing_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("missing parameter(s) [processPath, activityId]");

        instructionListeningReceiverMock.commandToCheck(Command.SET_TRACING);

        njams.start();
    }

    @Test
    public void instructionListenerFor_getTracing_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("missing parameter(s) [processPath, activityId]");

        instructionListeningReceiverMock.commandToCheck(Command.GET_TRACING);

        njams.start();
    }

    @Test
    public void instructionListenerFor_configureExtract_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("missing parameter(s) [processPath, activityId, extract]");

        instructionListeningReceiverMock.commandToCheck(Command.CONFIGURE_EXTRACT);

        njams.start();
    }

    @Test
    public void instructionListenerFor_deleteExtract_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("missing parameter(s) [processPath, activityId]");

        instructionListeningReceiverMock.commandToCheck(Command.DELETE_EXTRACT);

        njams.start();
    }

    @Test
    public void instructionListenerFor_getExtract_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("missing parameter(s) [processPath, activityId]");

        instructionListeningReceiverMock.commandToCheck(Command.GET_EXTRACT);

        njams.start();
    }

    @Test
    public void instructionListenerFor_sendProjectMessage_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Successfully sent ProjectMessage via NjamsClient");

        instructionListeningReceiverMock.commandToCheck(Command.SEND_PROJECTMESSAGE);

        njams.start();
    }

    @Test
    public void instructionListenerFor_replay_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("Client cannot replay processes. No replay handler is present.");

        instructionListeningReceiverMock.commandToCheck(Command.REPLAY);

        njams.start();
    }

    @Test
    public void instructionListenerFor_record_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Success");

        instructionListeningReceiverMock.commandToCheck(Command.RECORD);

        njams.start();
    }

    @Test
    public void instructionListenerFor_testExpression_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(1);
        expectedResponse.setResultMessage("missing parameter(s) [ruleType, expression, data]");

        instructionListeningReceiverMock.commandToCheck(Command.TEST_EXPRESSION);

        njams.start();
    }

    @Test
    public void instructionListenerFor_ping_isAddedBeforeRealReceiver_isStarted(){
        expectedResponse.setResultCode(0);
        expectedResponse.setResultMessage("Pong");

        instructionListeningReceiverMock.commandToCheck(Command.PING);

        njams.start();
    }

    private static class InstructionListeningReceiverMock extends AbstractReceiver {
        private Command commandToListenFor;
        private Response expectedResponse;
        private Response actualResponse;

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
    }
}
