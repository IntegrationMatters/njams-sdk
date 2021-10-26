package com.im.njams.sdk.communication.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaUtil {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaUtil.class);

    private KafkaUtil() {
        // static
    }

    /**
     * Uses the given Kafka properties for connecting and checking the given topics for existence.
     * @param kafkaProperties Kafka connection properties.
     * @param topics Topics to test.
     * @return The set of topics that have been found, i.e., topics that are not returned have not been found.
     */
    public static Collection<String> testTopics(Properties kafkaProperties, String... topics) {
        final Collection<String> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(kafkaProperties, new StringDeserializer(), new StringDeserializer())) {
            final Map<String, List<PartitionInfo>> topicMap = consumer.listTopics(Duration.ofSeconds(5));
            if (topics != null && topics.length > 0) {
                for (String topic : topics) {
                    if (topicMap.containsKey(topic)) {
                        found.add(topic);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to list topics", e);
        }
        return found;
    }

}
