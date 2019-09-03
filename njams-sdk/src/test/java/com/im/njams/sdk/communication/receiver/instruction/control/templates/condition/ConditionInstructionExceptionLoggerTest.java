/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.communication.receiver.instruction.control.templates.condition;

import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.communication.receiver.instruction.control.templates.condition.ConditionInstructionExceptionLogger;
import org.junit.Test;

import static com.im.njams.sdk.api.adapter.messageformat.command.Instruction.RequestReader.EMPTY_STRING;
import static com.im.njams.sdk.communication.receiver.instruction.control.templates.condition.ConditionInstructionExceptionLogger.INVALID_INSTRUCTION;
import static org.mockito.Mockito.*;

public class ConditionInstructionExceptionLoggerTest {

    private static final String COMMAND_TO_LOG = "TestCommand";
    private static final String PROCESS_PATH_TO_LOG = "TestProcessPath";
    private static final String ACTIVITY_ID_TO_LOG = "TestActivityId";
    private static final String EXCEPTION_MESSAGE_TO_LOG = "TestExceptionMessage";

//All null

    public static final String ALL_NULL = "cpan";

    @Test
    public void test0() {
        log(ALL_NULL);
    }

//Only Exception

    public static final String ONLY_EXCEPTION_WITH_NO_MESSAGE = "cpae";
    public static final String ONLY_EXCEPTION_WITH_MESSAGE = "cpaE";
    public static final String ONLY_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE = "cpat";
    public static final String ONLY_EXCEPTION_WITH_MESSAGE_AND_THROWABLE = "cpaT";

    @Test
    public void test1() {
        log(ONLY_EXCEPTION_WITH_NO_MESSAGE);
    }

    @Test
    public void test2() {
        log(ONLY_EXCEPTION_WITH_MESSAGE);
    }

    @Test
    public void test3() {
        log(ONLY_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE);
    }

    @Test
    public void test4() {
        log(ONLY_EXCEPTION_WITH_MESSAGE_AND_THROWABLE);
    }

//Only Activity

    public static final String ONLY_ACTIVITY = "cpAn";
    public static final String ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE = "cpAe";
    public static final String ACTIVITY_AND_EXCEPTION_WITH_MESSAGE = "cpAE";
    public static final String ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE = "cpAt";
    public static final String ACTIVITY_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE = "cpAT";

    @Test
    public void test5() {
        log(ONLY_ACTIVITY);
    }

    @Test
    public void test6() {
        log(ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE);
    }

    @Test
    public void test7() {
        log(ACTIVITY_AND_EXCEPTION_WITH_MESSAGE);
    }

    @Test
    public void test8() {
        log(ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE);
    }

    @Test
    public void test9() {
        log(ACTIVITY_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE);
    }

//Only Process

    public static final String ONLY_PROCESS = "cPan";
    public static final String PROCESS_AND_EXCEPTION_WITH_NO_MESSAGE = "cPae";
    public static final String PROCESS_AND_EXCEPTION_WITH_MESSAGE = "cPaE";
    public static final String PROCESS_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE = "cPat";
    public static final String PROCESS_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE = "cPaT";

    @Test
    public void test10() {
        log(ONLY_PROCESS);
    }

    @Test
    public void test11() {
        log(PROCESS_AND_EXCEPTION_WITH_NO_MESSAGE);
    }

    @Test
    public void test12() {
        log(PROCESS_AND_EXCEPTION_WITH_MESSAGE);
    }

    @Test
    public void test13() {
        log(PROCESS_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE);
    }

    @Test
    public void test14() {
        log(PROCESS_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE);
    }

//Activity and Process

    public static final String PROCESS_AND_ACTIVITY = "cPAn";
    public static final String PROCESS_ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE = "cPAe";
    public static final String PROCESS_ACTIVITY_AND_EXCEPTION_WITH_MESSAGE = "cPAE";
    public static final String PROCESS_ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE = "cPAt";
    public static final String PROCESS_ACTIVITY_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE = "cPAT";

    @Test
    public void test15() {
        log(PROCESS_AND_ACTIVITY);
    }

    @Test
    public void test16() {
        log(PROCESS_ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE);
    }

