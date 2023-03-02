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
 * @author sfaiz
 * @version 4.2.0-SNAPSHOT
 */
public class KafkaConstants {

    /**
     * Name of the Kafka Communication Component.
     */
    public static final String COMMUNICATION_NAME = "KAFKA";

    /**
     * The default topic prefix to be used if {@link com.im.njams.sdk.NjamsSettings#PROPERTY_KAFKA_TOPIC_PREFIX} is not set.
     */
    public static final String DEFAULT_TOPIC_PREFIX = "njams";

    /*
     * ********** Kafka client related prefixes
     *
     * You can find all possible Kafka properties under -
     * https://kafka.apache.org/10/javadoc/org/apache/kafka/clients/consumer/ConsumerConfig.html
     * https://kafka.apache.org/10/javadoc/org/apache/kafka/clients/producer/ProducerConfig.html
     * https://kafka.apache.org/10/javadoc/org/apache/kafka/clients/admin/AdminClientConfig.html
     */

    private KafkaConstants() {
        // constants
    }

}
