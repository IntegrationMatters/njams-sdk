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
package com.im.njams.sdk.communication;

/**
 * Thrown by a transport implementation from its initialization to state that it is not applicable in the current
 * environment, e.g. because a library it requires is not present. This is an expected outcome rather than an
 * error: the SDK skips such an implementation and looks for another one.
 */
public class TransportNotApplicableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final int MAX_CAUSE_DEPTH = 10;

    /**
     * Why an implementation classified by {@link #isNotApplicable(Throwable)} was skipped, for reporting.
     */
    public static final String NOT_APPLICABLE_REASON = "required JMS API not present";

    /**
     * Whether the given failure says that a transport implementation is not applicable on this classpath rather
     * than broken. Two things say so, and both must name a type of one of the two JMS APIs: a failed cast, which
     * is what a connection factory of the other JMS namespace produces, and a missing type. The whole cause chain
     * is examined, because an implementation may wrap the failure into an exception of its own.
     * <p>
     * Deliberately narrow in both directions: a type that is missing for any other reason, such as a broker client
     * library that was not deployed, is a real error, and so is a failed cast anywhere else — this classification
     * is applied to every transport's providers, not only to the JMS ones.
     *
     * @param failure the failure to classify.
     * @return whether the failure means "not applicable here" rather than "broken".
     */
    public static boolean isNotApplicable(final Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (isJmsNamespaceCastFailure(current) || isMissingJmsApi(current)) {
                return true;
            }
            final Throwable cause = current.getCause();
            if (cause == current) {
                return false;
            }
            current = cause;
        }
        return false;
    }

    /**
     * Whether the given throwable is a cast failure between the two JMS namespaces, which is what looking up a
     * connection factory of the other namespace produces. The message names both the source and the target class,
     * so the JMS API type appears anywhere in it rather than at the start. A cast failure that names no JMS API
     * type belongs to some other transport's provider and is a real defect.
     */
    private static boolean isJmsNamespaceCastFailure(final Throwable throwable) {
        if (!(throwable instanceof ClassCastException)) {
            return false;
        }
        final String message = throwable.getMessage();
        return message != null && (message.contains("javax.jms.") || message.contains("jakarta.jms."));
    }

    /**
     * Whether the given throwable means that a type of one of the two JMS APIs (<code>javax.jms</code> or
     * <code>jakarta.jms</code>) is not on the classpath. The SDK ships implementations for both, so one of them
     * is always expected to fail like this. A {@link NoClassDefFoundError} reports the internal class name (with
     * slashes), a {@link ClassNotFoundException} the dotted one. A missing type of a JMS API that is present but
     * incomplete is classified the same way.
     */
    private static boolean isMissingJmsApi(final Throwable throwable) {
        if (!(throwable instanceof NoClassDefFoundError) && !(throwable instanceof ClassNotFoundException)) {
            return false;
        }
        final String message = throwable.getMessage();
        return message != null && (message.startsWith("javax.jms.") || message.startsWith("jakarta.jms.")
            || message.startsWith("javax/jms/") || message.startsWith("jakarta/jms/"));
    }

    /**
     * @param message Describes why the transport is not applicable.
     */
    public TransportNotApplicableException(final String message) {
        super(message);
    }

    /**
     * @param message Describes why the transport is not applicable.
     * @param cause The underlying reason.
     */
    public TransportNotApplicableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