    @Test
    public void test17() {
        log(PROCESS_ACTIVITY_AND_EXCEPTION_WITH_MESSAGE);
    }

    @Test
    public void test18() {
        log(PROCESS_ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE);
    }

    @Test
    public void test19() {
        log(PROCESS_ACTIVITY_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE);
    }

//Only Command

    public static final String ONLY_COMMAND = "Cpan";
    public static final String COMMAND_AND_EXCEPTION_WITH_NO_MESSAGE = "Cpae";
    public static final String COMMAND_AND_EXCEPTION_WITH_MESSAGE = "CpaE";
    public static final String COMMAND_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE = "Cpat";
    public static final String COMMAND_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE = "CpaT";

    @Test
    public void test20() {
        log(ONLY_COMMAND);
    }

    @Test
    public void test21() {
        log(COMMAND_AND_EXCEPTION_WITH_NO_MESSAGE);
    }

    @Test
    public void test22() {
        log(COMMAND_AND_EXCEPTION_WITH_MESSAGE);
    }

    @Test
    public void test23() {
        log(COMMAND_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE);
    }

    @Test
    public void test24() {
        log(COMMAND_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE);
    }

//Command and Activity

    public static final String COMMAND_AND_ACTIVITY = "CpAn";
    public static final String COMMAND_ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE = "CpAe";
    public static final String COMMAND_ACTIVITY_AND_EXCEPTION_WITH_MESSAGE = "CpAE";
    public static final String COMMAND_ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE = "CpAt";
    public static final String COMMAND_ACTIVITY_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE = "CpAT";

    @Test
    public void test25() {
        log(COMMAND_AND_ACTIVITY);
    }

    @Test
    public void test26() {
        log(COMMAND_ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE);
    }

    @Test
    public void test27() {
        log(COMMAND_ACTIVITY_AND_EXCEPTION_WITH_MESSAGE);
    }

    @Test
    public void test28() {
        log(COMMAND_ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE);
    }

    @Test
    public void test29() {
        log(COMMAND_ACTIVITY_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE);
    }

//Command and Process

    public static final String COMMAND_AND_PROCESS = "CPan";
    public static final String COMMAND_PROCESS_AND_EXCEPTION_WITH_NO_MESSAGE = "CPae";
    public static final String COMMAND_PROCESS_AND_EXCEPTION_WITH_MESSAGE = "CPaE";
    public static final String COMMAND_PROCESS_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE = "CPat";
    public static final String COMMAND_PROCESS_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE = "CPaT";

    @Test
    public void test30() {
        log(COMMAND_AND_PROCESS);
    }

    @Test
    public void test31() {
        log(COMMAND_PROCESS_AND_EXCEPTION_WITH_NO_MESSAGE);
    }

    @Test
    public void test32() {
        log(COMMAND_PROCESS_AND_EXCEPTION_WITH_MESSAGE);
    }

    @Test
    public void test33() {
        log(COMMAND_PROCESS_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE);
    }

    @Test
    public void test34() {
        log(COMMAND_PROCESS_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE);
    }

//Command, Process and Activity

    public static final String COMMAND_PROCESS_AND_ACTIVITY = "CPAn";
    public static final String COMMAND_PROCESS_ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE = "CPAe";
    public static final String COMMAND_PROCESS_ACTIVITY_AND_EXCEPTION_WITH_MESSAGE = "CPAE";
    public static final String COMMAND_PROCESS_ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE = "CPAt";
    public static final String COMMAND_PROCESS_ACTIVITY_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE = "CPAT";

    @Test
    public void test35() {
        log(COMMAND_PROCESS_AND_ACTIVITY);
    }

    @Test
    public void test36() {
        log(COMMAND_PROCESS_ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE);
    }

    @Test
    public void test37() {
        log(COMMAND_PROCESS_ACTIVITY_AND_EXCEPTION_WITH_MESSAGE);
    }

    @Test
    public void test38() {
        log(COMMAND_PROCESS_ACTIVITY_AND_EXCEPTION_WITH_NO_MESSAGE_BUT_A_THROWABLE);
    }

