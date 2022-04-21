package com.im.njams.sdk;

import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.instructionListener.PingInstructionListener;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.configuration.ConfigurationInstructionListener;
import com.im.njams.sdk.njams.NjamsFactoryUtils;
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
