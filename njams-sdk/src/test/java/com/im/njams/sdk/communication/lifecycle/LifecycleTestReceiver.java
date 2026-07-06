package com.im.njams.sdk.communication.lifecycle;

import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;

/**
 * Fake receiver whose connect always succeeds — pairs with {@link LifecycleTestSender} under the same
 * transport name so a real Njams can start with a controllable sender.
 */
public class LifecycleTestReceiver extends AbstractReceiver {

    @Override
    public String getName() {
        return LifecycleTestTransport.NAME;
    }

    @Override
    public void connect() {
        connectionStatus = ConnectionStatus.CONNECTED;
    }

    @Override
    public void stop() {
        connectionStatus = ConnectionStatus.DISCONNECTED;
    }
}
