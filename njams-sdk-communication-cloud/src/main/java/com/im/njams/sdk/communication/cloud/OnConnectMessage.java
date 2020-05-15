package com.im.njams.sdk.communication.cloud;

public class OnConnectMessage {

    private Boolean isShared;
    private String connectionId;
    private String instanceId;
    private String path;
    private String clientVersion;
    private String sdkVersion;
    private String machine;
    private String category;

    public OnConnectMessage(Boolean isShared, String connectionId, String instanceId, String path, String clientVersion,
            String sdkVersion, String machine, String category) {
        super();
        this.isShared = isShared;
        this.connectionId = connectionId;
        this.instanceId = instanceId;
        this.path = path;
        this.clientVersion = clientVersion;
        this.sdkVersion = sdkVersion;
        this.machine = machine;
        this.category = category;
    }

    public Boolean getIsShared() {
        return isShared;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getPath() {
        return path;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public String getSdkVersion() {
        return sdkVersion;
    }

    public String getMachine() {
        return machine;
    }

    public String getCategory() {
        return category;
    }

}
