/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.cloud.connectable;

import com.amazonaws.services.iot.client.AWSIotMessage;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.amazonaws.services.iot.client.AWSIotTopic;
import com.amazonaws.services.iot.client.core.AwsIotTopicCallback;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.cloud.*;
import com.im.njams.sdk.communication.cloud.connector.receiver.CloudReceiverConnector;
import com.im.njams.sdk.communication.connectable.AbstractReceiver;
import com.im.njams.sdk.communication.connector.Connector;
import com.im.njams.sdk.utils.JsonUtils;
import com.im.njams.sdk.utils.StringUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import javax.net.ssl.HttpsURLConnection;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pnientiedt
 */
public class CloudReceiver extends AbstractReceiver implements AwsIotTopicCallback {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CloudReceiver.class);

    @Override
    public String getName() {
        return CloudConstants.COMMUNICATION_NAME;
    }

    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#ENDPOINT}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#APIKEY}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#CLIENT_INSTANCEID}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#CLIENT_CERTIFICATE}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#CLIENT_PRIVATEKEY}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#MAX_PAYLOAD_BYTES}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    protected Connector initialize(Properties properties) {
        CloudReceiverConnector cloudReceiverConnector = new CloudReceiverConnector(properties, getName() + Connector.SENDER_NAME_ENDING, njams);
        AWSIotTopic topic = new CloudMessageListener(this, cloudReceiverConnector.getTopicName(), CloudReceiverConnector.QOS);
        cloudReceiverConnector.setTopic(topic);
        return cloudReceiverConnector;

    }

    /**
     * Every time onInstruction in the AbstractReceiver is called, the instruction's
     * request is extended by this method.
     * 
     * @param request the request that will be extended
     * @return an exception response if there was a problem while retrieving payload
     * from nJAMS Cloud. If everything worked fine, it returns null.
     */
    @Override
    protected Response extendRequest(Request request) {
        String payloadUrl = "";
        Map<String, String> parameters = request.getParameters();
        if(parameters != null){
            payloadUrl = parameters.get("PayloadUrl");
        }
        if (StringUtils.isNotBlank(payloadUrl)) {
            try {
                parameters.put("Payload", getPayload(payloadUrl));
            } catch (Exception e) {
                LOG.warn("Error while retrieving payload from nJAMS Cloud {}", request.getCommand());
                Response response = new Response();
                response.setResultCode(1);
                response.setResultMessage("Error while retrieving payload from nJAMS Cloud.");
                return response;
            }
        }
        //Everything worked fine
        return null;
    }

    private String getPayload(String presignedUrl) throws Exception {
        URL url = new URL(presignedUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() != 200) {
            throw new NjamsSdkRuntimeException("Error while retrieving payload");
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }

    @Override
    public void onMessage(AWSIotMessage message) {
        try {
            LOG.info("Received message on topic {}:\n{}", message.getTopic(), message.getStringPayload());
            Instruction instruction = getInstruction(message);
            if (instruction != null) {
                String uuid = getUUID(instruction);
                if (uuid != null) {
                    super.onInstruction(instruction);
                    reply(instruction, uuid);
                } else {
                    LOG.error("Received message on topic {} does not contain a valid mqttUuid. Ignore!");
                }
            } else {
                LOG.warn("Received message on topic {} is not a valid instruction. Ignore!", message.getTopic());
            }
        } catch (Exception e) {
            LOG.error("Error in onMessage", e);
        }
    }

    public Instruction getInstruction(AWSIotMessage message) {
        try {
            Instruction instruction = JsonUtils.parse(message.getStringPayload(), Instruction.class);
            if (instruction.getRequest() != null) {
                return instruction;
            }
        } catch (Exception e) {
            LOG.error("Error deserializing Instruction", e);
        }
        LOG.warn("MSG is not a valid Instruction");
        return null;
    }

    public String getUUID(Instruction instruction) {
        try {
            if (instruction != null && instruction.getRequest() != null
                    && instruction.getRequest().getParameters() != null) {
                return instruction.getRequest().getParameters().get("mqttUuid");
            }
        } catch (Exception e) {
            LOG.error("Error deserializing Instruction", e);
        }
        LOG.warn("Instruction does not contain a property mqttUuid");
        return null;
    }

    public void reply(Instruction instruction, String uuid) {
        try {
            // clear Payload to avoid messages exceeding limit
            instruction.setRequestParameter("Payload", "");
            instruction.setRequestParameter("PayloadUrl", "");

            String response = JsonUtils.serialize(instruction);
            String replyTopic = "/" + ((CloudReceiverConnector)connector).getInstanceId() + "/replies/";
            AWSIotMessage replyMessage = new AWSIotMessage(replyTopic, AWSIotQos.QOS1);
            replyMessage.setStringPayload(response);
            ((CloudReceiverConnector)connector).getMqttClient().publish(replyMessage);
            LOG.debug("Sent reply on {}:\n{}", replyTopic, response);
        } catch (Exception e) {
            LOG.error("Error while sending reply for {}", uuid, e);
        }
    }
}
