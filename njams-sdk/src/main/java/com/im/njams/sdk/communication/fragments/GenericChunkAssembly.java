/*
 * Copyright (c) 2025 Integration Matters GmbH
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
package com.im.njams.sdk.communication.fragments;

import static com.im.njams.sdk.communication.MessageHeaders.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.communication.MessageHeaders;

/**
 * Collects and merges messages that are split into several chunks.<br>
 * See {@link #resolve(Object)} for details.<br>
 * Obtain instances from {@link ChunkAssemblyManager}.
 *
 * @author cwinkler
 * @param <T> The type of the raw chunked messages as used by a reader implementation, e.g., {@link TextMessage} for
 * JMS.
 *
 */
public abstract class GenericChunkAssembly<T> {

    private static final Logger LOG = LoggerFactory.getLogger(GenericChunkAssembly.class);

    private static final int CHUNK_EXPIRATION = 5000;
    private static final int EXPIRATION_CHECK_INTERVAL = 1000;

    // message-key -> message chunks
    private final Map<String, ChunkedMessage> chunkedMessages = new ConcurrentHashMap<>();

    private final Function<T, Map<String, String>> headersFunction;
    private final Function<T, String> keyFunction;
    private final Function<T, String> dataFunction;

    private final String chunkMessageKeyHeader;
    private final String chunkNoHeader;
    private final String chunkCountHeader;

    private long nextCheck = 0;

    /**
     * @param keyFunction Optional. Function that provides the unique message ID for a given message chunk. I.e., all
     * parts (chunks) that belong to the same message must have the same key. Defaults to reading the
     * {@link MessageHeaders#NJAMS_CHUNK_MESSAGE_KEY_HEADER} header but can be overridden here, if there is better/faster
     * way to do this.
     * @param headersFunction Required. Function that provides the headers map from a given message.
     * @param dataFunction Required. Function that provides the actual data chunk from a given message. This is the
     * part that is actually split into chunks and re-assembled here.
     */
    protected GenericChunkAssembly(Function<T, String> keyFunction,
        Function<T, Map<String, String>> headersFunction,
        Function<T, String> dataFunction, boolean httpNginxHeaders) {
        this.headersFunction = headersFunction;
        this.dataFunction = dataFunction;
        this.keyFunction = keyFunction;

        chunkMessageKeyHeader =
            httpNginxHeaders ? NJAMS_CHUNK_MESSAGE_KEY_HTTP_HEADER : NJAMS_CHUNK_MESSAGE_KEY_HEADER;
        chunkNoHeader = httpNginxHeaders ? NJAMS_CHUNK_NO_HTTP_HEADER : NJAMS_CHUNK_NO_HEADER;
        chunkCountHeader = httpNginxHeaders ? NJAMS_CHUNKS_HTTP_HEADER : NJAMS_CHUNKS_HEADER;

        LOG.debug("{} created with: checkInterval={}ms, chunkExpiration={}ms", this, EXPIRATION_CHECK_INTERVAL,
            CHUNK_EXPIRATION);
    }

    /**
     * A fast thread-safe variant of {@link Map#computeIfAbsent(Object, Function)} that ensures that each entry is created
     * only once.
     *
     * @param <V> The map's value type
     * @param <K> The map's key type
     * @param map The map from that the value should be fetched
     * @param key The key to get
     * @param factory Value factory, applied in case the key does not exist in the map
     * @return
     */
    private static <K, V> V getFast(Map<K, V> map, K key, Function<K, V> factory) {
        final V t = map.get(key);
        if (t != null) {
            return t;
        }
        synchronized (map) {
            return map.computeIfAbsent(key, factory);
        }
    }

    /**
     * Creates and returns a {@link RawMessage} from the given message only if the resulting message is complete with
     * respect to nJAMS messages. Otherwise, this method returns <code>null</code> and stores the given record until
     * it has been completed with the remaining chunks.
     *
     * @param partialMessage A message chunk used for resolving a complete message.
     * @return The message returned for a split messages contains the headers of the first chunk only.
     */
    public RawMessage resolve(final T partialMessage) {
        final Map<String, String> headers = headersFunction.apply(partialMessage);
        if (headers == null || !headers.containsKey(chunkCountHeader)) {
            // this is a single message
            return buildMessage(dataFunction.apply(partialMessage), headers);
        }

        // here we have a message split into multiple chunks
        final String messageKey = getMessageKey(partialMessage, headers);
        if (messageKey == null) {
            // the key is mandatory for split messages
            LOG.error("Missing message key for split message ({}, headers={})", this, headers);
            return buildMessage(dataFunction.apply(partialMessage), headers);
        }
        final RawMessage resolved;
        try {
            final ChunkedMessage chunkedMessage =
                getFast(chunkedMessages, messageKey, k -> new ChunkedMessage(headers, getTotalChunks(headers)));
            if (LOG.isDebugEnabled() && chunkedMessage.isNew()) {
                LOG.debug("Stored split message for key {} [{}]. Expecting {} chunks.", messageKey, this,
                    chunkedMessage.size());
            }
            resolved = chunkedMessage.add(getPos(headers), dataFunction.apply(partialMessage));
            if (resolved != null) {
                chunkedMessages.remove(messageKey);
                LOG.debug("Removed resolved split message for key {} ({}, chunks={}).", messageKey, this,
                    chunkedMessage.size());
            }
            return resolved;
        } catch (Exception e) {
            LOG.error("Error during chunk processing ({}, message={})", this, messageKey, e);
            chunkedMessages.remove(messageKey);
            // treat this as a normal message, which might result in further processing errors (parsing)
            return buildMessage(dataFunction.apply(partialMessage), headers);
        } finally {
            checkIncompleteMessages();
        }
    }

    private String getMessageKey(T partialMessage, final Map<String, String> headers) {
        if (keyFunction != null) {
            return keyFunction.apply(partialMessage);
        }
        return headers.get(chunkMessageKeyHeader);
    }

    private RawMessage buildMessage(String message, Map<String, String> headers) {
        return new RawMessage(headers, message);
    }

    private int getPos(final Map<String, String> headers) {
        return Integer.parseInt(headers.get(chunkNoHeader));
    }

    private int getTotalChunks(final Map<String, String> headers) {
        return Integer.parseInt(headers.get(chunkCountHeader));
    }

    /**
     * Removes incomplete messages if too old.
     */
    private void checkIncompleteMessages() {
        final long now = System.currentTimeMillis();
        if (now < nextCheck) {
            return;
        }
        synchronized (chunkedMessages) {
            if (now < nextCheck) {
                return;
            }
            // run each minute
            nextCheck = now + EXPIRATION_CHECK_INTERVAL;
            LOG.debug("Check for stalled messages. [{}]", this);
            // expire older than 1 hour
            long expireTime = now - CHUNK_EXPIRATION;
            int total = 0;
            try {
                // only purge for running DPs, respectively, keep messages for DPs that are currently stopped
                final int count = chunkedMessages.size();
                chunkedMessages.values().removeIf(c -> c.getTimestamp() < expireTime);
                total += count - chunkedMessages.size();
            } catch (Exception e) {
                LOG.error("Unexpected error during cleanup. [{}]", this, e);
            }
            if (total > 0) {
                LOG.warn("Purged {} stalled messages. [{}]", total, this);
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
