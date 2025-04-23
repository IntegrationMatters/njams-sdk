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

import java.util.Map;

/**
 * Immutable container for messages build of headers and message body.
 */
public class RawMessage {
    private final Map<String, String> headers;
    private final String body;

    /**
     * Sole constructor for full initialization of an immutable instance.
     * @param headers The message's headers, if any.
     * @param body The message's body/content.
     */
    public RawMessage(Map<String, String> headers, String body) {
        this.headers = headers;
        this.body = body;
    }

    /**
     * Get this message's headers.
     * @return This message's headers, if any. May be <code>null</code>.
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Get this message's body/content which is not necessarily a complete message.
     * @return This message's body/content.
     */
    public String getBody() {
        return body;
    }

    /**
     * Return's the value of a header from this message's headers.
     * @param key The name of the header to read.
     * @return The header's value, or <code>null</code> if no such header exists.
     */
    public String getHeader(String key) {
        return headers == null ? null : headers.get(key);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("RawMessage[headers=").append(headers).append(", body=")
            .append(body.length() > 500 ? body.substring(0, 500) + "..." : body).append("]");
        return builder.toString();
    }

}
