package com.im.njams.sdk;

import com.im.njams.sdk.njams.NjamsReceiver;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class NjamsReceiverMock extends NjamsReceiver {

    private int startCounter;
    private int stopCounter;

    NjamsReceiverMock() {
        super(null, null, null, null, null, null);
    }

    @Override
    public void start() {
        startCounter++;
    }

    @Override
    public void stop() {
        stopCounter++;
    }

    void assertThatStartWasCalledTimes(int times) {
        assertThat(startCounter, is(equalTo(times)));
    }

    public void assertThatStopWasCalledTimes(int times) {
        assertThat(stopCounter, is(equalTo(times)));
    }
}
