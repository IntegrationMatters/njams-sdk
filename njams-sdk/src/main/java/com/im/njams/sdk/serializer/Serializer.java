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
 * Serializer interface. Implementations turn a typed object into a {@link String} representation,
 * optionally respecting a maximum string length to avoid producing very large strings that will
 * be truncated downstream.
 *
 * <p>Implementations must declare {@link #serialize(Object, int)}, but honouring the
 * {@code sizeLimit} argument is <strong>optional</strong>. Honouring it is encouraged because it
 * can save significant CPU and memory when serializing large objects that would otherwise be
 * built in full and then truncated. If an implementation cannot easily limit its output, it may
 * ignore {@code sizeLimit} entirely and serialize the whole object: the SDK applies final
 * truncation separately, exactly as it did before this argument existed, so correctness is
 * unaffected. For the same reason an over-estimating implementation that stops at roughly
 * {@code sizeLimit + X} characters is also perfectly acceptable.</p>
 *
 * <p>The single-argument {@link #serialize(Object)} is a convenience that delegates with no
 * effective limit.</p>
 *
 * <p>The SDK ships two implementations:</p>
 * <ul>
 *   <li>{@link JsonSerializer} — serializes via Jackson and honours {@code sizeLimit} effectively,
 *       aborting the stream once the limit is reached so a large object is never fully
 *       materialised.</li>
 *   <li>{@link StringSerializer} — serializes via {@link Object#toString()}. Because it must build
 *       the complete {@code toString()} result before it can clip it, it <strong>cannot</strong>
 *       limit work or memory during serialization; passing {@code sizeLimit} only trims the
 *       already-allocated string. Prefer a serializer that truncates effectively (such as
 *       {@link JsonSerializer}, or a custom implementation) whenever that is feasible, and reserve
 *       {@link StringSerializer} for cases where no better option exists.</li>
 * </ul>
 *
 * @author stkniep
 * @param <T> generic
 */
public interface Serializer<T> {

    /**
     * Serialize given Object to String with no effective size limit.
     *
     * <p>The default implementation delegates to {@link #serialize(Object, int)} passing
     * {@link Integer#MAX_VALUE} as the size limit. Implementations may override this method
     * to provide a faster unlimited path that skips size-tracking bookkeeping.</p>
     *
     * @param object Object to be serialized
     * @return String representation for the given Object
     * @throws NjamsSdkRuntimeException if serialization fails
     */
    default String serialize(T object) throws NjamsSdkRuntimeException {
        return serialize(object, Integer.MAX_VALUE);
    }

    /**
     * Serialize given Object to String, respecting the given size limit when greater than 0.
     *
     * <p>Honouring {@code sizeLimit} is <strong>optional but recommended</strong>: stopping output
     * once roughly {@code sizeLimit} characters have been produced avoids building large strings
     * that are discarded by downstream truncation, saving CPU and memory. Implementations that
     * cannot easily do this may ignore {@code sizeLimit} and return the full serialization — the
     * SDK truncates the result separately afterwards, so behaviour is still correct, just less
     * efficient. An over-estimating implementation that stops near {@code sizeLimit + X} is also
     * acceptable; the returned string may therefore exceed {@code sizeLimit} and callers are
     * expected to apply final truncation. A {@code sizeLimit} of {@link Integer#MAX_VALUE} or any
     * non-positive value means "no limit".</p>
     *
     * @param object    Object to be serialized
     * @param sizeLimit Recommended (not mandatory) upper bound for the returned string length when
     *                  positive and less than {@link Integer#MAX_VALUE}; otherwise the limit is
     *                  ignored. Implementations may ignore or over-estimate this value
     * @return String representation for the given Object, possibly clipped near {@code sizeLimit}
     * @throws NjamsSdkRuntimeException if serialization fails
     */
    String serialize(T object, int sizeLimit) throws NjamsSdkRuntimeException;
}
