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
 *
 */

package com.im.njams.sdk.njams.receiver;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsFactory;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.instructionListener.PingInstructionListener;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.configuration.ConfigurationInstructionListener;
import com.im.njams.sdk.njams.util.NjamsFactoryUtils;
import com.im.njams.sdk.njams.NjamsJobs;
import com.im.njams.sdk.njams.NjamsProjectMessage;
import com.im.njams.sdk.njams.metadata.NjamsMetadataFactory;
import com.im.njams.sdk.settings.Settings;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class NjamsInstructionListenerAddedBeforeReceiverStartIT {

    @Test
    public void instructionListeners_defaultsAreAllSet_beforeRealReceiver_isStarted(){
        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestReceiver.NAME);

        NjamsFactory njamsFactory = NjamsFactoryUtils.createMinimalNjamsFactory();
        Njams njams = new Njams(njamsFactory);

        InstructionListenerSizeCheckerMock instructionListenerSizeCheckerMock = new InstructionListenerSizeCheckerMock();
        instructionListenerSizeCheckerMock.setNjamsReceiver(njamsFactory.getNjamsReceiver());
        TestReceiver.setReceiverMock(instructionListenerSizeCheckerMock);

        Map<String, Object> neededInstructionListeners = new HashMap<>();
        neededInstructionListeners.put("Listening for Ping", new PingInstructionListener(null, null));
        neededInstructionListeners.put("Listening for SendProjectMessage", new NjamsProjectMessageStub());
        neededInstructionListeners.put("Listening for Replay", new NjamsJobsStub());
        neededInstructionListeners.put("Listening for all configuration related command", new ConfigurationInstructionListener(null));

        int numberOfInstructionListeners = neededInstructionListeners.size();
        instructionListenerSizeCheckerMock.checkForSize(numberOfInstructionListeners);
        njams.start();
    }

    private static class InstructionListenerSizeCheckerMock extends AbstractReceiver{

        private int sizeCheck;

        @Override
        public void connect() {

        }

        @Override
        public String getName() {
            return "InstructionListeningCounterMock";
        }

        @Override
        public void init(Properties properties) {

        }

        @Override
        public void stop() {

        }

        @Override
        public void start() {
            final List<InstructionListener> availableInstructionListeners = getNjamsReceiver().getInstructionListeners();
            assertThat(availableInstructionListeners.size(), is(equalTo(sizeCheck)));
        }

        public void checkForSize(int numberOfInstructionListeners) {
            sizeCheck = numberOfInstructionListeners;
        }
    }

    private class NjamsProjectMessageStub extends NjamsProjectMessage{
        public NjamsProjectMessageStub() {
            super(NjamsMetadataFactory.createMetadataWith(new Path(), "client", "SDK"), null, null, null, null, null, new Settings());
        }
    }

    private static class NjamsJobsStub extends NjamsJobs {
        public NjamsJobsStub() {
            super(null, null, null, null);
        }
    }
}