    @Test
    public void test39() {
        log(COMMAND_PROCESS_ACTIVITY_AND_EXCEPTION_WITH_MESSAGE_AND_THROWABLE);
    }

    private void log(String transitionWord) {
        ConditionInstructionExceptionLogger logger = mockLoggingMethods(transitionWord);

        logger.log();

        verifyCorrectLogging(logger, transitionWord);
    }

    private ConditionInstructionExceptionLogger mockLoggingMethods(String trueOrFalseString) {
        char[] trueOrFalseChars = trueOrFalseString.toCharArray();

        String commandToUse = EMPTY_STRING;
        String processPathToUse = EMPTY_STRING;
        String activityIdToUse = EMPTY_STRING;
        NjamsInstructionException njamsInstructionExceptionToUse = mock(NjamsInstructionException.class);
        Throwable throwableToUse = mock(Throwable.class);

        if (parseCharToBoolean(trueOrFalseChars[0])) {
            commandToUse = COMMAND_TO_LOG;
        }
        if (parseCharToBoolean(trueOrFalseChars[1])) {
            processPathToUse = PROCESS_PATH_TO_LOG;
        }
        if (parseCharToBoolean(trueOrFalseChars[2])) {
            activityIdToUse = ACTIVITY_ID_TO_LOG;
        }

        char exceptionChar = trueOrFalseChars[3];
        if (parseCharToBoolean(exceptionChar)) {
            if (exceptionChar == 'T') {//exception has a message and a cause
                when(njamsInstructionExceptionToUse.getMessage()).thenReturn(EXCEPTION_MESSAGE_TO_LOG);
                when(njamsInstructionExceptionToUse.getCause()).thenReturn(throwableToUse);
            }
            if (exceptionChar == 'E') { //exception has a message but no cause
                when(njamsInstructionExceptionToUse.getMessage()).thenReturn(EXCEPTION_MESSAGE_TO_LOG);
            }
        } else {
            if (exceptionChar == 'n') { //exception is null
                njamsInstructionExceptionToUse = null;
            } else if (exceptionChar == 't') { //exception has no message but a cause
                when(njamsInstructionExceptionToUse.getCause()).thenReturn(throwableToUse);
            }
        }

        return spy(new ConditionInstructionExceptionLogger(commandToUse, processPathToUse, activityIdToUse,
                njamsInstructionExceptionToUse));
    }

    private boolean parseCharToBoolean(char booleanCharacter) {
        if (Character.isLowerCase(booleanCharacter)) {
            return false;
        } else {
            return true;
        }
    }

    private void verifyCorrectLogging(ConditionInstructionExceptionLogger logger, String trueOrFalseString) {
        char[] trueOrFalseChars = trueOrFalseString.toCharArray();

        char commandChar = trueOrFalseChars[0];
        char processChar = trueOrFalseChars[1];
        char activityChar = trueOrFalseChars[2];
        char exceptionChar = trueOrFalseChars[3];

        StringBuilder messageThatHasBeenLogged = new StringBuilder();


        if (!parseCharToBoolean(commandChar)) {
            messageThatHasBeenLogged = messageThatHasBeenLogged.append(INVALID_INSTRUCTION);
        } else {
            messageThatHasBeenLogged.append("Failed to execute command: [").append(COMMAND_TO_LOG).append("]");
            if (parseCharToBoolean(processChar)) {
                messageThatHasBeenLogged.append(" on process: ").append(PROCESS_PATH_TO_LOG);
                if (parseCharToBoolean(activityChar)) {
                    messageThatHasBeenLogged.append("#").append(ACTIVITY_ID_TO_LOG);
                }
            }
            messageThatHasBeenLogged.append(".");
            if (parseCharToBoolean(exceptionChar)) {
                messageThatHasBeenLogged.append(" Reason: ").append(EXCEPTION_MESSAGE_TO_LOG);
            }
        }

        if (parseCharToBoolean(commandChar) && (exceptionChar == 't' || exceptionChar == 'T')) {
            verify(logger).logStringAndException(eq(messageThatHasBeenLogged.toString()), any());
        } else {
            verify(logger).logString(messageThatHasBeenLogged.toString());
        }
    }
}