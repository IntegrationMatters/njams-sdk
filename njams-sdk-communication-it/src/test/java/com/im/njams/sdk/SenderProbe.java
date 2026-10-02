package com.im.njams.sdk;

import com.im.njams.sdk.communication.NjamsSender;

/**
 * Test accessor for the package-private {@code Njams.sender()}. Lives in the SDK's package on purpose, because
 * the sender is internal SDK infrastructure that is not accessible from other packages.
 */
public final class SenderProbe {

    private SenderProbe() {
    }

    /**
     * @param njams the client instance
     * @return the (lazily created) sender of the given instance
     */
    public static NjamsSender of(Njams njams) {
        return njams.sender();
    }
}
