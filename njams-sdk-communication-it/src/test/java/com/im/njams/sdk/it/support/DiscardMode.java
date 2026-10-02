package com.im.njams.sdk.it.support;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.DiscardPolicy;
import com.im.njams.sdk.settings.ClientSettings;

/** The three SDK discard policies; every fault scenario runs once per mode (spec §6.1). */
public enum DiscardMode {
    NONE(DiscardPolicy.NONE),
    ON_CONNECTION_LOSS(DiscardPolicy.ON_CONNECTION_LOSS),
    DISCARD(DiscardPolicy.DISCARD);

    private final DiscardPolicy policy;

    DiscardMode(DiscardPolicy policy) {
        this.policy = policy;
    }

    public void apply(ClientSettings settings) {
        settings.put(NjamsSettings.PROPERTY_DISCARD_POLICY, policy.toString());
    }

    /** {@code true} if this mode never drops a message on a connection problem but holds it (back-pressure). */
    public boolean holdsMessages() {
        return this == NONE;
    }

    @Override
    public String toString() {
        return policy.toString();
    }
}
