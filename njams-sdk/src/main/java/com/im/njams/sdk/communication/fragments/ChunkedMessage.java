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

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ChunkedMessage {
    private long timestamp = System.currentTimeMillis();
    private final Map<String, String> headers;
    private final String[] chunks;
    private boolean isNew = true;

    public ChunkedMessage(final Map<String, String> headers, int expectedChunks) {
        this.headers = headers;
        if (expectedChunks < 1) {
            throw new IllegalArgumentException("Illegal number of chunks: " + expectedChunks);
        }
        chunks = new String[expectedChunks];
    }

    /**
     * For logging only
     * @return
     */
    public synchronized boolean isNew() {
        boolean b = isNew;
        isNew = false;
        return b;
    }

    private boolean isComplete() {
        for (int i = chunks.length - 1; i >= 0; i--) {
            if (chunks[i] == null) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        return chunks.length;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String[] getChunks() {
        return chunks;
    }

    public synchronized RawMessage add(final int chunkNo, final String partialData) {
        if (chunkNo < 1 || chunkNo > chunks.length) {
            throw new IndexOutOfBoundsException("Unexpected chunk #" + chunkNo);
        }
        // chunk-no starts with 1
        chunks[chunkNo - 1] = partialData;
        return build();
    }

    private RawMessage build() {
        if (!isComplete()) {
            return null;
        }
        return new RawMessage(headers, Arrays.stream(chunks).collect(Collectors.joining()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(chunks);
        return prime * result + Objects.hash(headers);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ChunkedMessage other = (ChunkedMessage) obj;
        return Arrays.equals(chunks, other.chunks) && Objects.equals(headers, other.headers);
    }

}