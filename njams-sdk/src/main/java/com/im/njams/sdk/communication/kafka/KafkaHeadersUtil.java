package com.im.njams.sdk.communication.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class KafkaHeadersUtil {
    public static class HeadersUpdater {
        private final Headers headers;

        private HeadersUpdater(Headers headers) {
            this.headers = Objects.requireNonNull(headers);
        }

        /**
         * Conditionally add a header.
         * @param key
         * @param value
         * @param condition Condition function getting the key and value as input arguments.
         * @return
         */
        public HeadersUpdater addHeader(String key, String value, BiFunction<String, String, Boolean> condition) {
            if (condition.apply(key, value)) {
                return addHeader(key, value);
            }
            return this;
        }

        public HeadersUpdater addHeader(String key, String value) {
            headers.add(key, String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return this;
        }

        /**
         * Conditionally remove a header.
         * @param key
         * @param condition Condition function getting the key and the currently stored value (or <code>null</code>)
         * as input arguments.
         * @return
         */
        public HeadersUpdater removeHeader(String key, BiFunction<String, String, Boolean> condition) {
            if (condition.apply(key, getHeader(headers, key))) {
                return removeHeader(key);
            }
            return this;
        }

        public HeadersUpdater removeHeader(String key) {
            headers.remove(key);
            return this;
        }
    }

    private KafkaHeadersUtil() {
        // static only
    }

    /**
     * Extracts headers from the given Kafka record and returns as common map.
     * On duplicate key, just any value survives.
     * @param record
     * @return
     */
    public static Map<String, String> convertHeaders(ConsumerRecord<?, ?> record) {
        return convertHeaders(record.headers());
    }

    /**
     * Converts the given Kafka headers to a common map.
     * On duplicate key, just any value survives.
     * @param headers
     * @return
     */
    public static Map<String, String> convertHeaders(Headers headers) {
        return StreamSupport.stream(headers.spliterator(), false)
                .collect(Collectors.toMap(Header::key, h -> new String(h.value(), StandardCharsets.UTF_8), (a, b) -> b,
                        TreeMap::new));
    }

    /**
     * Extracts a specific header from the given Kafka record.
     * On duplicate key, the last value is returned.
     * @param record
     * @param key
     * @return
     */
    public static String getHeader(ConsumerRecord<?, ?> record, String key) {
        return getHeader(record.headers(), key);
    }

    /**
     * Extracts a specific header from the given Kafka headers.
     * On duplicate key, the last value is returned.
     * @param headers
     * @param key
     * @return
     */
    public static String getHeader(Headers headers, String key) {
        final Header header = headers.lastHeader(key);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Creates a headers updater for chaining updates to the given record's headers.
     * @param record
     * @return
     */
    public static HeadersUpdater headersUpdater(ProducerRecord<?, ?> record) {
        return headersUpdater(record.headers());
    }

    /**
     * Creates a headers updater for chaining updates to the given headers.
     * @param headers
     * @return
     */
    public static HeadersUpdater headersUpdater(Headers headers) {
        return new HeadersUpdater(headers);
    }
}
