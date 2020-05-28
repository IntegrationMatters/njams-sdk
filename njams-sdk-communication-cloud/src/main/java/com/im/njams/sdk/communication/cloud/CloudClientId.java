package com.im.njams.sdk.communication.cloud;

import java.util.UUID;

public class CloudClientId {

    private static CloudClientId cloudClientId = null;
    public String clientId;
    public int suffix = 0;

    private CloudClientId(String instanceId) {
        String clientIdRaw = UUID.randomUUID().toString();
        clientId = instanceId + "_" + clientIdRaw;
       
    }

    public static CloudClientId getInstance(String instanceId, Boolean isSharedReceiver) {
        if (cloudClientId == null) {
            cloudClientId = new CloudClientId(instanceId);
            return cloudClientId;
        }
        
        // if receiver is shared always return same cloudClientId with same suffix
        if(isSharedReceiver) {
            return cloudClientId;
        }
        
        // otherwise increase suffix
        cloudClientId.suffix++;

        return cloudClientId;

    }

}