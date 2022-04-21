package com.im.njams.sdk;

import com.im.njams.sdk.njams.mock.MockingNjamsFactory;
import com.im.njams.sdk.njams.mock.NjamsReceiverMock;
import org.junit.Before;
import org.junit.Test;

public class NjamsWithNjamsReceiverStopTest {

    private Njams njams;
    private NjamsReceiverMock njamsReceiverMock;

    @Before
    public void setUp() {
        njams = new Njams(new MockingNjamsFactory());

        njamsReceiverMock = new NjamsReceiverMock();
        njamsReceiverMock = (NjamsReceiverMock) njams.getNjamsReceiver();
    }

    @Test
    public void callsStop_njamsReceiverIsNotStoppedBeforeStarting(){
        try {
            njams.stop();
        }catch(Exception someException){
            //We don't care for the exception
        }
        njamsReceiverMock.assertThatStopWasCalledTimes(0);
    }

    @Test
    public void callsStop_afterAStart_njamsReceiverStopIsCalled(){
        njams.start();
        njams.stop();

        njamsReceiverMock.assertThatStopWasCalledTimes(1);
    }

    @Test
    public void callsStopTwice_afterAStart_firstStopStopsNjamsReceiver_secondOneIsNotExecuted(){
        njams.start();
        njams.stop();
        try {
            njams.stop();
        }catch(Exception someException){
            //We don't care for the exception
        }
        njamsReceiverMock.assertThatStopWasCalledTimes(1);
    }

    @Test
    public void callsStart_andStop_alternating_afterEachStartAStopStopsNjamsReceiver(){
        njams.start();
        njams.stop();
        njams.start();
        njams.stop();

        njamsReceiverMock.assertThatStopWasCalledTimes(2);
    }

    @Test
    public void callsStartTwice_stopStillOnlyCallsOneStopOnNjamsReceiver(){
        njams.start();
        njams.start();
        njams.stop();
        try {
            njams.stop();
        }catch(Exception someException){
            //We don't care for the exception
        }
        njamsReceiverMock.assertThatStopWasCalledTimes(1);
    }

}
