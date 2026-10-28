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
package com.im.njams.sdk.utils;

/**
 * Provides some String related utilities.
 *
 * @author cwinkler
 *
 */
public class StringUtils {
    /** The three dots ellipsis character '…' used for abbreviations. */
    public static final char ELLIPSIS = '\u2026';


    private StringUtils() {
        // static only
    }

    /**
     * <p>Checks if a String is whitespace, empty ("") or null.</p>
     *
     * <pre>
     * StringUtils.isBlank(null)      = true
     * StringUtils.isBlank("")        = true
     * StringUtils.isBlank(" ")       = true
     * StringUtils.isBlank("bob")     = false
     * StringUtils.isBlank("  bob  ") = false
     * </pre>
     *
     * @param s  the String to check, may be null
     * @return <code>true</code> if the String is null, empty or whitespace
     */
    public static boolean isBlank(String s) {
        if (s == null) {
            return true;
        }
        final int len = s.length();
        if (len == 0) {
            return true;
        }
        for (int i = 0; i < len; i++) {
            if (Character.isWhitespace(s.charAt(i)) == false) {
                return false;
            }
        }
        return true;
    }

    /**
     * <p>Checks if a String contains any character different from a whitespace.</p>
     *
     * <pre>
     * StringUtils.isNotBlank(null)      = false
     * StringUtils.isNotBlank("")        = false
     * StringUtils.isNotBlank(" ")       = false
     * StringUtils.isNotBlank("bob")     = true
     * StringUtils.isNotBlank("  bob  ") = true
     * </pre>
     *
     * @param str  the String to check, may be null
     * @return <code>true</code> if the String is not null and contains any non-whitespace character.
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * Abbreviates the given string to given length
     * @param s The string to abbreviate
     * @param maxLength The maximum length
     * @return The given string if shorter than the given length, or a shortened version with a single ellipsis
     * character ({@value #ELLIPSIS}) appended that indicates the truncation. I.e., the length of a truncated result
     * is <code>maxLength+1</code>.
     */
    public static String abbreviate(String s, int maxLength) {
        if (s == null) {
            return null;
        }
        if (s.length() <= maxLength || maxLength < 1) {
            return s;
        }
        return s.substring(0, maxLength) + ELLIPSIS;
    }

    /**
     * Creates a debug string from the given JMS message.
     * @param msg The message to serialize; any {@code javax.jms} or {@code jakarta.jms} message
     * @return A string containing the message's type, and properties, and body (abbreviated).
     */
    public static String messageToString(final Object msg) {
        if (msg == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(msg.getClass().getName()).append("[properties={");
        appendHeaders(sb, msg);
        sb.append('}');
        if (JmsMessageAccess.isTextMessage(msg)) {
            sb.append("; body=");
            final String body = JmsMessageAccess.text(msg);
            if (body == null) {
                sb.append("null");
            } else {
                sb.append(abbreviate(body, 1000));
            }
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Creates a debug string from the given JMS message.
     * @param msg The message to serialize
     * @return A string containing the message's type, and properties, and body (abbreviated).
     * @deprecated Use {@link #messageToString(Object)} instead.
     */
    @Deprecated(since = "6.1.0")
    public static String messageToString(final javax.jms.Message msg) {
        return messageToString((Object) msg);
    }

    /**
     * Creates a debug string from the given JMS message's type and properties.
     * @param msg The message to serialize; any {@code javax.jms} or {@code jakarta.jms} message
     * @return A string containing the message's type and properties.
     */
    public static String headersToString(final Object msg) {
        if (msg == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(msg.getClass().getName()).append("[properties={");
        appendHeaders(sb, msg);
        sb.append("}]");
        return sb.toString();
    }

    /**
     * Creates a debug string from the given JMS message's type and properties.
     * @param msg The message to serialize
     * @return A string containing the message's type and properties.
     * @deprecated Use {@link #headersToString(Object)} instead.
     */
    @Deprecated(since = "6.1.0")
    public static String headersToString(final javax.jms.Message msg) {
        return headersToString((Object) msg);
    }

    private static void appendHeaders(StringBuilder sb, final Object msg) {
        int i = 0;
        for (final String key : JmsMessageAccess.propertyNames(msg)) {
            if (i++ > 0) {
                sb.append(", ");
            }
            final Object obj = JmsMessageAccess.objectProperty(msg, key);
            sb.append(key).append('=').append(obj == null ? "null" : abbreviate(obj.toString(), 300));
        }
    }

}
