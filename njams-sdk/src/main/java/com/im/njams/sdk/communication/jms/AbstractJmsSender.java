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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.jms;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.TransportNotApplicableException;
import com.im.njams.sdk.communication.fragments.SplitSupport;
import com.im.njams.sdk.communication.fragments.SplitSupport.SplitIterator;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.JsonUtils;

/**
 * The part of the JMS sender that does not touch a JMS type, shared by the {@code javax.jms} and the
 * {@code jakarta.jms} sender.
 * <p>
 * Internal SDK infrastructure, not an extension point: it is public only so that the sender implementations in
 * other packages can extend it. Message, producer and exception types are passed as {@link Object} so that no
 * JMS namespace is named here; the abstract hooks are where a subclass supplies them.
 * </p>
 */
public abstract class AbstractJmsSender extends AbstractSender {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJmsSender.class);

    /**
     * Name for the JMS communication implementation.
     */
    public static final String COMMUNICATION_NAME = "JMS";

    /** Destination used by sender and receiver when no destination setting is present. */
    static final String DEFAULT_DESTINATION = "njams";

    /** Guards against a pathological transport exception whose cause chain loops back on itself. */
    private static final int MAX_CAUSE_DEPTH = 32;

    // Values of the JMS DeliveryMode constants, identical in javax.jms and jakarta.jms.
    private static final int DELIVERY_MODE_NON_PERSISTENT = 1;
    private static final int DELIVERY_MODE_PERSISTENT = 2;
    // 22 == TIBCO RELIABLE SEND
    private static final int DELIVERY_MODE_TIBCO_RELIABLE = 22;

    private SplitSupport splitSupport = null;
    private boolean useProjectQueue = false;

    /**
     * Initializes this Sender via the given settings.
     *
     * @param settings the settings needed to initialize
     * @throws TransportNotApplicableException if the configured JMS factory belongs to the other JMS namespace
     */
    @Override
    public void init(ClientSettings settings) {
        resolveFactory(settings);
        super.init(settings);
        useProjectQueue =
            "false".equalsIgnoreCase(settings.getProperty(NjamsSettings.PROPERTY_JMS_SUPPORTS_MESSAGE_SELECTOR));
        if (useProjectQueue) {
            LOG.info("Using separate project queue.");
        }
        splitSupport = new SplitSupport(settings, -1);
        LOG.debug("Initialized sender {} (useProjectQueue={})", getName(), useProjectQueue);
    }

    /**
     * Resolves the JMS factory this sender will use. Called first during {@link #init(ClientSettings)}.
     * Implementations signal that the configured factory belongs to the other JMS namespace by throwing a
     * {@link TransportNotApplicableException}.
     * <p>
     * Implementations must read from the {@code settings} parameter: the inherited {@code settings} field is
     * only assigned after this hook has returned.
     *
     * @param settings the settings the sender is initialized with
     */
    protected abstract void resolveFactory(ClientSettings settings);

    /**
     * @return {@code true} if project and trace messages go to a separate queue because the broker does not
     *         support message selectors.
     */
    protected final boolean isUseProjectQueue() {
        return useProjectQueue;
    }

    @Override
    public String getName() {
        return COMMUNICATION_NAME;
    }

    /**
     * Serializes the given message to its JSON wire representation.
     *
     * @param msg the message to serialize
     * @return the serialized message
     */
    protected String serialize(CommonMessage msg) {
        return JsonUtils.serialize(msg);
    }

    /**
     * Serializes the given message and sends it, split into chunks if message splitting is active.
     *
     * @param producer the namespace-specific producer, handed back to the hooks
     * @param msg the message to send
     * @param messageType the nJAMS message type header value
     * @param clientSessionId the client session id header value
     * @throws Exception if the message cannot be sent
     */
    protected void sendMessage(Object producer, CommonMessage msg, String messageType, String clientSessionId)
        throws Exception {
        final String data = serialize(msg);
        if (splitSupport.isSplitting()) {
            sendChunks(producer, msg, data, messageType, clientSessionId);
        } else {
            final String logId;
            if (msg instanceof LogMessage) {
                logId = ((LogMessage) msg).getLogId();
            } else {
                logId = null;
            }

            final Object message = buildMessage(logId, msg.getPath(), data, messageType, clientSessionId);
            tryToSend(producer, message);
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Sent {} for {} to {}:\n{}", msg.getClass().getSimpleName(), msg.getPath(),
                describeDestination(producer), data);
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("Sent {} for {} to {}", msg.getClass().getSimpleName(), msg.getPath(),
                describeDestination(producer));
        }
    }

    private void sendChunks(Object producer, CommonMessage msg, String data, String messageType,
        String clientSessionId)
        throws Exception {
        final SplitIterator chunks = splitSupport.iterator(data);
        if (chunks.isEmpty()) {
            return;
        }
        final String messageKey;
        final String logId;
        if (msg instanceof LogMessage) {
            logId = ((LogMessage) msg).getLogId();
            messageKey = logId;
        } else {
            if (chunks.size() > 1) {
                // ensure same key for all chunks
                messageKey = UUID.randomUUID().toString();
            } else {
                messageKey = null;
            }
            logId = null;
        }
        while (chunks.hasNext()) {
            final Object message = buildMessage(logId, msg.getPath(), chunks.next(), messageType, clientSessionId);
            splitSupport.addChunkHeaders((k, v) -> setProperty(message, k, v), chunks.currentIndex(), chunks.size(),
                messageKey);
            tryToSend(producer, message);
        }
    }

    /**
     * Creates a text message carrying the given body and the nJAMS headers.
     *
     * @param logId the log id header value, or {@code null} to omit it
     * @param path the path header value
     * @param body the message text
     * @param messageType the nJAMS message type header value
     * @param clientSessionId the client session id header value
     * @return the message, to be handed back to {@link #setProperty} and {@link #tryToSend}
     * @throws Exception if the message cannot be created
     */
    protected abstract Object buildMessage(String logId, String path, String body, String messageType,
        String clientSessionId) throws Exception;

    /**
     * Sets a string property on a message created by {@link #buildMessage}. A failure is logged, not thrown.
     *
     * @param message the message
     * @param key the property name
     * @param value the property value
     */
    protected abstract void setProperty(Object message, String key, String value);

    /**
     * Sends a message created by {@link #buildMessage} through the given producer, applying the shared retry
     * handling via {@link #sendWithRetry}.
     *
     * @param producer the producer passed to {@link #sendMessage}
     * @param message the message to send
     * @throws Exception if the message cannot be sent
     */
    protected abstract void tryToSend(Object producer, Object message) throws Exception;

    /**
     * Describes the destination of the given producer for log output.
     *
     * @param producer the producer passed to {@link #sendMessage}
     * @return a printable destination description
     */
    protected abstract String describeDestination(Object producer);

    /**
     * @return the namespace's {@code ResourceAllocationException} type, whose occurrence in a failure's cause chain
     *         marks congestion.
     */
    protected abstract Class<?> congestionExceptionType();

    /**
     * @return the configured JMS delivery mode as the numeric value used by both JMS namespaces.
     */
    protected final int resolveDeliveryMode() {
        final String deliveryMode = settings.getProperty(NjamsSettings.PROPERTY_JMS_DELIVERY_MODE);
        if ("NON_PERSISTENT".equalsIgnoreCase(deliveryMode) || "NONPERSISTENT".equalsIgnoreCase(deliveryMode)) {
            LOG.debug("Set JMS delivery mode to NON_PERSISTENT.");
            return DELIVERY_MODE_NON_PERSISTENT;
        }
        if ("RELIABLE".equalsIgnoreCase(deliveryMode)) {
            LOG.debug("Set JMS delivery mode to Tibco RELIABLE.");
            return DELIVERY_MODE_TIBCO_RELIABLE;
        }
        return DELIVERY_MODE_PERSISTENT;
    }

    /**
     * @return the configured destination prefix, or the default if none is configured.
     */
    protected final String destinationPrefix() {
        final String destination = settings.getPropertyWithAlternativeKey(
            NjamsSettings.PROPERTY_JMS_DESTINATION, NjamsSettings.PROPERTY_JMS_DESTINATION_PREFIX);
        return destination != null ? destination : DEFAULT_DESTINATION;
    }

    /**
     * Logs a send failure with the JMS detail the shared retry handling does not have — that a
     * resource-allocation failure means the destination is momentarily full rather than unreachable.
     *
     * @param outcome what the SDK decided about this failure.
     * @param failure the failure itself.
     */
    @Override
    protected void logError(SendFailureOutcome outcome, Throwable failure) {
        switch (outcome) {
        case DISCARDED_BY_POLICY:
            LOG.debug("JMS destination limit exceeded; applying discard policy [{}].", discardPolicy);
            break;
        case ESCALATING:
            if (isCongestion(failure)) {
                LOG.debug("JMS destination limit exceeded; discard policy [{}] gives up on this message.",
                    discardPolicy);
            } else {
                LOG.warn("Failed to send to the JMS destination; treating the connection as broken.", failure);
            }
            break;
        default:
            LOG.debug("Retrying JMS send after failure.", failure);
            break;
        }
    }

    /**
     * Identifies a {@link #congestionExceptionType() ResourceAllocationException} anywhere in the failure's cause
     * chain as congestion: the JMS-spec-defined, provider-independent signal that the broker could not allocate
     * the resources needed to accept the message right now (e.g. a destination or store limit), which is expected
     * to resolve itself shortly as consumers drain the backlog. This holds for any compliant JMS provider — no
     * vendor-specific exception type is involved. Any other failure, including a bare {@code JMSException} or one
     * indicating the connection is technically up but not usable (e.g. an {@code InvalidDestinationException}),
     * is treated as a real connection problem.
     *
     * @param failure the failure that was reported.
     * @return {@code true} only if the cause chain contains a resource-allocation exception.
     */
    @Override
    public boolean isCongestion(Throwable failure) {
        final Class<?> congestionType = congestionExceptionType();
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (congestionType.isInstance(current)) {
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
     * JMS has no portable, provider-independent signal that distinguishes a permanently oversized message from
     * transient resource exhaustion — both surface as the same resource-allocation exception classified by
     * {@link #isCongestion}. Guessing wrong here would silently drop a message that could still have gone through,
     * so this intentionally never claims a message is permanently rejected.
     *
     * @param failure the failure that was reported; ignored.
     * @return always {@code false}.
     */
    @Override
    protected boolean isMessageRejected(Throwable failure) {
        return false;
    }
}
