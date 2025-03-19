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

    private static final int KAFKA_MAX_SIZE_DEFAULT = 1048576;
    private static final int KAFKA_MAX_SIZE_OVERHEAD = 2048;

    private final int maxMessageBytes;

    private final String chunkNoHeader;
    private final String chunksHeader;
    private final String chunkMessageKeyHeader;

    /**
     * Constructor that initializes this instance from the given {@link Settings}.
     * @param settings
     */
    public SplitSupport(Settings settings) {
        this(settings.getAllProperties());
    }

    /**
     * Constructor that initializes this instance from the given {@link Properties}.
     * @param properties
     */
    public SplitSupport(Properties properties) {
        final String transport = properties.getProperty(NjamsSettings.PROPERTY_COMMUNICATION);
        if (transport == null) {
            throw new IllegalArgumentException("Missing setting: " + NjamsSettings.PROPERTY_COMMUNICATION);
        }

        final int limit = getPropertyInt(properties, NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, -1);
        int kafkaLimit = Integer.MAX_VALUE;
        if (KafkaConstants.COMMUNICATION_NAME.equalsIgnoreCase(transport)) {
            kafkaLimit = KAFKA_MAX_SIZE_DEFAULT;
            Properties kafka = KafkaUtil.filterKafkaProperties(properties, ClientType.PRODUCER);
            String property = kafka.getProperty(ProducerConfig.MAX_REQUEST_SIZE_CONFIG);
            if (StringUtils.isNotBlank(property)) {
                // reduce by some overhead for headers
                kafkaLimit = getPropertyInt(kafka, property, kafkaLimit) - KAFKA_MAX_SIZE_OVERHEAD;
                if (kafkaLimit <= 0) {
                    LOG.warn("Illegal {} setting for Kafka client: {}.", ProducerConfig.MAX_REQUEST_SIZE_CONFIG,
                            property);
                    kafkaLimit = KAFKA_MAX_SIZE_DEFAULT - KAFKA_MAX_SIZE_OVERHEAD;
                }
            }
        }

        if (limit > kafkaLimit) {
            LOG.warn("Configured max message size ({}) is larger than that configured for the Kafka client ({}). "
                    + "Using Kafka client's setting.", limit, kafkaLimit);
            maxMessageBytes = kafkaLimit;
        } else {
            maxMessageBytes = limit;
        }
        if (maxMessageBytes > 0) {
            LOG.info("Limitting max message size to {}bytes", maxMessageBytes);
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

    private static int getPropertyInt(Properties properties, String key, int defaultValue) {
        final String s = properties.getProperty(key);
        if (StringUtils.isNotBlank(s)) {
            try {
                return Integer.parseInt(s);
            } catch (Exception e) {
                LOG.warn("Failed to parse value {} of property {} to int.", s, key);
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Splits the given data string into chunks exactly respecting the configured (or resolved)
     * {@link NjamsSettings#PROPERTY_MAX_MESSAGE_SIZE} value (UTF-8 bytes).
     *
     * @param data
     * @return Sorted list of chunks resolved from the given input data.
     */
    public List<String> splitData(final String data) {
        if (StringUtils.isBlank(data)) {
            return Collections.emptyList();
        }
        if (maxMessageBytes <= 0) {
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
