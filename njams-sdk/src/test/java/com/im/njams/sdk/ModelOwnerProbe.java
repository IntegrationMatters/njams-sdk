package com.im.njams.sdk;

import com.im.njams.sdk.model.ProcessModel;

/**
 * Test accessor for the package-private owner-aware {@code NjamsModel.create(Path, Njams)}, for tests outside the
 * {@code com.im.njams.sdk} package that work with a proxied (spied) {@link Njams}: the created model must reference
 * the proxy the test is working with, not the facet's plain backreference.
 */
public final class ModelOwnerProbe {

    private ModelOwnerProbe() {
    }

    /**
     * Creates and registers a process directly below the client path of the given instance, owned by that instance
     * (typically a Mockito spy).
     *
     * @param njams       the (possibly proxied) client instance
     * @param processName the single-segment process name below the client path
     * @return the new process model, owned by <code>njams</code>
     */
    public static ProcessModel create(Njams njams, String processName) {
        return njams.model().create(njams.metadata().getClientPath().getOrCreateChild(processName), njams);
    }
}
