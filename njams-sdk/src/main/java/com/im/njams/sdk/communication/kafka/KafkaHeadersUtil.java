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

/**
 * Provides utilities for working with Kafka {@link Headers}.
 * @author cwinkler
 *
 */
public class KafkaHeadersUtil {
    /**
     * Support class for chaining {@link Header} updates.
     */
    public static class HeadersUpdater {
        private final Headers headers;

        private HeadersUpdater(Headers headers) {
            this.headers = Objects.requireNonNull(headers);
        }

        /**
         * Conditionally add a header.
         * @param name The name of the header.
         * @param value The value of the header.
         * @param condition Condition function getting the key and value as input arguments.
         * @return This instance for chaining updates.
         */
        public HeadersUpdater addHeader(String name, String value, BiFunction<String, String, Boolean> condition) {
            if (condition.apply(name, value)) {
                return addHeader(name, value);
            }
            return this;
        }

        /**
         * Add a header.
         * @param name The name of the header.
         * @param value The value of the header.
         * @return This instance for chaining updates.
         */
        public HeadersUpdater addHeader(String name, String value) {
            headers.add(name, String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return this;
        }

        /**
         * Conditionally remove a header.
         * @param name The name of the header to be removed.
         * @param condition Condition function getting the key and the currently stored value (or <code>null</code>)
         * as input arguments.
         * @return This instance for chaining updates.
         */
        public HeadersUpdater removeHeader(String name, BiFunction<String, String, Boolean> condition) {
            if (condition.apply(name, getHeader(headers, name))) {
                return removeHeader(name);
            }
            return this;
        }

        /**
         * Remove a header.
         * @param name The name of the header to be removed.
         * @return This instance for chaining updates.
         */
        public HeadersUpdater removeHeader(String name) {
            headers.remove(name);
            return this;
        }
    }

    private KafkaHeadersUtil() {
        // static only
    }

    /**
     * Extracts headers from the given Kafka record and returns as common map.
     * On duplicate key, just any value survives.
     * @param record The Kafka record from that headers are extracted and converted.
     * @return A map containing headers extracted from the given record.
     */
    public static Map<String, String> convertHeaders(ConsumerRecord<?, ?> record) {
        return convertHeaders(record.headers());
    }

    /**
     * Converts the given Kafka headers to a common map.
     * On duplicate key, just any value survives.
     * @param headers The Kafka headers to be converted.
     * @return A map containing entries extracted from the given headers.
     */
    public static Map<String, String> convertHeaders(Headers headers) {
        return StreamSupport.stream(headers.spliterator(), false)
                .collect(Collectors.toMap(Header::key, h -> new String(h.value(), StandardCharsets.UTF_8), (a, b) -> b,
                        TreeMap::new));
    }

    /**
     * Extracts a specific header from the given Kafka record.
     * On duplicate name, the last value is returned.
     * @param record The Kafka record to read a header from.
     * @param name The name of the header to read.
     * @return The value stored with the header with the given name. If multiple headers are stored with the same name,
     * only the last value is returned.
     */
    public static String getHeader(ConsumerRecord<?, ?> record, String name) {
        return getHeader(record.headers(), name);
    }

    /**
     * Extracts a specific header from the given Kafka headers.
     * On duplicate name, the last value is returned.
     * @param headers The Kafka headers to read a specific header from.
     * @param name The name of the header to read.
     * @return The value stored with the header with the given name. If multiple headers are stored with the same name,
     * only the last value is returned.
     */
    public static String getHeader(Headers headers, String name) {
        final Header header = headers.lastHeader(name);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Creates a headers updater for chaining updates to the given record's headers.
     * @param record The Kafka record whose headers shall be updated.
     * @return An updater that can be used for chaining updates on the same headers.
     * @see HeadersUpdater
     */
    public static HeadersUpdater headersUpdater(ProducerRecord<?, ?> record) {
        return headersUpdater(record.headers());
    }

    /**
     * Creates a headers updater for chaining updates to the given headers.
     * @param headers The Kafka headers that shall be updated.
     * @return An updater that can be used for chaining updates on the same headers.
     * @see HeadersUpdater
     */
    public static HeadersUpdater headersUpdater(Headers headers) {
        return new HeadersUpdater(headers);
    }
}
