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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.TransportNotApplicableException;
import com.im.njams.sdk.communication.fragments.JMSChunkAssembly;
import com.im.njams.sdk.communication.fragments.RawMessage;
import com.im.njams.sdk.communication.fragments.SplitSupport;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * The part of the JMS receiver that does not touch a JMS type, shared by the {@code javax.jms} and the
 * {@code jakarta.jms} receiver.
 * <p>
 * Internal SDK infrastructure, not an extension point: it is public only so that the receiver implementations in
 * other packages can extend it. Messages are passed as {@link Object} so that no JMS namespace is named here; the
 * abstract hooks are where a subclass supplies them.
 * </p>
 */
public abstract class AbstractJmsReceiver extends AbstractReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJmsReceiver.class);

    /** The name of the commands topic this receiver listens on. */
    protected String topicName = null;

    /**
     * The message property naming the receiver a command is addressed to. Used both in the broker-side
     * selector built here and in the client-side filter the subclass builds.
     */
    protected static final String NJAMS_RECEIVER_PROPERTY = "NJAMS_RECEIVER";

    /**
     * The mapper used for reading and writing instructions. Deliberately not {@code protected}: {@link ObjectMapper}
     * is relocated when the SDK is shaded and must not appear on a public or protected member.
     */
    private ObjectMapper mapper = null;

    /** Whether the broker supports message selectors; if not, {@link #messageFilter} is used instead. */
    protected boolean useMessageselector = true;

    /** The broker-side message selector, or <code>null</code> if none applies. */
    protected String messageSelector = null;

    /** The client-side replacement for {@link #messageSelector} when the broker supports no selectors. */
    protected Predicate<Object> messageFilter = m -> true;

    /** Messages older than this are discarded by {@link #messageFilter}. */
    protected long oldestMessageTime = Long.MAX_VALUE;

    /** Splits outgoing replies that exceed the configured message size. */
    protected SplitSupport splitSupport = null;

    /** Reassembles inbound commands that arrived split into several messages. */
    protected final JMSChunkAssembly chunkAssembly = new JMSChunkAssembly();

    /**
     * Returns the name for this Receiver. (JMS)
     *
     * @return the name of this Receiver. (JMS)
     */
    @Override
    public String getName() {
        return AbstractJmsSender.COMMUNICATION_NAME;
    }

    /**
     * Initializes this Receiver via the given settings.
     * <p>
     * Valid settings are:
     * <ul>
     * <li>{@value NjamsSettings#PROPERTY_JMS_CONNECTION_FACTORY}
     * <li>{@value NjamsSettings#PROPERTY_JMS_USERNAME}
     * <li>{@value NjamsSettings#PROPERTY_JMS_PASSWORD}
     * <li>{@value NjamsSettings#PROPERTY_JMS_DESTINATION} (or its alternative
     * {@value NjamsSettings#PROPERTY_JMS_DESTINATION_PREFIX})
     * <li>...
     * </ul>
     * For more look in the github FAQ of this project.
     *
     * @param settings the settings needed to init
     * @throws TransportNotApplicableException if the configured JMS factory belongs to the other JMS namespace
     */
    @Override
    public void init(ClientSettings settings) {
        resolveFactory(settings);
        super.init(settings);
        connectionStatus = ConnectionStatus.DISCONNECTED;
        mapper = JsonSerializerFactory._internal().getFastMapper();
        if (StringUtils.isNotBlank(settings.getProperty(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION))) {
            topicName = settings.getProperty(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION);
        } else {
            topicName = settings.getPropertyWithAlternativeKey(NjamsSettings.PROPERTY_JMS_DESTINATION,
                AbstractJmsSender.DEFAULT_DESTINATION, NjamsSettings.PROPERTY_JMS_DESTINATION_PREFIX)
                + ".commands";
        }
        useMessageselector =
            !"false".equalsIgnoreCase(settings.getProperty(NjamsSettings.PROPERTY_JMS_SUPPORTS_MESSAGE_SELECTOR));

        splitSupport = new SplitSupport(settings, -1);
    }

    /**
     * Resolves the JMS factory this receiver will use. Called first during {@link #init(ClientSettings)}.
     * Implementations signal that the configured factory belongs to the other JMS namespace by throwing a
     * {@link TransportNotApplicableException}.
     * <p>
     * Implementations must read from the {@code settings} parameter: the inherited {@code settings} field is
     * only assigned after this hook has returned.
     *
     * @param settings the settings the receiver is initialized with
     */
    protected abstract void resolveFactory(ClientSettings settings);

    /**
     * This method creates a String that is used as a message selector.
     *
     * @return the message selector String.
     */
    protected String createMessageSelector() {
        if (!useMessageselector) {
            return null;
        }
        final Collection<Njams> njamsInstances = provideNjamsInstances();
        if (njamsInstances.isEmpty()) {
            return null;
        }
        final String selector = njamsInstances.stream().map(n -> n.metadata().getClientPath())
            .flatMap(AbstractJmsReceiver::ancestorPathStrings)
            .sorted()
            .collect(Collectors.joining("' OR " + NJAMS_RECEIVER_PROPERTY + " = '",
                NJAMS_RECEIVER_PROPERTY + " = '", "'"));
        LOG.debug("Updated message selector: {}", selector);
        return selector;
    }

    /**
     * Creates the client-side message filter used when the broker does not support message selectors. The
     * predicate's body needs the JMS message type, so it is supplied by the namespace-specific subclass.
     *
     * @return the filter to store in {@link #messageFilter}.
     */
    protected abstract Predicate<Object> createMessageFilter();

    /**
     * @param path the path to walk upwards.
     * @return the given path and all its ancestors below the root, as strings.
     */
    protected static Stream<String> ancestorPathStrings(Path path) {
        List<String> result = new ArrayList<>();
        for (Path p = path; !p.isRoot(); p = p.getParent()) {
            result.add(p.toString());
        }
        return result.stream();
    }

    /**
     * Has to provide all {@link Njams} instances that use this receiver for selecting the commands addressed to any
     * of these instances.
     * @return All instances that use this receiver.
     */
    protected Collection<Njams> provideNjamsInstances() {
        // the non-shared receiver only serves one Njams instance
        return Collections.singleton(njams);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Also recomputes the message selector and filter, so that they cover the newly registered instance.
     */
    @Override
    public void setNjams(Njams njams) {
        super.setNjams(njams);
        if (njams != null) {
            updateFilters();
        }
    }

    /**
     * Recomputes the broker-side message selector and the client-side message filter from the currently registered
     * {@link Njams} instances.
     */
    protected void updateFilters() {
        LOG.debug("Updating selector/filter");
        messageSelector = createMessageSelector();
        messageFilter = createMessageFilter();
    }

    /**
     * Sets the connection status to DISCONNECTED and closes all resources that have been saved as fields.
     *
     * @return a list of exceptions that may have been thrown by any of the resources.
     */
    protected abstract List<Exception> closeAll();

    /**
     * This method is used on close and logs all exceptions that have been given in the provided list
     * of exceptions that occurred when trying to close the JMS connection.
     *
     * @param exceptions the exceptions that will be logged.
     */
    protected void printExceptionsOnClose(List<Exception> exceptions) {
        exceptions.forEach(e -> {
            String msg;
            if (e instanceof NjamsSdkRuntimeException) {
                msg = e.getMessage();
                final Throwable cause = e.getCause();
                if (cause != null) {
                    msg += ". Caused by: " + cause.toString();
                }
            } else {
                msg = e.toString();
            }
            LOG.warn(msg);
            LOG.debug(msg, e);
        });
    }

    /**
     * This method stops the Jms Receiver by closing all its resources, if its
     * status is CONNECTED.
     */
    @Override
    public void stop() {
        if (!isConnected()) {
            return;
        }
        List<Exception> exceptions = this.closeAll();
        if (!exceptions.isEmpty()) {
            printExceptionsOnClose(exceptions);
            LOG.warn("Unable to close {} correctly.", getClass().getSimpleName());
        } else {
            LOG.info("{} has been stopped successfully.", getClass().getSimpleName());
        }
    }

    /**
     * Maps the JSON body of a fully assembled command message to an {@link Instruction}.
     *
     * @param resolved the assembled message, or <code>null</code> if it is still incomplete.
     * @return the {@link Instruction} that was extracted, or <code>null</code> if the message was not yet complete
     * or did not contain a valid instruction.
     */
    protected final Instruction deserializeInstruction(RawMessage resolved) {
        if (resolved == null) {
            LOG.debug("Received incomplete command message.");
            return null;
        }
        try {
            final Instruction instruction = mapper.readValue(resolved.getBody(), Instruction.class);
            if (instruction.getRequest() != null) {
                return instruction;
            }
        } catch (Exception e) {
            LOG.error("Error deserializing instruction: {}", resolved.getBody(), e);
        }
        LOG.warn("Illegal instruction format in: {}", resolved.getBody());
        return null;
    }

    /**
     * Serializes the given instruction to the JSON body of a reply message.
     *
     * @param instruction the instruction to serialize.
     * @return the instruction as JSON.
     * @throws IOException if the instruction cannot be serialized.
     */
    protected final String serializeInstruction(Instruction instruction) throws IOException {
        return mapper.writeValueAsString(instruction);
    }
}
