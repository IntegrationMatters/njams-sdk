/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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

import static com.im.njams.sdk.utils.PropertyUtil.getPropertyInt;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Provides some utilities related to Kafka communication.
 *
 * @author cwinkler
 */
public class KafkaUtil {

    private static class PropertyFilter {
        private final String prefix;
        private final Collection<String> supportedKeys;

        private PropertyFilter(final String prefix, final Collection<String> supportedKeys) {
            this.prefix = prefix;
            this.supportedKeys = Collections.unmodifiableCollection(supportedKeys);
        }

    }

    private static final Logger LOG = LoggerFactory.getLogger(KafkaUtil.class);

    /**
     * Kafka client connection type.
     */
    public enum ClientType {
        /**
         * Admin client connection
         */
        ADMIN(NjamsSettings.PROPERTY_KAFKA_ADMIN_PREFIX, AdminClientConfig.configNames()),
        /**
         * Consumer connection
         */
        CONSUMER(NjamsSettings.PROPERTY_KAFKA_CONSUMER_PREFIX, ConsumerConfig.configNames()),
        /**
         * Producer connection
         */
        PRODUCER(NjamsSettings.PROPERTY_KAFKA_PRODUCER_PREFIX, ProducerConfig.configNames());

        private final PropertyFilter clientFilter;
        private final PropertyFilter typeFilter;
        private final String pathToDefaults;

        private ClientType(final String pfx, final Collection<String> keys) {
            clientFilter = new PropertyFilter(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX, keys);
            typeFilter = new PropertyFilter(pfx, keys);
            pathToDefaults = "/kafka-defaults/" + name().toLowerCase() + ".properties";
        }

    }

    /** Kafka's internal default for a producer's message size limit (1MB). */
    private static final int MAX_MESSAGE_SIZE_DEFAULT = 1048576;
    /** Some overhead that is subtracted from Kafka's producer message size limit, if used. */
    public static final int HEADERS_OVERHEAD = 4096;

    private KafkaUtil() {
        // static
    }

    /**
     * Uses the given Kafka properties for connecting and checking the given topics for existence.
     *
     * @param njamsProperties nJAMS client properties
     * @param topics          Topics to test.
     * @return The set of topics that have been found, i.e., topics that are not returned have not been found.
     */
    public static Collection<String> testTopics(final Properties njamsProperties, final String... topics) {
        final Collection<String> found = new ArrayList<>();
        final Properties p = filterKafkaProperties(njamsProperties, ClientType.CONSUMER);
        p.remove(ConsumerConfig.CLIENT_ID_CONFIG);
        try (KafkaConsumer<String, String> consumer =
            new KafkaConsumer<>(p, new StringDeserializer(), new StringDeserializer())) {
            final Map<String, List<PartitionInfo>> topicMap = consumer.listTopics(Duration.ofSeconds(5));
            if (topics != null && topics.length > 0) {
                for (final String topic : topics) {
                    if (topicMap.containsKey(topic)) {
                        found.add(topic);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Filters properties starting with {@link NjamsSettings#PROPERTY_KAFKA_CLIENT_PREFIX} and cuts the prefix for using them as
     * configuration for the Kafka client.
     *
     * @param properties The original client properties.
     * @param clientType Indicates the Kafka client type for that the resulting properties shall be applicable.
     * @return Properties instance prepared to be used as Kafka client configuration.
     */
    public static Properties filterKafkaProperties(final Properties properties, final ClientType clientType) {
        return filterKafkaProperties(properties, clientType, null);
    }

    /**
     * Filters properties starting with {@link NjamsSettings#PROPERTY_KAFKA_CLIENT_PREFIX} and cuts the prefix for using them as
     * configuration for the Kafka client.
     *
     * @param properties The original client properties.
     * @param clientType Indicates the Kafka client type for that the resulting properties shall be applicable.
     * @param clientId   If the resulting properties do not contain <code>client.id</code> or <code>group.id</code>,
     *                   this values is used for the according setting.
     * @return Properties instance prepared to be used as Kafka client configuration.
     */
    public static Properties filterKafkaProperties(final Properties properties, final ClientType clientType,
        final String clientId) {
        final Properties kafkaProperties = new Properties();
        final ClientType type = clientType == null ? ClientType.CONSUMER : clientType;

        // add keys from CLIENT_PREFIX
        properties.stringPropertyNames().stream()
            .map(k -> getKafkaKey(k, type.clientFilter)).filter(Objects::nonNull)
            .forEach(e -> kafkaProperties.setProperty(e.getValue(), properties.getProperty(e.getKey())));
        // override with keys from type specific prefix
        properties.stringPropertyNames().stream().map(k -> getKafkaKey(k, type.typeFilter))
            .filter(Objects::nonNull)
            .forEach(e -> kafkaProperties.setProperty(e.getValue(), properties.getProperty(e.getKey())));

        if (StringUtils.isNotBlank(clientId) && !kafkaProperties.containsKey(ConsumerConfig.CLIENT_ID_CONFIG)) {
            kafkaProperties.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        }
        applyDefaults(kafkaProperties, clientType);
        LOG.debug("Filtered Kafka {} properties: {}", clientType, kafkaProperties);
        return kafkaProperties;
    }

    /**
     * Maps the given nJAMS setting key to a Kafka config key if applicable.
     *
     * @param njamsKey The original type.
     * @param filter   Property filter object
     * @return Entry containing the original key as key and the truncated Kafka key as value, or <code>null</code>
     * if the resulting key is not a valid Kafka config key.
     */
    private static Entry<String, String> getKafkaKey(final String njamsKey, PropertyFilter filter) {
        if (!njamsKey.regionMatches(true, 0, filter.prefix, 0, filter.prefix.length())) {
            return null;
        }

        final String key = njamsKey.substring(filter.prefix.length());
        if (filter.supportedKeys.contains(key)) {
            return new AbstractMap.SimpleImmutableEntry<>(njamsKey, key);
        }
        return null;
    }

    private static void applyDefaults(Properties kafka, final ClientType clientType) {
        final Properties defaults = new Properties();
        try (InputStream is = KafkaSender.class.getResourceAsStream(clientType.pathToDefaults)) {
            defaults.load(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Kafka default settings.", e);
        }
        for (String key : defaults.stringPropertyNames()) {
            if (!kafka.containsKey(key)) {
                kafka.setProperty(key, defaults.getProperty(key));
            }
        }
    }

    /**
     * Get Kafka's producer setting or use its default.
     * See also {@link NjamsSettings#PROPERTY_MAX_MESSAGE_SIZE}
     * @param properties The client's properties that may also contain Kafka producer settings.
     * @return The producer's max message size setting to use for message chunking.
     */
    public static int getProducerLimit(final Properties properties) {
        final Properties kafka = KafkaUtil.filterKafkaProperties(properties, ClientType.PRODUCER);
        final int kafkaLimit = getPropertyInt(kafka, ProducerConfig.MAX_REQUEST_SIZE_CONFIG, MAX_MESSAGE_SIZE_DEFAULT);
        return kafkaLimit - HEADERS_OVERHEAD;
    }

}
