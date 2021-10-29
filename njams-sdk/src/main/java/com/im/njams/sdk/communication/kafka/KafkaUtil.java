package com.im.njams.sdk.communication.kafka;

import static com.im.njams.sdk.communication.kafka.KafkaConstants.ADMIN_PREFIX;
import static com.im.njams.sdk.communication.kafka.KafkaConstants.CONSUMER_PREFIX;
import static com.im.njams.sdk.communication.kafka.KafkaConstants.PRODUCER_PREFIX;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
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

    private static final Logger LOG = LoggerFactory.getLogger(KafkaUtil.class);

    /**
     * Kafka client connection type.
     */
    public enum ClientType {
        /** Admin client connection */
        ADMIN,
        /** Consumer connection */
        CONSUMER,
        /** Producer connection */
        PRODUCER
    };

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

        final Entry<String, Collection<String>> filter = getPropertiesFilter(clientType);
        // add keys from CLIENT_PREFIX
        properties.stringPropertyNames().stream()
        .map(k -> getKafkaKey(k, KafkaConstants.CLIENT_PREFIX, filter.getValue())).filter(Objects::nonNull)
        .forEach(e -> kafkaProperties.setProperty(e.getValue(), properties.getProperty(e.getKey())));
        // override with keys from type specific prefix
        properties.stringPropertyNames().stream().map(k -> getKafkaKey(k, filter.getKey(), filter.getValue()))
        .filter(Objects::nonNull)
        .forEach(e -> kafkaProperties.setProperty(e.getValue(), properties.getProperty(e.getKey())));

        if (StringUtils.isNotBlank(clientId)) {
            if (!kafkaProperties.containsKey(ConsumerConfig.CLIENT_ID_CONFIG)) {
                kafkaProperties.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
            }
            if (!kafkaProperties.containsKey(ConsumerConfig.GROUP_ID_CONFIG)) {
                kafkaProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, clientId);
            }
        }
        LOG.debug("Filtered Kafka {} properties: {}", clientType, kafkaProperties);
        return kafkaProperties;
    }

    /**
     * @param clientType The intended Kafka usage type.
     * @return A filter containing the property prefix to use and the set of supported Kafka config keys.
     */
    private static Entry<String, Collection<String>> getPropertiesFilter(final ClientType clientType) {
        final String prefix;
        final Collection<String> kafkaKeys;

        if (clientType == null) {
            prefix = CONSUMER_PREFIX;
            kafkaKeys = ConsumerConfig.configNames();

        } else {
            switch (clientType) {
            case ADMIN:
                prefix = ADMIN_PREFIX;
                kafkaKeys = AdminClientConfig.configNames();
                break;
            case PRODUCER:
                prefix = PRODUCER_PREFIX;
                kafkaKeys = ProducerConfig.configNames();
                break;
            case CONSUMER:
                // intentional fall-through
            default:
                prefix = CONSUMER_PREFIX;
                kafkaKeys = ConsumerConfig.configNames();
                break;
            }
        }
        return new AbstractMap.SimpleImmutableEntry<>(prefix, kafkaKeys);
    }

    /**
     * Maps the given nJAMS setting key to a Kafka config key if applicable.
     * @param njamsKey The original type.
     * @param prefix The prefix for extracting the Kafka key
     * @param kafkaKeys The set of supported keys
     * @return Entry containing the original key as key and the truncated Kafka key as value, or <code>null</code>
     * if the resulting key is not a valid Kafka config key.
     */
    private static Entry<String, String> getKafkaKey(final String njamsKey, final String prefix,
            final Collection<String> kafkaKeys) {
        if (!njamsKey.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }

        final String key = njamsKey.substring(prefix.length());
        if (kafkaKeys.contains(key)) {
            return new AbstractMap.SimpleImmutableEntry<>(njamsKey, key);
        }
        return null;
    }
}
