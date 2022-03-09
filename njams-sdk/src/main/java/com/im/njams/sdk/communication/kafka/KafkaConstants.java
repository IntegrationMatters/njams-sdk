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

/**
 *
 * @author sfaiz
 * @version 4.2.0-SNAPSHOT
 */
public class KafkaConstants {

    /** Name of the Kafka Communication Component. */
    public static final String COMMUNICATION_NAME = "KAFKA";

    // ********** General nJAMS properties

    /** Prefix for the all communication settings including those that are nJAMS specific. */
    public static final String PROPERTY_PREFIX = "njams.sdk.communication.kafka.";
    /** Prefix only for the Kafka specific communication settings that are directly passed to the client. */

    /** Property key for the communication properties. Specifies a prefix for resolving the actual topic names. */
    public static final String TOPIC_PREFIX = PROPERTY_PREFIX + "topicPrefix";

    /** Property key for the communication properties. Overrides the default commands topic. */
    public static final String COMMANDS_TOPIC = PROPERTY_PREFIX + "commandsTopic";

    /**
     * Property key for the communication properties. Timeout after last use of the
     * KafkaProducer, who is responsible for responding to Commands.
     */
    public static final String REPLY_PRODUCER_IDLE_TIME = PROPERTY_PREFIX + "replyProducerIdleTime";
    /** The default topic prefix to be used if {@link #TOPIC_PREFIX} is not set. */
    public static final String DEFAULT_TOPIC_PREFIX = "njams";

    /*
     * ********** Kafka client related prefixes
     *
     * You can find all possible Kafka properties under -
     * https://kafka.apache.org/10/javadoc/org/apache/kafka/clients/consumer/ConsumerConfig.html
     * https://kafka.apache.org/10/javadoc/org/apache/kafka/clients/producer/ProducerConfig.html
     * https://kafka.apache.org/10/javadoc/org/apache/kafka/clients/admin/AdminClientConfig.html
     */

    /** Prefix for properties that shall be applied to all types of Kafka clients */
    public static final String CLIENT_PREFIX = PROPERTY_PREFIX + "client.";
    /** Prefix for properties that shall be applied to Kafka consumers */
    public static final String CONSUMER_PREFIX = CLIENT_PREFIX + "consumer.";
    /** Prefix for properties that shall be applied to Kafka producers */
    public static final String PRODUCER_PREFIX = CLIENT_PREFIX + "producer.";
    /** Prefix for properties that shall be applied to the Kafka admin client */
    public static final String ADMIN_PREFIX = CLIENT_PREFIX + "admin.";

    private KafkaConstants() {
        // constants
    }

}
