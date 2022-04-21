package com.im.njams.sdk;

import com.im.njams.sdk.njams.mock.MockingNjamsFactory;
import com.im.njams.sdk.njams.mock.NjamsReceiverMock;
import org.junit.Before;
import org.junit.Test;

public class NjamsWithNjamsReceiverStartTest {

    private Njams njams;
    private NjamsReceiverMock njamsReceiverMock;

    @Before
    public void setUp() {
        final MockingNjamsFactory mockingNjamsFactory = new MockingNjamsFactory();
        njams = new Njams(mockingNjamsFactory);

        njamsReceiverMock = mockingNjamsFactory.getNjamsReceiver();
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
