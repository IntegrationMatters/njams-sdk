/*
 * Copyright (c) 2025 Integration-Matters GmbH
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
package com.im.njams.sdk.communication;

import static com.im.njams.sdk.communication.MessageHeaders.*;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.http.HttpSender;
import com.im.njams.sdk.communication.kafka.KafkaConstants;
import com.im.njams.sdk.communication.kafka.KafkaUtil;
import com.im.njams.sdk.communication.kafka.KafkaUtil.ClientType;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Splits large messages according to the configuration setting {@link NjamsSettings#PROPERTY_MAX_MESSAGE_SIZE}.
 *
 * @author cwinkler
 *
 */
public class SplitSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SplitSupport.class);
    private static final CharsetEncoder UTF_8_ENCODER = StandardCharsets.UTF_8.newEncoder();

    /** Kafka's internal default for a producer's message size limit (1MB). */
    private static final int KAFKA_MAX_SIZE_DEFAULT = 1048576;
    /** Some overhead that is subtracted from Kafka's producer message size limit, if used. */
    public static final int KAFKA_OVERHEAD = 4096;
    /** The minimum value allowed as max message size (10kb)*/
    public static final int MIN_SIZE_LIMIT = 10240;

    private final int maxMessageBytes;

    private final String chunkNoHeader;
    private final String chunksHeader;
    private final String chunkMessageKeyHeader;

    /**
     * Constructor that initializes this instance from the given {@link Settings}.
     * @param settings {@link Settings} to be used for initializing this instance.
     */
    public SplitSupport(Settings settings) {
        this(settings.getAllProperties());
    }

    /**
     * Constructor that initializes this instance from the given {@link Properties}.
     * @param properties {@link Properties} to be used for initializing this instance.
     */
    public SplitSupport(Properties properties) {
        final String transport = properties.getProperty(NjamsSettings.PROPERTY_COMMUNICATION);
        if (transport == null) {
            throw new IllegalArgumentException("Missing setting: " + NjamsSettings.PROPERTY_COMMUNICATION);
        }

        int limit = getPropertyInt(properties, NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, -1);
        if ("true"
            .equalsIgnoreCase(properties.getProperty(NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE_NO_LIMITS, "false"))) {
            maxMessageBytes = limit;
        } else {
            if (limit > 0 && limit < MIN_SIZE_LIMIT) {
                LOG.warn(
                    "The configured max message size limit of {} bytes is less than the allowed minimum of {} bytes."
                        + " Using the minimum.",
                    limit, MIN_SIZE_LIMIT);
                limit = MIN_SIZE_LIMIT;
            }
            final boolean isKafka = KafkaConstants.COMMUNICATION_NAME.equalsIgnoreCase(transport);
            final int kafkaLimit = isKafka ? getKafkaProducerLimit(properties) : Integer.MAX_VALUE;

            if (isKafka && limit <= 0) {
                maxMessageBytes = kafkaLimit;
            } else if (limit > kafkaLimit) {
                LOG.warn("The configured max message size of {} bytes is larger than that configured for the Kafka "
                    + "client ({} bytes). Using Kafka client's setting.", limit, kafkaLimit);
                maxMessageBytes = kafkaLimit;
            } else {
                maxMessageBytes = limit;
            }
        }
        if (maxMessageBytes > 0) {
            LOG.info("Limitting max message size to {} bytes", maxMessageBytes);
        }

        if (HttpSender.NAME.equalsIgnoreCase(transport) || "HTTPS".equalsIgnoreCase(transport)) {
            chunkNoHeader = NJAMS_CHUNK_NO_HTTP_HEADER;
            chunksHeader = NJAMS_CHUNKS_HTTP_HEADER;
            chunkMessageKeyHeader = NJAMS_CHUNK_MESSAGE_KEY_HTTP_HEADER;
            LOG.debug("Using http headers.");
        } else {
            chunkNoHeader = NJAMS_CHUNK_NO_HEADER;
            chunksHeader = NJAMS_CHUNKS_HEADER;
            chunkMessageKeyHeader = NJAMS_CHUNK_MESSAGE_KEY_HEADER;
            LOG.debug("Using JMS/Kafka headers.");
        }
    }

    /**
     * Get Kafka's producer setting or use its default.
     * See also {@link NjamsSettings#PROPERTY_MAX_MESSAGE_SIZE}
     * @param properties
     * @return
     */
    private int getKafkaProducerLimit(Properties properties) {
        final Properties kafka = KafkaUtil.filterKafkaProperties(properties, ClientType.PRODUCER);
        final int kafkaLimit = getPropertyInt(kafka, ProducerConfig.MAX_REQUEST_SIZE_CONFIG, KAFKA_MAX_SIZE_DEFAULT);
        if (kafkaLimit <= MIN_SIZE_LIMIT + KAFKA_OVERHEAD) {
            LOG.error(
                "The configured Kafka client producer's message size limit of {} bytes is less than the allowed "
                    + "minimum of {} bytes. This setup is not supported.",
                kafkaLimit, MIN_SIZE_LIMIT + KAFKA_OVERHEAD);
            throw new IllegalArgumentException(
                "Illegal value " + kafkaLimit + " for setting: " + ProducerConfig.MAX_REQUEST_SIZE_CONFIG
                    + ": Is less than minimum: " + (MIN_SIZE_LIMIT + KAFKA_OVERHEAD));
        }
        return kafkaLimit - KAFKA_OVERHEAD;
    }

    /**
     * Returns whether or not a max-message size limit is active that requires splitting larger messages.
     * If <code>false</code> {@link #splitData(String)} will virtually do nothing, i.e., it will always return a list
     * with a single entry containing the whole data.
     * @return Whether splitting is active.
     */
    public boolean isSplitting() {
        return maxMessageBytes > 0;
    }

    private static int getPropertyInt(Properties properties, String key, int defaultValue) {
        final String s = properties.getProperty(key);
        if (StringUtils.isNotBlank(s)) {
            try {
                return Integer.parseInt(s);
            } catch (Exception e) {
                LOG.warn("Failed to parse value {} of property {} to int.", s, key);
            }
        }
        return defaultValue;
    }

    /**
     * Splits the given data string into chunks exactly respecting the configured (or resolved)
     * {@link NjamsSettings#PROPERTY_MAX_MESSAGE_SIZE} value (UTF-8 bytes).
     *
     * @param data The data to split.
     * @return Sorted list of chunks resolved from the given input data. The list will be empty only if
     * <code>data</code> is <code>null</code>. Otherwise it will contain at least one entry with the given input.
     */
    public List<String> splitData(final String data) {
        if (data == null) {
            return Collections.emptyList();
        }
        if (!isSplitting()) {
            return Collections.singletonList(data);
        }
        final ByteBuffer out = ByteBuffer.allocate(maxMessageBytes);
        final CharBuffer in = CharBuffer.wrap(data);

        List<String> chunks = null;
        int pos = 0;
        boolean first = true;
        while (true) {
            final CoderResult cr = UTF_8_ENCODER.encode(in, out, true);
            if (first) {
                // short exit if splitting is not necessary or disabled
                if (!cr.isOverflow()) {
                    // data fits into one message
                    return Collections.singletonList(data);
                }
                // create array for collecting chunks
                chunks = new ArrayList<>();
                first = false;
            }
            final int newpos = data.length() - in.length();
            chunks.add(data.substring(pos, newpos));
            if (!cr.isOverflow()) {
                break;
            }
            pos = newpos;
            // this weird cast is a workaround for a compatibility issue between Java-8 and 11.
            // see approach 2 in the answer to this post:
            // https://stackoverflow.com/questions/61267495/exception-in-thread-main-java-lang-nosuchmethoderror-java-nio-bytebuffer-flip
            ((Buffer) out).rewind();
        }
        return chunks;
    }

    /**
     * Passes chunk-related headers to the given headers updater function if required.
     * @param headersUpdater Function that accepts key/value strings and is backed on a message's headers/properties.
     * @param currentChunk The index (based on 0) of the current chunk. I.e., the current iteration index when
     * processing the chunks resolved by {@link #splitData(String)}.
     * @param totalChunks The total number of chunks for the current message. I.e., the size of the list returned
     * by {@link #splitData(String)}.
     * @param messageKey The unique message key that identifies the chunks that belong to a single message.
     */
    public void addChunkHeaders(BiConsumer<String, String> headersUpdater, int currentChunk, int totalChunks,
        String messageKey) {
        if (totalChunks < 2) {
            return;
        }
        headersUpdater.accept(chunkNoHeader, String.valueOf(currentChunk + 1));
        headersUpdater.accept(chunksHeader, String.valueOf(totalChunks));
        headersUpdater.accept(chunkMessageKeyHeader, messageKey);
    }
}
