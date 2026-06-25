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

/**
 * Immutable result of a size-limited serialization (see
 * {@link Serializer#serialize(Object, int)}): the produced string together with an explicit flag
 * telling whether the serializer had to stop early because the size limit was reached.
 *
 * <p>The flag is the reliable signal that the output is incomplete; callers must not infer
 * truncation from the string length, because a size-honouring serializer stops at (or near) the
 * limit whether or not more content existed.</p>
 */
public final class SerializerResult {

    private final String value;
    private final boolean truncated;

    /**
     * Creates a result.
     *
     * @param value     the serialized string; may be <code>null</code> when the serialized object
     *                  was <code>null</code>
     * @param truncated <code>true</code> if serialization stopped early because the size limit was
     *                  reached, <code>false</code> if the value is the complete serialization
     */
    public SerializerResult(final String value, final boolean truncated) {
        this.value = value;
        this.truncated = truncated;
    }

    /**
     * Returns the serialized string, possibly clipped at the size limit.
     *
     * @return the serialized value; may be <code>null</code>
     */
    public String value() {
        return value;
    }

    /**
     * Returns whether the serialized output was truncated because the size limit was reached.
     *
     * @return <code>true</code> if the value is incomplete (the object was larger than the limit),
     *         <code>false</code> if it is the complete serialization
     */
    public boolean truncated() {
        return truncated;
    }

    @Override
    public String toString() {
        return "SerializerResult{truncated=" + truncated + ", value=" + value + '}';
    }
}
