package com.im.njams.sdk.communication.kafka;

import static com.im.njams.sdk.communication.kafka.KafkaConstants.ADMIN_PREFIX;
import static com.im.njams.sdk.communication.kafka.KafkaConstants.CLIENT_PREFIX;
import static com.im.njams.sdk.communication.kafka.KafkaConstants.CONSUMER_PREFIX;
import static com.im.njams.sdk.communication.kafka.KafkaConstants.PRODUCER_PREFIX;

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

import com.im.njams.sdk.utils.StringUtils;

/**
 * Provides some utilities related to Kafka communication.
 *
 * @author cwinkler
 *
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
        /** Admin client connection */
        ADMIN(ADMIN_PREFIX, AdminClientConfig.configNames()),
        /** Consumer connection */
        CONSUMER(CONSUMER_PREFIX, ConsumerConfig.configNames()),
        /** Producer connection */
        PRODUCER(PRODUCER_PREFIX, ProducerConfig.configNames());

        private final PropertyFilter clientFilter;
        private final PropertyFilter typeFilter;

        private ClientType(final String pfx, final Collection<String> keys) {
            clientFilter = new PropertyFilter(CLIENT_PREFIX, keys);
            typeFilter = new PropertyFilter(pfx, keys);
        }
    }

    private KafkaUtil() {
        // static
    }

    /**
     * Uses the given Kafka properties for connecting and checking the given topics for existence.
     * @param njamsProperties nJAMS client properties
     * @param topics Topics to test.
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
     * Filters properties starting with {@link KafkaConstants#CLIENT_PREFIX} and cuts the prefix for using them as
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
     * Filters properties starting with {@link KafkaConstants#CLIENT_PREFIX} and cuts the prefix for using them as
     * configuration for the Kafka client.
     *
     * @param properties The original client properties.
     * @param clientType Indicates the Kafka client type for that the resulting properties shall be applicable.
     * @param clientId If the resulting properties do not contain <code>client.id</code> or <code>group.id</code>,
     * this values is used for the according setting.
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

        if (StringUtils.isNotBlank(clientId)) {
            if (!kafkaProperties.containsKey(ConsumerConfig.CLIENT_ID_CONFIG)) {
                kafkaProperties.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
            }
            if (clientType == ClientType.CONSUMER && !kafkaProperties.containsKey(ConsumerConfig.GROUP_ID_CONFIG)) {
                kafkaProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, clientId);
            }
        }
        LOG.debug("Filtered Kafka {} properties: {}", clientType, kafkaProperties);
        return kafkaProperties;
    }

    /**
     * Maps the given nJAMS setting key to a Kafka config key if applicable.
     * @param njamsKey The original type.
     * @param filter Property filter object
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
}
