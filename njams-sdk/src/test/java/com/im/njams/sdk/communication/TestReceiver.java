/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import java.util.Properties;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.njams.communication.receiver.NjamsReceiver;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.settings.Settings;

/**
 * Dummy implementation for testing.<br>
 * <b>Note:</b> For using this instance, the test environment needs to have a the full qualified class name of this
 * {@link TestReceiver} in the <code>META_INF/services/com.im.njams.sdk.communication.Receiver</code> file.
 *
 */
public class TestReceiver implements Receiver {

    private static Receiver receiver = null;
    public static final String NAME = TestSender.NAME;

    /**
     * Delegates all request to the given receiver.<br>
     * <b>Note:</b> {@link #getName()} is invoked on the given receiver but the value returned is always {@link #NAME}.
     * @param receiver
     */
    public static void setReceiverMock(Receiver receiver) {
        TestReceiver.receiver = receiver;
    }

    /**
     * Returns a settings prepared for using this receiver implementation.
     * @return
     */
    public static Settings getSettings() {
        return TestSender.getSettings();
    }

    @Override
    public String getName() {
        if (receiver != null) {
            receiver.getName();
        }
        return NAME;
    }

    @Override
    public void init(Properties properties) {
        if (receiver != null) {
            receiver.init(properties);
        }

    }

    @Override
    public void onInstruction(Instruction instruction) {
        if (receiver != null) {
            receiver.onInstruction(instruction);
        }

    }

    @Override
    public void start() {
        if (receiver != null) {
            receiver.start();
        }

    }

    @Override
    public void stop() {
        if (receiver != null) {
            receiver.stop();
        }

    }

    @Override
    public void setNjamsReceiver(NjamsReceiver njamsReceiver) {
        if (receiver != null){
            receiver.setNjamsReceiver(njamsReceiver);
        }
    }

    @Override
    public NjamsReceiver getNjamsReceiver() {
        return null;
    }


    @Override
    public void setInstanceMetadata(NjamsMetadata njamsMetadata) {

    }

    @Override
    public NjamsMetadata getInstanceMetadata() {
        return null;
    }
}
