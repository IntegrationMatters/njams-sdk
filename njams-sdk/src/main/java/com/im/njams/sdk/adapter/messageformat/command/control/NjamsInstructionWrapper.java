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
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.DefaultInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.ReplayInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.exceptions.NjamsInstructionException;

public class NjamsInstructionWrapper {

    private static final String INSTRUCTION_IS_NULL_EXCEPTION_MESSAGE = "Instruction is null and therefore can't be " +

                                                                        "wrapped.";

    private com.faizsiegeln.njams.messageformat.v4.command.Instruction instructionToWrap;

    private Instruction wrappedInstruction;

    private CommandClassifier commandClassifier;

    public NjamsInstructionWrapper(com.faizsiegeln.njams.messageformat.v4.command.Instruction instructionToWrap) {
        this.instructionToWrap = instructionToWrap;
        this.commandClassifier = new CommandClassifier();
    }

    public Instruction wrap() throws NjamsInstructionException {
        if (!isInstructionWrapped()) {
            wrapInstruction();
        }
        return wrappedInstruction;
    }

    private boolean isInstructionWrapped() {
        return wrappedInstruction != null;
    }

    private void wrapInstruction() throws NjamsInstructionException {
        checkInstruction();

        setWrappedInstruction();
    }

    private void checkInstruction() throws NjamsInstructionException {
        if (instructionToWrap == null) {
            throw new NjamsInstructionException(INSTRUCTION_IS_NULL_EXCEPTION_MESSAGE);
        }
    }

    private void setWrappedInstruction() {
        CommandClassifier.TypeOfCommand typeOfCommand = commandClassifier
                .getTypeOfCommand(Command.getFromCommandString(instructionToWrap.getCommand()));
        wrappedInstruction = createInstructionBasedOnCommand(typeOfCommand);
    }

    private Instruction createInstructionBasedOnCommand(CommandClassifier.TypeOfCommand typeOfCommand) {
        if (typeOfCommand == CommandClassifier.TypeOfCommand.CONDITION) {
            return new ConditionInstruction(instructionToWrap);
        } else if(typeOfCommand == CommandClassifier.TypeOfCommand.REPLAY){
            return new ReplayInstruction(instructionToWrap);
        } else{
            return new DefaultInstruction(instructionToWrap);
        }
    }

    private static class CommandClassifier{

        private enum TypeOfCommand {
            DEFAULT,
            REPLAY,
            CONDITION
        }

        public TypeOfCommand getTypeOfCommand(Command command){
            TypeOfCommand typeOfCommand = TypeOfCommand.DEFAULT;
            if (command != null) {
                if (isConditionCommand(command)) {
                    typeOfCommand = TypeOfCommand.CONDITION;
                } else if (isReplayCommand(command)) {
                    typeOfCommand = TypeOfCommand.REPLAY;
                }
            }
            return typeOfCommand;
        }

        private boolean isConditionCommand(Command commandToCheck) {
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

        private boolean isReplayCommand(Command command) {
            if(command == Command.REPLAY){
                return true;
            }
            else{
                return false;
            }
        }
    }
}
