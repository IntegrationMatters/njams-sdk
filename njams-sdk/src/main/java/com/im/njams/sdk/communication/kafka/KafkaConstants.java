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
 *
 */
public class KafkaConstants {

    private KafkaConstants() {
        // constants
    }

    /**
     * Prefix for the Kafka communication
     */
    public static final String PROPERTY_PREFIX = "njams.sdk.communication.kafka";

    /**
     * Name of the Kafka Communication Component.
     */
    public static final String COMMUNICATION_NAME = "Kafka";

    /**
     * Property key for the communication properties. Specifies the destination.
     */
    public static final String DESTINATION = PROPERTY_PREFIX + ".destination";

    /**
     * Property key for the communication properties. Specifies the commands
     * destination.
     */
    public static final String COMMANDS_DESTINATION = PROPERTY_PREFIX + ".destination.commands";

    /**
     * Property key for the communication properties. Timeout after last use of the
     * KafkaProducer, who is responsible for responding to Commands.
     */
    public static final String IDLE_COMMANDS_RESPONSE_PRODUCER_TIMEOUT = PROPERTY_PREFIX
            + "idleCommandsResponseProducerTimeout";

    /**
     * You can find all possible Kafka properties under -
     * https://kafka.apache.org/10/javadoc/org/apache/kafka/clients/consumer/ConsumerConfig.html
     * -
     * https://kafka.apache.org/10/javadoc/org/apache/kafka/clients/producer/ProducerConfig.html
     */
}
