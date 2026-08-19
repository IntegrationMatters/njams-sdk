/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.http;

import java.net.URL;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Reports that the nJAMS server kept responding with a non-success HTTP status code for a message send, after the
 * sender's own retries were exhausted. Distinct from {@link HttpSendException}: the HTTP client itself never
 * failed here, so this does not necessarily indicate a broken connection.
 *
 * @since 6.0.0
 */
public class HttpStatusException extends NjamsSdkRuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    /**
     * @param url        the URL the request was sent to.
     * @param statusCode the HTTP status code repeatedly received.
     */
    public HttpStatusException(URL url, int statusCode) {
        super("Error sending message with HTTP client URL " + url + " Response status is: " + statusCode);
        this.statusCode = statusCode;
    }

    /**
     * @return the HTTP status code repeatedly received.
     */
    public int getStatusCode() {
        return statusCode;
    }

}
