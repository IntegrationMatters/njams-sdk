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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.adapter.messageformat.command.control;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.replay.ReplayInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;

public class NjamsInstructionWrapper {

    private com.faizsiegeln.njams.messageformat.v4.command.Instruction instructionToWrap;

    private Instruction wrappedInstruction;

    public NjamsInstructionWrapper(com.faizsiegeln.njams.messageformat.v4.command.Instruction instructionToWrap) {
        this.instructionToWrap = instructionToWrap;
    }

    public Instruction wrap() {
        if (!isInstructionWrapped()) {
            wrapInstruction();
        }
        return wrappedInstruction;
    }

    private boolean isInstructionWrapped() {
        return wrappedInstruction != null;
    }

    private void wrapInstruction() {
        Command commandFromInstruction = extractCommandFromInstruction();

        CommandClassifier typeOfCommand = CommandClassifier.getTypeOfCommand(commandFromInstruction);

        wrappedInstruction = createInstructionBasedOnCommandType(typeOfCommand);
    }

    private Command extractCommandFromInstruction() {
        try {
            return Command.getFromCommandString(instructionToWrap.getCommand());
        } catch (NullPointerException instructionIsNullException) {
            return null;
        }
    }

    private Instruction createInstructionBasedOnCommandType(CommandClassifier typeOfCommand) {
        if (typeOfCommand == CommandClassifier.CONDITION) {
            return new ConditionInstruction(instructionToWrap);
        } else if (typeOfCommand == CommandClassifier.REPLAY) {
            return new ReplayInstruction(instructionToWrap);
        } else {
            return new NjamsInstruction(instructionToWrap);
        }
    }

    private enum CommandClassifier {

        DEFAULT,
        REPLAY,
        CONDITION;

        public static CommandClassifier getTypeOfCommand(Command command) {
            CommandClassifier typeOfCommand = DEFAULT;
            if (command != null) {
                if (isConditionCommand(command)) {
                    typeOfCommand = CONDITION;
                } else if (isReplayCommand(command)) {
                    typeOfCommand = REPLAY;
                }
            }
            return typeOfCommand;
        }

        private static boolean isConditionCommand(Command commandToCheck) {
            switch (commandToCheck) {
                case GET_LOG_LEVEL:
                case SET_LOG_LEVEL:
                case GET_LOG_MODE:
                case SET_LOG_MODE:
                case GET_TRACING:
                case SET_TRACING:
                case GET_EXTRACT:
                case CONFIGURE_EXTRACT:
                case DELETE_EXTRACT:
                case RECORD:
                    return true;
                default:
                    return false;
            }
        }

        private static boolean isReplayCommand(Command command) {
            if (command == Command.REPLAY) {
                return true;
            } else {
                return false;
            }
        }
    }
}
