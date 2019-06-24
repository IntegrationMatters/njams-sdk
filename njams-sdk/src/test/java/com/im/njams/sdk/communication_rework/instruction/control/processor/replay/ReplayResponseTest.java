package com.im.njams.sdk.communication_rework.instruction.control.processor.replay;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.DateTimeUtility;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.im.njams.sdk.communication_rework.instruction.control.processor.replay.ReplayResponse.EXCEPTION_KEY;
import static com.im.njams.sdk.communication_rework.instruction.control.processor.replay.ReplayResponse.MAIN_LOG_ID_KEY;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;

public class ReplayResponseTest {

    private static final int TEST_RESULT_CODE = 1337;
    private static final String TEST_RESULT_MESSAGE = "TEST_MESSAGE";
    private static final LocalDateTime TEST_TIME = DateTimeUtility.now();
    private static final String TEST_EXCEPTION = "TEST_EXCEPTION";
    private static final String TEST_MAIN_LOG_ID = "TEST_ID";
    private static final String TEST_PARAMETER_KEY = "TEST_KEY";
    private static final String TEST_PARAMETER_VALUE = "TEST_VALUE";

    private ReplayResponse replayResponse;

    @Before
    public void initResponse() {
        replayResponse = spy(new ReplayResponse());
    }

    @Test
    public void addParametersToInstruction(){
        fillReplayResponse();
        Instruction instruction = new Instruction();
        replayResponse.addParametersToInstruction(instruction);

        Response response = instruction.getResponse();

        assertEquals(TEST_RESULT_CODE, response.getResultCode());
        assertEquals(TEST_RESULT_MESSAGE, response.getResultMessage());
        assertEquals(TEST_TIME, response.getDateTime());
        Map<String, String> parameters = response.getParameters();
        assertEquals(parameters.get(EXCEPTION_KEY), TEST_EXCEPTION);
        assertEquals(parameters.get(MAIN_LOG_ID_KEY), TEST_MAIN_LOG_ID);
        assertEquals(parameters.get(TEST_PARAMETER_KEY), TEST_PARAMETER_VALUE);
    }

    private void fillReplayResponse(){
        replayResponse.setResultCode(TEST_RESULT_CODE);
        replayResponse.setResultMessage(TEST_RESULT_MESSAGE);
        replayResponse.setDateTime(TEST_TIME);
        replayResponse.setException(TEST_EXCEPTION);
        replayResponse.setMainLogId(TEST_MAIN_LOG_ID);
        Map<String, String> parameters = new HashMap<>();
        parameters.put(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
        replayResponse.setParameters(parameters);
    }
}