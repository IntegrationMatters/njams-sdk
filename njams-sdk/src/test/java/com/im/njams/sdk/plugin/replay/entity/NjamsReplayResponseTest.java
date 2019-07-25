///*
// * Copyright (c) 2019 Faiz & Siegeln Software GmbH
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
// * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
// * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
// * the Software.
// *
// * The Software shall be used for Good, not Evil.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
// * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
// * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
// * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
// * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// */
//package com.im.njams.sdk.plugin.replay.entity;
//
//import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
//import com.faizsiegeln.njams.messageformat.v4.command.Response;
//import com.im.njams.sdk.common.DateTimeUtility;
//import org.junit.Before;
//import org.junit.Test;
//
//import java.time.LocalDateTime;
//import java.util.HashMap;
//import java.util.Map;
//
//import static com.im.njams.sdk.plugin.replay.entity.NjamsReplayResponse.EXCEPTION_KEY;
//import static com.im.njams.sdk.plugin.replay.entity.NjamsReplayResponse.MAIN_LOG_ID_KEY;
//import static org.junit.Assert.assertEquals;
//import static org.mockito.Mockito.spy;
//
//public class NjamsReplayResponseTest {
//
//    private static final int TEST_RESULT_CODE = 1337;
//    private static final String TEST_RESULT_MESSAGE = "TEST_MESSAGE";
//    private static final LocalDateTime TEST_TIME = DateTimeUtility.now();
//    private static final String TEST_EXCEPTION = "TEST_EXCEPTION";
//    private static final String TEST_MAIN_LOG_ID = "TEST_ID";
//    private static final String TEST_PARAMETER_KEY = "TEST_KEY";
//    private static final String TEST_PARAMETER_VALUE = "TEST_VALUE";
//
//    private NjamsReplayResponse replayResponse;
//
//    @Before
//    public void initResponse() {
//        replayResponse = spy(new NjamsReplayResponse());
//    }
//
//    @Test
//    public void addParametersToInstruction(){
//        fillReplayResponse();
//        Instruction instruction = new Instruction();
//        replayResponse.addParametersToInstruction(instruction);
//
//        Response response = instruction.getResponse();
//
//        assertEquals(TEST_RESULT_CODE, response.getResultCode());
//        assertEquals(TEST_RESULT_MESSAGE, response.getResultMessage());
//        assertEquals(TEST_TIME, response.getDateTime());
//        Map<String, String> parameters = response.getParameters();
//        assertEquals(parameters.get(EXCEPTION_KEY), TEST_EXCEPTION);
//        assertEquals(parameters.get(MAIN_LOG_ID_KEY), TEST_MAIN_LOG_ID);
//        assertEquals(parameters.get(TEST_PARAMETER_KEY), TEST_PARAMETER_VALUE);
//    }
//
//    private void fillReplayResponse(){
//        replayResponse.setResultCode(TEST_RESULT_CODE);
//        replayResponse.setResultMessage(TEST_RESULT_MESSAGE);
//        replayResponse.setDateTime(TEST_TIME);
//        replayResponse.setException(TEST_EXCEPTION);
//        replayResponse.setMainLogId(TEST_MAIN_LOG_ID);
//        Map<String, String> parameters = new HashMap<>();
//        parameters.put(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
//        replayResponse.setParameters(parameters);
//    }
//}