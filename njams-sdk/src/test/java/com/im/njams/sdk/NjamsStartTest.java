package com.im.njams.sdk;

import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class NjamsStartTest {

    private Njams njams;
    private NjamsReceiverMock njamsReceiverMock;

    @Before
    public void setUp() {
        njams = new Njams(new Path(), "SDK", new Settings());

        njamsReceiverMock = new NjamsReceiverMock();
        njams.setNjamsReceiver(njamsReceiverMock);
    }

    @Test
    public void callsStart_onNjamsReceiver(){
        njams.start();

        njamsReceiverMock.assertThatStartWasCalled(1);
    }

    @Test
    public void callsStart_aSecondTime_njamsReceiverIsOnlyCalledOnce(){
        njams.start();
        njams.start();

        njamsReceiverMock.assertThatStartWasCalled(1);
    }

    @Test
    public void callsStart_aSecondTime_afterAStopInBetween_callsStartASecondTime(){
        njams.start();
        njams.stop();
        njams.start();

        njamsReceiverMock.assertThatStartWasCalled(2);
    }

    private static class NjamsReceiverMock extends NjamsReceiver{

        private int startCounter;

        private NjamsReceiverMock() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void start(){
            startCounter++;
        }

        private void assertThatStartWasCalled(int times) {
            assertThat(startCounter, is(equalTo(times)));
        }
    }
}
