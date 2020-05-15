package com.im.njams.sdk.communication.cloud;

import java.security.KeyStore;

import org.slf4j.LoggerFactory;

import com.amazonaws.services.iot.client.AWSIotMqttClient;

public class CustomAWSIotMqttClient extends AWSIotMqttClient {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CustomAWSIotMqttClient.class);

    CloudReceiver receiver;

    public CustomAWSIotMqttClient(String clientEndpoint, String clientId, KeyStore keyStore, String keyPassword,
            CloudReceiver receiver) {

        super(clientEndpoint, clientId, keyStore, keyPassword);
        this.receiver = receiver;
    }

    @Override
    public void onConnectionSuccess() {
        super.onConnectionSuccess();
        LOG.info("Resend onConnectMessages");
        try {
            receiver.resendOnConnect();
        } catch (Exception e) {
            // connection couldn't be fully recovered
            LOG.warn("Failed resend onConnect messages: {}", e);
        }
    }

}
