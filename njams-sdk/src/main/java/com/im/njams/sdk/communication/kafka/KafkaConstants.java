/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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

package com.im.njams.sdk.communication.kafka;

import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.utils.StringUtils;

/**
 *
 * @author sfaiz
 * @version 4.2.0-SNAPSHOT
 */
public class KafkaConstants {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConstants.class);

    private KafkaConstants() {
        // constants
    }

    /**
     * Prefix for the Kafka communication
     */
    public static final String PROPERTY_PREFIX = "njams.sdk.communication.kafka.";

    /**
     * Name of the Kafka Communication Component.
     */
    public static final String COMMUNICATION_NAME = "Kafka";

    /**
     * Property key for the communication properties. Specifies the destination.
     */
    public static final String TOPIC_PREFIX = PROPERTY_PREFIX + "topic";

    /**
     * Property key for the communication properties. Specifies the commands
     * destination.
     */
    public static final String COMMANDS_TOPIC = TOPIC_PREFIX + ".commands";

    /**
     * Property key for the communication properties. Timeout after last use of the
     * KafkaProducer, who is responsible for responding to Commands.
     */
    public static final String REPLY_PRODUCER_IDLE_TIME = PROPERTY_PREFIX + "replyProducerIdleTime";

    /*
     * You can find all possible Kafka properties under -
     * https://kafka.apache.org/10/javadoc/org/apache/kafka/clients/consumer/ConsumerConfig.html
     * -
     * https://kafka.apache.org/10/javadoc/org/apache/kafka/clients/producer/ProducerConfig.html
     */

    /**
     * Filters properties starting with {@value #PROPERTY_PREFIX} and cuts the prefix for using them as configuration
     * for the Kafka client.
     *
     * @param properties The original client properties.
     * @return Properties instance prepared to be used as Kafka client configuration.
     */
    public static Properties filterKafkaProperties(Properties properties) {
        return filterKafkaProperties(properties, null);
    }

    /**
     * Filters properties starting with {@value #PROPERTY_PREFIX} and cuts the prefix for using them as configuration
     * for the Kafka client.
     *
     * @param properties The original client properties.
     * @param clientId If the resulting properties do not contain <code>client.id</code> or <code>group.id</code>,
     * this values is used for the according setting.
     * @return Properties instance prepared to be used as Kafka client configuration.
     */
    public static Properties filterKafkaProperties(Properties properties, String clientId) {
        final Properties kafkaProperties = new Properties();
        properties.stringPropertyNames().stream().filter(k -> k.startsWith(KafkaConstants.PROPERTY_PREFIX))
        .forEach(k -> kafkaProperties.setProperty(k.substring(KafkaConstants.PROPERTY_PREFIX.length()),
                properties.getProperty(k)));
        if (StringUtils.isNotBlank(clientId)) {
            if (!kafkaProperties.containsKey(ConsumerConfig.CLIENT_ID_CONFIG)) {
                kafkaProperties.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
            }
            if (!kafkaProperties.containsKey(ConsumerConfig.GROUP_ID_CONFIG)) {
                kafkaProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, clientId);
            }
        }
        LOG.debug("Filtered Kafka properties: {}", kafkaProperties);
        return kafkaProperties;
    }

}
