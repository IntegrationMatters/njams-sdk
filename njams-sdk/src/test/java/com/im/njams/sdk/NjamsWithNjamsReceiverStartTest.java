package com.im.njams.sdk;

import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.njams.mock.MockingNjamsFactory;
import com.im.njams.sdk.njams.mock.NjamsReceiverMock;
import com.im.njams.sdk.settings.Settings;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class NjamsWithNjamsReceiverStartTest {

    private Njams njams;
    private NjamsReceiverMock njamsReceiverMock;

    @BeforeClass
    public static void setNjamsFactory(){
        Njams.setNjamsFactory(new MockingNjamsFactory());
    }

    @AfterClass
    public static void cleanUp(){
        Njams.setNjamsFactory(null);
    }

    @Before
    public void setUp() {
        njams = new Njams(new Path(), "SDK", new Settings());

        njamsReceiverMock = (NjamsReceiverMock) njams.getNjamsReceiver();
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
