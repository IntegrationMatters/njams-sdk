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
package com.im.njams.sdk.communication.receiver.instruction.boundary;

import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.receiver.instruction.control.InstructionController;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.InstructionProcessor;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides checks that only valid {@link InstructionProcessor instructionProcessors} are
 * used to process {@link Instruction instructions}. Furthermore the class logs the
 * {@link com.faizsiegeln.njams.messageformat.v4.command.Request requests} and
 * {@link com.faizsiegeln.njams.messageformat.v4.command.Response responses} that are processed here.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public class InstructionProcessorService {

    private static final Logger LOG = LoggerFactory.getLogger(InstructionProcessorService.class);

    private InstructionController instructionController = new InstructionController();

    /**
     * Adds an {@link InstructionProcessor instructionProcessor} for the command String that is referring to
     * {@link InstructionProcessor#getCommandToListenTo()}. If a {@link InstructionProcessor instructionProcessor}
     * with the same commandString is found, the old InstructionProcessor will be overwritten by the new
     * instructionProcessor.
     *
     * @param instructionProcessor the InstructionProcessor to add
     */
    public synchronized void addInstructionProcessor(InstructionProcessor instructionProcessor) {
        if (instructionProcessor != null) {
            String commandToListenTo = instructionProcessor.getCommandToListenTo();
            if (StringUtils.isBlank(commandToListenTo)) {
                LOG.warn("Cannot set InstructionProcessor for no command");
            } else {
                InstructionProcessor oldInstructionProcessor = instructionController
                        .getInstructionProcessor(commandToListenTo);
                if (oldInstructionProcessor != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Replacing InstructionProcessor {} for command {} by {}.",
                                oldInstructionProcessor.getClass().getSimpleName(), commandToListenTo,
                                instructionProcessor.getClass().getSimpleName());
                    }
                }
                this.instructionController.putInstructionProcessor(commandToListenTo, instructionProcessor);
            }
        }
    }

    /**
     * Returns the InstructionProcessor that is used to handle the {@link Instruction instruction} with the given
     * command.
     *
     * @param commandToListenTo the command the {@link InstructionProcessor instructionProcessor} is listening to
     * @return the {@link InstructionProcessor instructionProcessor} that would process the {@link Instruction
     * instruction} with the given command or null, if there is no {@link InstructionProcessor instructionProcessor}
     * for this command.
     */
    public InstructionProcessor getInstructionProcessor(String commandToListenTo) {
        return instructionController.getInstructionProcessor(commandToListenTo);
    }

    /**
     * Removes the {@link InstructionProcessor instructionProcessor} that listens to the given command. If no
     * {@link InstructionProcessor instructionProcessor} can be found that listens to the command, nothing happens.
     *
     * @param instructionProcessorCommand the command the {@link InstructionProcessor instructionProcessor} is
     *                                    listening to.
     */
    public synchronized void removeInstructionProcessor(String instructionProcessorCommand) {
        InstructionProcessor removedInstructionProcessor = instructionController
                .removeInstructionProcessor(instructionProcessorCommand);
        if (LOG.isDebugEnabled()) {
            if (removedInstructionProcessor != null) {
                LOG.debug("Removed InstructionProcessor {} for command {}.",
                        removedInstructionProcessor.getClass().getSimpleName(), instructionProcessorCommand);
            } else {
                LOG.debug("Can't remove InstructionProcessor for command {}, because it hasn't been added before.",
                        instructionProcessorCommand);
            }
        }
    }

    /**
     * Processes the {@link Instruction instruction} by the correct InstructionListener that listens to the command
     * that is brought by the {@link Instruction instruction}, if a suitable {@link InstructionProcessor
     * instructionProcessor} has been
     * {@link InstructionProcessorService#addInstructionProcessor(InstructionProcessor) added} before.
     *
     * @param instruction the instruction to process.
     */
    public synchronized void processInstruction(Instruction instruction) {
        try {
            InstructionLogger logger = new InstructionLogger(instruction);

            logger.logRequest();

            instructionController.processInstruction(instruction);

            logger.logResponse();
        } catch (NjamsInstructionException njamsInstructionIsCorruptedException) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(njamsInstructionIsCorruptedException.getMessage());
            }
        }
    }

    /**
     * Stops the normal processing of the instructions.
     */
    public synchronized void stop() {
        instructionController.removeAllInstructionProcessors();
    }

    /**
     * Facility to log requests and responses.
     */
    private static class InstructionLogger {

        private static final String NJAMS_INSTRUCTION_IS_NULL_EXCEPTION = "NjamsInstruction must not be null";
        private static final String REQUEST_READER_IS_NULL_EXCEPTION = "RequestReader must not be null";

        private Instruction.RequestReader requestToLog;
        private Instruction.ResponseWriter responseToLog;
        private String command;

        private InstructionLogger(Instruction instructionToLog) throws NjamsInstructionException {
            setRequestReaderAndResponseWriter(instructionToLog);
            setCommandToLog();
        }

        private void setRequestReaderAndResponseWriter(Instruction instructionToLog) throws NjamsInstructionException {
            try {
                requestToLog = instructionToLog.getRequestReader();
                responseToLog = instructionToLog.getResponseWriter();
            } catch (NullPointerException instructionIsNullException) {
                throw new NjamsInstructionException(NJAMS_INSTRUCTION_IS_NULL_EXCEPTION, instructionIsNullException);
            }
        }

        private void setCommandToLog() throws NjamsInstructionException {
            try {
                command = requestToLog.getCommand();
            } catch (NullPointerException requestIsNullException) {
                throw new NjamsInstructionException(REQUEST_READER_IS_NULL_EXCEPTION, requestIsNullException);
            }
        }

        private void logRequest() {
            if (LOG.isDebugEnabled() && !requestToLog.isEmpty()) {
                LOG.debug("Received request with command: {}", command);
            }
            if (LOG.isTraceEnabled()) {
                try {
                    LOG.trace("Request: \n{}", requestToLog);
                } catch (NjamsSdkRuntimeException requestNotSerializableException) {
                    LOG.error("Request couldn't be serialized successfully", requestNotSerializableException);
                }
            }
        }

        private void logResponse() {
            if (!requestToLog.isEmpty()) {
                logResponseForProcessedRequest();
            } else {
                logResponseForInvalidInstruction();
            }
        }

        private void logResponseForProcessedRequest() {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created response for command: {}", command);
            }
            if (LOG.isTraceEnabled()) {
                try {
                    LOG.trace("Response for command {} : \n{}", command, responseToLog);
                } catch (Exception responseNotSerializableException) {
                    LOG.error("Response couldn't be serialized successfully", responseNotSerializableException);
                }
            }
        }

        private void logResponseForInvalidInstruction() {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created response for invalid instruction");
            }
            if (LOG.isTraceEnabled()) {
                try {
                    LOG.trace("Response for a not forwarded request {} : \n{}", responseToLog);
                } catch (Exception responseNotSerializableException) {
                    LOG.error("Response couldn't be serialized successfully", responseNotSerializableException);
                }
            }
        }
    }
}