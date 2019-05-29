///*
// * Copyright (c) 2018 Faiz & Siegeln Software GmbH
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
// * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
// * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
// *
// * The Software shall be used for Good, not Evil.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
// * IN THE SOFTWARE.
// */
//package com.im.njams.sdk.communication_rework.instruction.processor.configuration;
//
//import com.faizsiegeln.njams.messageformat.v4.command.Command;
//import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
//import com.im.njams.sdk.communication.InstructionListener;
//import com.im.njams.sdk.communication_rework.instruction.processor.configuration.InstructionSupport;
//
///**
// * InstructionListener implementation for all instructions which will modify
// * configuration values.
// *
// * @author pnientiedt
// */
//public class ConfigurationInstructionListener implements InstructionListener {
//
//    /**
//     * Configure configuration if a valid instruction will be given.
//     *
//     * @param instruction to validate
//     */
//    @Override
//    public void onInstruction(final Instruction instruction) {
//        final Command command = Command.getFromInstruction(instruction);
//        final InstructionSupport instructionSupport = new InstructionSupport(instruction);
//        if (command == null) {
//            instructionSupport.error("Missing or unsupported command [" + instruction.getCommand()
//                    + "] in instruction.");
//            return;
//        }
//        LOG.debug("Received command: {}", command);
//        switch (command) {
//
//        case SEND_PROJECTMESSAGE:
//        case REPLAY:
//        case TEST_EXPRESSION:
//            // not handled here.
//            LOG.debug("Ignoring command: {}", command);
//            return;
//
//        default:
//
//            return;
//        }
//        LOG.debug("Handled command: {} (result={}) on process: {}{}", command, instructionSupport.isError() ? "error"
//                : "ok", instructionSupport.getProcessPath(), instructionSupport.getActivityId() == null ? "" : "#"
//                + instructionSupport.getActivityId());
//    }
//
//
//
//
//}
