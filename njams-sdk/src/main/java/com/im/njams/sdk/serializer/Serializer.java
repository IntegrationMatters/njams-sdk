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
 * <p>Implementations must declare {@link #serialize(Object, int)} and return a
 * {@link SerializerResult} carrying the serialized string and an explicit {@code truncated} flag.
 * Honouring the {@code sizeLimit} argument is <strong>optional</strong>, but the {@code truncated}
 * flag must always be set correctly: it is the SDK's only reliable signal that the output is
 * incomplete (callers must not infer truncation from the string length). Honouring {@code sizeLimit}
 * is encouraged because it can save significant CPU and memory when serializing large objects that
 * would otherwise be built in full and then clipped; an implementation that cannot easily limit its
 * output may serialize the whole object and report {@code truncated} from a length comparison.</p>
 *
 * <p>The single-argument {@link #serialize(Object)} is a convenience that serializes with no
 * effective limit and therefore can never truncate; it returns the plain string.</p>
 *
 * <p>The SDK ships two implementations:</p>
 * <ul>
 *   <li>{@link JsonSerializer} — serializes via Jackson and honours {@code sizeLimit} effectively,
 *       aborting the stream once the limit is reached so a large object is never fully
 *       materialised; it reports {@code truncated} precisely from that abort.</li>
 *   <li>{@link StringSerializer} — serializes via {@link Object#toString()}. Because it must build
 *       the complete {@code toString()} result before it can clip it, it <strong>cannot</strong>
 *       limit work or memory during serialization; passing {@code sizeLimit} only trims the
 *       already-allocated string. It reports {@code truncated} from the length comparison. Prefer a
 *       serializer that truncates effectively (such as {@link JsonSerializer}, or a custom
 *       implementation) whenever that is feasible, and reserve {@link StringSerializer} for cases
 *       where no better option exists.</li>
 * </ul>
 *
 * @author stkniep
 * @param <T> generic
 */
public interface Serializer<T> {

    /**
     * Serialize given Object to String with no effective size limit.
     *
     * <p>With no limit the result can never be truncated, so this convenience returns the plain
     * string. The default implementation delegates to {@link #serialize(Object, int)} passing
     * {@link Integer#MAX_VALUE} and unwraps the {@link SerializerResult#value()}. Implementations
     * may override this method to provide a faster unlimited path that skips size-tracking
     * bookkeeping.</p>
     *
     * @param object Object to be serialized
     * @return String representation for the given Object
     * @throws NjamsSdkRuntimeException if serialization fails
     */
    default String serialize(T object) throws NjamsSdkRuntimeException {
        final SerializerResult result = serialize(object, Integer.MAX_VALUE);
        return result == null ? null : result.value();
    }

    /**
     * Serialize given Object, respecting the given size limit when greater than 0, and report
     * whether the output had to be truncated.
     *
     * <p>Honouring {@code sizeLimit} is <strong>optional but recommended</strong>: stopping output
     * once roughly {@code sizeLimit} characters have been produced avoids building large strings,
     * saving CPU and memory. Implementations that cannot easily do this may ignore {@code sizeLimit}
     * and serialize the whole object, reporting {@code truncated} from a length comparison.</p>
     *
     * <p>The {@link SerializerResult#truncated()} flag is <strong>mandatory</strong> and must be
     * {@code true} exactly when the object was larger than {@code sizeLimit} (so the returned value
     * is incomplete). A {@code sizeLimit} of {@link Integer#MAX_VALUE} or any non-positive value
     * means "no limit", in which case the result is never truncated.</p>
     *
     * @param object    Object to be serialized
     * @param sizeLimit Recommended (not mandatory) upper bound for the produced string length when
     *                  positive and less than {@link Integer#MAX_VALUE}; otherwise the limit is
     *                  ignored
     * @return the serialized value together with the truncation flag, or <code>null</code> if the
     *         implementation represents a <code>null</code> object as <code>null</code>
     * @throws NjamsSdkRuntimeException if serialization fails
     */
    SerializerResult serialize(T object, int sizeLimit) throws NjamsSdkRuntimeException;
}
