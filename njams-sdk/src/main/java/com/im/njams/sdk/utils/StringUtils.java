package com.im.njams.sdk.utils;

import java.util.Collection;
import java.util.Collections;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

/**
 * Provides some String related utilities.
 *
 * @author cwinkler
 *
 */
public class StringUtils {
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
     * @return The given string if shorter than the given length, or a shortened version with three dots at the end that
     * indicate the truncation. I.e., the length of a truncated result is <code>maxLength+3</code>.
     */
    public static String abbreviate(String s, int maxLength) {
        if (s == null) {
            return null;
        }
        if (s.length() <= maxLength || maxLength < 1) {
            return s;
        }
        return s.substring(0, maxLength) + "...";
    }

    /**
     * Creates a debug string from the given JMS {@link Message}.
     * @param msg The message to serialize
     * @return A string containing the message's type, and properties, and body (abbreviated).
     */
    public static String messageToString(final Message msg) {
        if (msg == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(msg.getClass().getName()).append("[properties={");
        appendHeaders(sb, msg);
        sb.append('}');
        if (msg instanceof TextMessage) {
            sb.append("; body=");
            String body = null;
            try {
                body = ((TextMessage) msg).getText();
            } catch (JMSException e) {
                body = "[error: " + e + "]";
            }
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
     * Creates a debug string from the given JMS {@link Message}.
     * @param msg The message to serialize
     * @return A string containing the message's type, and properties, and body (abbreviated).
     */
    public static String headersToString(final Message msg) {
        if (msg == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(msg.getClass().getName()).append("[properties={");
        appendHeaders(sb, msg);
        sb.append("}]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String appendHeaders(StringBuilder sb, final Message msg) {
        Collection<String> keys = Collections.emptyList();
        try {
            keys = Collections.list(msg.getPropertyNames());
        } catch (Exception e) {
            // ignore
        }
        int i = 0;
        for (final String key : keys) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(key).append('=');
            try {
                final Object obj = msg.getObjectProperty(key);
                sb.append(obj == null ? "null" : abbreviate(obj.toString(), 300));
            } catch (JMSException e) {
                sb.append("[error: ").append(e).append(']');
            }
        }
        return sb.toString();
    }

}
