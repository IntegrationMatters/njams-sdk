package com.im.njams.sdk.communication.cloud;

import java.util.UUID;

public class CloudClientId {

    private static CloudClientId cloudClientId = null;
    public String clientId;

    private CloudClientId(String instanceId) {
        String clientIdRaw = UUID.randomUUID().toString();
        clientId = instanceId + "_" + clientIdRaw;
    }

    public static CloudClientId getInstance(String instanceId) {
        if (cloudClientId == null) {
            cloudClientId = new CloudClientId(instanceId);
        }

        return cloudClientId;
    }

}