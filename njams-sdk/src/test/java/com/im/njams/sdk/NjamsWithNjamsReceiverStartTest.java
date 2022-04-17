package com.im.njams.sdk;

import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

public class NjamsWithNjamsReceiverStartTest {

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

        njamsReceiverMock.assertThatStartWasCalledTimes(1);
    }

    @Test
    public void callsStart_aSecondTime_butNjamsReceiverStartIsStillOnlyCalledOnce(){
        njams.start();
        njams.start();

        njamsReceiverMock.assertThatStartWasCalledTimes(1);
    }

    @Test
    public void callsStart_aSecondTime_afterAStopInBetween_callsStartOnNjamsReceiverASecondTime(){
        njams.start();
        njams.stop();
        njams.start();

        njamsReceiverMock.assertThatStartWasCalledTimes(2);
    }
}
