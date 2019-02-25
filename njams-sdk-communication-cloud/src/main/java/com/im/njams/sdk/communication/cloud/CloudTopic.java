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
package com.im.njams.sdk.communication.cloud;

import com.amazonaws.services.iot.client.AWSIotMessage;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.amazonaws.services.iot.client.AWSIotTopic;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.utils.JsonUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pnientiedt
 */
public class CloudTopic extends AWSIotTopic {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CloudTopic.class);

    private CloudReceiver receiver;

    public CloudTopic(CloudReceiver receiver) {
        super(receiver.getTopicName(), receiver.getQos());
        this.receiver = receiver;
    }

    @Override
    public void onMessage(AWSIotMessage message) {
        try {
            LOG.info("Received message on topic {}:\n{}", message.getTopic(), message.getStringPayload());
            Instruction instruction = getInstruction(message);
            if (instruction != null) {
                String uuid = getUUID(instruction);
                if (uuid != null) {
                    receiver.onInstruction(instruction);
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
            String replyTopic = "/" + receiver.getInstanceId() + "/replies/";
            AWSIotMessage replyMessage = new AWSIotMessage(replyTopic, AWSIotQos.QOS1);
            replyMessage.setStringPayload(response);
            receiver.getMqttclient().publish(replyMessage);
            LOG.debug("Sent reply on {}:\n{}", replyTopic, response);
        } catch (Exception e) {
            LOG.error("Error while sending reply for {}", uuid, e);
        }
    }

}
