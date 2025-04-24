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
package com.im.njams.sdk.communication.fragments;

import static com.im.njams.sdk.communication.MessageHeaders.*;
import static com.im.njams.sdk.utils.PropertyUtil.getPropertyBool;
import static com.im.njams.sdk.utils.PropertyUtil.getPropertyInt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.http.HttpSender;
import com.im.njams.sdk.settings.Settings;

/**
 * Splits large messages according to the configuration setting {@link NjamsSettings#PROPERTY_MAX_MESSAGE_SIZE} or
 * using a technical size limit caused by the used transport implementation.
 *
 * @author cwinkler
 *
 */
public class SplitSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SplitSupport.class);

    /**
     * Simple immutable container for two <code>int</code> values defining a from/to range.
     */
    public static class Range {
        private final int from;
        private final int to;

        /**
         * Sole constructor.
         * @param from The from parameter.
         * @param to The to parameter.
         */
        public Range(int from, int to) {
            this.from = from;
            this.to = to;
        }

        /**
         * @return The from value.
         */
        public int from() {
            return from;
        }

        /**
         * @return The to value.
         */
        public int to() {
            return to;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Range[from=").append(from).append(", to=").append(to).append("]");
            return builder.toString();
        }

    }

    /**
     * Iterator for message chunks having some additional methods for getting size (chunk count) and current index.
     */
    public class SplitIterator implements Iterator<String> {

        private final Iterator<Range> indexesIterator;
        private final String data;
        private final int size;

        // used for implementing fast single/no entry iterator; used when indexesIterator is null.
        private boolean dataReturned = false;
        // user for getting the current iteration index
        private int currentIndex = -1;

        private SplitIterator(String data) {
            this.data = data;
            final List<Range> splitIndexes = getSplitIndexesInternal(data);
            if (splitIndexes == null) {
                indexesIterator = null;
                dataReturned = true;
                size = 0;
            } else if (splitIndexes == FIT_ALL) {
                indexesIterator = null;
                dataReturned = false;
                size = 1;
            } else {
                indexesIterator = splitIndexes.iterator();
                size = splitIndexes.size();
            }
        }

        @Override
        public boolean hasNext() {
            return indexesIterator == null ? !dataReturned : indexesIterator.hasNext();
        }

        @Override
        public String next() {
            if (indexesIterator == null) {
                if (dataReturned) {
                    throw new NoSuchElementException();
                }
                currentIndex = 0;
                dataReturned = true;
                return data;
            }
            final Range range = indexesIterator.next();
            currentIndex++;
            return data.substring(range.from, range.to);
        }

        /**
         * The numeric index of the current record, i.e., the one returned by the last call to
         * {@link #next()}. Before the first call to {@link #next()}, this method returns -1.
         * @return The current iteration index
         */
        public int currentIndex() {
            return currentIndex;
        }

        /**
         * The size of records returned by this iterator.
         * @return This iterator's size
         */
        public int size() {
            return size;
        }

        /**
         * Returns whether this iterator returns no records.
         * @return Whether this iterator returns no records.
         */
        public boolean isEmpty() {
            return size > 0;
        }
    }

    /**
     * Marker list instance returned by {@link #getSplitIndexes(String)} if the whole given data fits
     * into this instance's max-message size. The list is empty but has to be interpreted as if containing
     * single entry with <code>{0, data.length()}</code>.
     */
    private static final List<Range> FIT_ALL = Collections.unmodifiableList(new ArrayList<>());

    /** For testing only. Disables limit checks for {@link NjamsSettings#PROPERTY_MAX_MESSAGE_SIZE} */
    public static final String TESTING_NO_LIMIT_CHECKS = "test-no-limit-checks";

    /** The minimum value allowed as max message size */
    public static final int MIN_SIZE_LIMIT = 10240;

    private final int maxMessageBytes;

    private final String chunkNoHeader;
    private final String chunksHeader;
    private final String chunkMessageKeyHeader;

    /**
     * Constructor that initializes this instance from the given {@link Settings}.
     * @param settings {@link Settings} to be used for initializing this instance.
     * @param techLimit Technical limitation of the maximum message size enforced by the transport implementation,
     * if any. This works as an upper limit. Set to 0 or less if there is no such limit.
     */
    public SplitSupport(final Settings settings, final int techLimit) {
        this(settings.getAllProperties(), techLimit);
    }

    /**
     * Constructor that initializes this instance from the given {@link Properties}.
     * @param properties {@link Properties} to be used for initializing this instance.
     * @param techLimit Technical limitation of the maximum message size enforced by the transport implementation,
     * if any. This works as an upper limit. Set to 0 or less if there is no such limit.
     */
    public SplitSupport(final Properties properties, final int techLimit) {
        final String transport = properties.getProperty(NjamsSettings.PROPERTY_COMMUNICATION);

        final int configuredLimit = getPropertyInt(properties, NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, -1);
        if (getPropertyBool(properties, TESTING_NO_LIMIT_CHECKS, false)) {
            maxMessageBytes = configuredLimit;
        } else {
            maxMessageBytes = resolveLimit(configuredLimit, techLimit);
        }
        if (maxMessageBytes > 0) {
            LOG.info("Limitting max message size to {} bytes", maxMessageBytes);
        }

        if (HttpSender.NAME.equalsIgnoreCase(transport) || "HTTPS".equalsIgnoreCase(transport)) {
            chunkNoHeader = NJAMS_CHUNK_NO_HTTP_HEADER;
            chunksHeader = NJAMS_CHUNKS_HTTP_HEADER;
            chunkMessageKeyHeader = NJAMS_CHUNK_MESSAGE_KEY_HTTP_HEADER;
            LOG.debug("Using nginx compatible http headers.");
        } else {
            chunkNoHeader = NJAMS_CHUNK_NO_HEADER;
            chunksHeader = NJAMS_CHUNKS_HEADER;
            chunkMessageKeyHeader = NJAMS_CHUNK_MESSAGE_KEY_HEADER;
            LOG.debug("Using common message properties.");
        }
    }

    private static int resolveLimit(int configuredLimit, int techLimit) {
        if (techLimit > 0 && techLimit < MIN_SIZE_LIMIT) {
            throw new IllegalArgumentException(
                "The technical transport message size limit of " + techLimit + " bytes is "
                    + "less than the allowed minimum of " + MIN_SIZE_LIMIT + " bytes.");
        }
        if (configuredLimit > 0 && configuredLimit < MIN_SIZE_LIMIT) {
            LOG.warn(
                "The configured max message size limit of {} bytes is less than the allowed minimum of {} bytes."
                    + " Using the minimum.",
                configuredLimit, MIN_SIZE_LIMIT);
            configuredLimit = MIN_SIZE_LIMIT;
        }

        if (techLimit > 0 && configuredLimit <= 0) {
            return techLimit;
        }
        if (techLimit > 0 && configuredLimit > techLimit) {
            LOG.warn("The configured max message size of {} bytes is larger than the transport's "
                + "technical limit of {} bytes. Using the technical limit.", configuredLimit, techLimit);
            return techLimit;
        }
        return configuredLimit;
    }

    /**
     * Returns whether or not a max-message size limit is active that requires splitting larger messages.
     * If <code>false</code> using this instance can be avoided for better performance.
     * @return Whether splitting is active.
     */
    public boolean isSplitting() {
        return maxMessageBytes > 0;
    }

    /**
     * Calculates the indexes in terms of {@link String#substring(int, int)} where to split the given data so that
     * each chunk fits into this instance's max-message size.<br>
     * <br>
     * <b>Note:</b> Users should prefer using the {@link #iterator(String)} which correctly evaluates the split indexes.
     *
     * @param data The data for which split indexes are calculated.
     * @return List of indexes for splitting the given input data.
     * @see #iterator(String)
     */
    public List<Range> getSplitIndexes(final String data) {
        final List<Range> list = getSplitIndexesInternal(data);
        if (list == FIT_ALL) {
            // convert the internal marker into an according result
            return Collections.singletonList(new Range(0, data.length()));
        }
        return list;
    }

    private List<Range> getSplitIndexesInternal(final String data) {
        if (data == null) {
            return Collections.emptyList();
        }
        if (!isSplitting() || data.isEmpty()) {
            return FIT_ALL;
        }
        final int stopPos = data.length() - 1;
        int startPosIncl = 0;
        int endPosExcl = 0;
        List<Range> chunks = null;
        while (endPosExcl < stopPos) {
            endPosExcl = nextChunkEnd(data, startPosIncl);
            if (chunks == null) {
                if (endPosExcl >= stopPos) {
                    return FIT_ALL;
                }
                chunks = new ArrayList<>();
            }
            chunks.add(new Range(startPosIncl, endPosExcl));
            startPosIncl = endPosExcl;
        }
        return chunks;
    }

    /**
     * Returns a new {@link SplitIterator} for the given data that iterates over the data's chunks according to this
     * instance's max-message size.
     * @param data The data to iterate as chunks
     * @return An iterator for given data's chunks.
     */
    public SplitIterator iterator(final String data) {
        return new SplitIterator(data);
    }

    /**
     * Returns the index in the given string so that the substring using this end index fits into the max-message
     * limit (as UTF-8 byte size).
     *
     * @param sequence The next data sequence to check.
     * @param startPos The start index that is used for calculating the next chunk end index.
     * @return The next endIndex for calling {@link String#substring(int, int)}, i.e., the returned value is exclusive.
     * @see <a href="https://stackoverflow.com/questions/8511490/calculating-length-in-utf-8-of-java-string-without-actually-encoding-it">
     * Implementation adapted from Stackoverflow</a>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc3629#section-3">UTF-8 Specification</a>
     */
    private int nextChunkEnd(CharSequence sequence, int startPos) {
        final int len = sequence.length();
        int bytes = 0;
        for (int i = startPos; i < len; i++) {
            boolean isSurrogate = false;
            final char c = sequence.charAt(i);
            if (c <= 0x7F) {
                bytes++;
            } else if (c <= 0x7FF) {
                bytes += 2;
            } else if (isSurrogate = Character.isHighSurrogate(c)) {
                bytes += 4;
                ++i;
            } else {
                bytes += 3;
            }
            if (bytes > maxMessageBytes) {
                return isSurrogate ? i - 1 : i;
            }
        }
        return len;
    }

    /**
     * The configured (or resolved) max message size in bytes that this instance uses.
     * @return The configured (or resolved) max message size in bytes.
     */
    public int getMaxMessageSize() {
        return maxMessageBytes;
    }

    /**
     * Passes chunk-related headers to the given headers updater function if required.
     * @param headersUpdater Function that accepts key/value strings and is backed on a message's headers/properties.
     * @param currentChunk The index (based on 0) of the current chunk. I.e., the current iteration index when
     * processing the chunks.
     * @param totalChunks The total number of chunks to be sent for the current message. If &lt; 2, this method
     * will do nothing.
     * @param messageKey The unique message key that identifies the chunks that belong to a single message.
     */
    public void addChunkHeaders(final BiConsumer<String, String> headersUpdater, final int currentChunk,
        final int totalChunks, final String messageKey) {
        if (totalChunks < 2) {
            return;
        }
        headersUpdater.accept(chunkNoHeader, String.valueOf(currentChunk + 1));
        headersUpdater.accept(chunksHeader, String.valueOf(totalChunks));
        headersUpdater.accept(chunkMessageKeyHeader, messageKey);
    }
}
