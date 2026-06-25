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
package com.im.njams.sdk.serializer;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * String Serializer
 *
 * @author stkniep
 * @param <T> generic
 */
public class StringSerializer<T> implements Serializer<T> {

    /**
     * Serialize
     *
     * @param t generic to serialize
     * @return serialized generic
     * @throws NjamsSdkRuntimeException exception
     */
    @Override
    public String serialize(final T t) throws NjamsSdkRuntimeException{
        return t == null ? "" : t.toString();
    }

    /**
     * Serialize via {@link Object#toString()} and substring the result to {@code sizeLimit}
     * characters when a positive, less-than-{@link Integer#MAX_VALUE} limit is given.
     *
     * <p>Because the full {@code toString()} is built before it can be clipped, this serializer
     * cannot save work or memory; it reports {@link SerializerResult#truncated()} from the length
     * comparison, i.e. {@code true} exactly when the {@code toString()} result was longer than
     * {@code sizeLimit}.</p>
     *
     * @param t         Object to serialize, may be {@code null}
     * @param sizeLimit Maximum length of the returned string when positive and less than
     *                  {@link Integer#MAX_VALUE}; otherwise the limit is ignored
     * @return {@code t.toString()} clipped to {@code sizeLimit} characters with the truncation flag,
     *         or an empty, non-truncated result when {@code t} is {@code null}
     */
    @Override
    public SerializerResult serialize(final T t, final int sizeLimit) throws NjamsSdkRuntimeException {
        if (t == null) {
            return new SerializerResult("", false);
        }
        final String s = t.toString();
        if (sizeLimit <= 0 || sizeLimit == Integer.MAX_VALUE || sizeLimit >= s.length()) {
            return new SerializerResult(s, false);
        }
        return new SerializerResult(s.substring(0, sizeLimit), true);
    }

}
