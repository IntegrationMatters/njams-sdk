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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.ClasspathValidator;
import com.im.njams.sdk.utils.ServiceLoaderSupport;

/**
 * Factory for creating Sender and Receiver
 *
 * @author pnientiedt
 */
public class CommunicationFactory {
    private static final Logger LOG = LoggerFactory.getLogger(CommunicationFactory.class);

    /**
     * Property key used internally to pass the client path from the {@link Njams} instance to
     * senders and receivers via their configuration properties. The {@code "njams.$"} prefix marks
     * the key as not user-configurable.
     */
    public static final String INTERNAL_PROPERTY_CLIENTPATH = "njams.$clientPath";

    private final ClientSettings settings;
    // the sender class that was selected first; the candidate selection is not repeated for further senders.
    // Assumes the communication type does not change during this factory's lifetime.
    private volatile Class<? extends AbstractSender> resolvedSenderClass;
    private final Object senderResolutionLock = new Object();
    private static final Map<Class<? extends AbstractReceiver>, AbstractReceiver> sharedReceivers = new HashMap<>();

    /**
     * Create a new CommunicationFactory
     *
     * @param settings Settings to add
     */
    public CommunicationFactory(ClientSettings settings) {
        this.settings = settings;
    }

    ServiceLoaderSupport<AbstractReceiver> getReceiverLoader() {
        return new ServiceLoaderSupport<>(AbstractReceiver.class);
    }

    ServiceLoaderSupport<AbstractSender> getSenderLoader() {
        return new ServiceLoaderSupport<>(AbstractSender.class);
    }

    /**
     * Returns the Receiver specified by the value of {@value NjamsSettings#PROPERTY_COMMUNICATION} (or its
     * alternative {@value NjamsSettings#PROPERTY_COMMUNICATION_TYPE}) specified in the CommunicationProperties
     * in the Settings
     *
     * @param njams The {@link Njams} client instance for that messages shall be received.
     * @return new initialized Receiver
     */
    public AbstractReceiver getReceiver(Njams njams) {
        String requiredReceiverName = settings.getPropertyWithAlternativeKey(
                NjamsSettings.PROPERTY_COMMUNICATION, NjamsSettings.PROPERTY_COMMUNICATION_TYPE);
        if (requiredReceiverName != null) {
            if ("HTTPS".equalsIgnoreCase(requiredReceiverName)) {
                requiredReceiverName = "HTTP";
            }
            final boolean shared = settings.getBool(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, false);
            final ServiceLoaderSupport<AbstractReceiver> receivers = getReceiverLoader();
            List<Class<? extends AbstractReceiver>> candidates =
                    findReceiverCandidates(receivers, requiredReceiverName, shared);
            if (!candidates.isEmpty()) {
                final AbstractReceiver newInstance =
                        createReceiver(candidates, njams, shared, requiredReceiverName, receivers);
                if (shared && !(newInstance instanceof ShareableReceiver)) {
                    LOG.info("Communication type '{}' uses a dedicated receiver instance per client; sharing "
                        + "applies to the sender only.", newInstance.getName());
                }
                newInstance.setNjams(njams);

                return newInstance;
            }
            final String error = "Unable to find receiver implementation for communication type '"
                + requiredReceiverName + "' configured by setting " + NjamsSettings.PROPERTY_COMMUNICATION + ".";
            LOG.error("{}{}{}", error, System.lineSeparator(),
                    receivers.describeAvailability(AbstractReceiver::getName));
            throw new IllegalStateException(error + " " + ServiceLoaderSupport.SEE_LOG_HINT);
        }
        throw new IllegalStateException("Unable to find " + NjamsSettings.PROPERTY_COMMUNICATION
                + " (or its alternative " + NjamsSettings.PROPERTY_COMMUNICATION_TYPE + ") in settings properties");
    }

    /**
     * All receivers matching the given name, in the order they are to be tried: those matching the wish for a
     * sharable receiver first, the others after them; the services-file order is kept within each group.
     */
    private List<Class<? extends AbstractReceiver>> findReceiverCandidates(
        ServiceLoaderSupport<AbstractReceiver> receivers, String name, boolean wantsSharable) {
        final List<AbstractReceiver> matches = receivers.findAll(r -> r.getName().equalsIgnoreCase(name));
        final List<Class<? extends AbstractReceiver>> candidates = new ArrayList<>();
        final List<Class<? extends AbstractReceiver>> others = new ArrayList<>();
        for (AbstractReceiver match : matches) {
            (wantsSharable == match instanceof ShareableReceiver ? candidates : others).add(match.getClass());
        }
        candidates.addAll(others);
        LOG.debug("Found receivers for criteria name={}, sharable={}: {}", name, wantsSharable, candidates);
        return candidates;
    }

    private AbstractReceiver createReceiver(List<Class<? extends AbstractReceiver>> candidates, Njams njams,
        boolean shared, String name, ServiceLoaderSupport<AbstractReceiver> receivers) {
        Map<String, String> copy = new LinkedHashMap<>();
        settings.forEach(e -> copy.put(e.getKey(), e.getValue()));
        ClientSettings receiverSettings = ClientSettings.from(copy);
        receiverSettings.put(INTERNAL_PROPERTY_CLIENTPATH, njams.metadata().getClientPath().toString());
        return this.<AbstractReceiver>selectApplicable("receiver", name, candidates,
                clazz -> createReceiver(clazz, shared, receiverSettings),
                "Unable to create new receiver " + name + " instance.",
                () -> receivers.describeAvailability(AbstractReceiver::getName));
    }

    private AbstractReceiver createReceiver(Class<? extends AbstractReceiver> clazz, boolean shared,
        ClientSettings receiverSettings) throws ReflectiveOperationException {
        AbstractReceiver receiver;
        if (shared && ShareableReceiver.class.isAssignableFrom(clazz)) {
            synchronized (sharedReceivers) {
                receiver = sharedReceivers.get(clazz);
                if (receiver != null) {
                    LOG.debug("Reusing shared receiver {}", clazz);
                    return receiver;
                }
                LOG.debug("Creating shared receiver {}", clazz);
                receiver = newValidatedInstance(clazz);
                receiver.init(receiverSettings);
                // cached only after a successful init, so a failing receiver is never handed out later
                sharedReceivers.put(clazz, receiver);
                return receiver;
            }
        }
        LOG.debug("Creating dedicated receiver {}", clazz);
        receiver = newValidatedInstance(clazz);
        receiver.init(receiverSettings);
        return receiver;
    }

    private static <T> T newValidatedInstance(Class<? extends T> clazz) throws ReflectiveOperationException {
        final T instance = clazz.getDeclaredConstructor().newInstance();
        if (instance instanceof ClasspathValidator) {
            ((ClasspathValidator) instance).validate();
        }
        return instance;
    }

    /** Creates and initializes one candidate; throws {@link TransportNotApplicableException} to be skipped. */
    @FunctionalInterface
    private interface CandidateFactory<T> {
        T create(Class<? extends T> candidate) throws Exception;
    }

    /**
     * Tries the given candidates in order and returns the first that can be created and initialized. A candidate
     * that throws {@link TransportNotApplicableException} is skipped; any other failure is final, since silently
     * replacing a misconfigured transport by another implementation would hide the problem. The candidates must
     * not be empty.
     */
    private <T> T selectApplicable(String kind, String name, List<Class<? extends T>> candidates,
        CandidateFactory<T> factory, String failureMessage, Supplier<String> availability) {
        final List<String> declined = new ArrayList<>();
        final List<TransportNotApplicableException> causes = new ArrayList<>();
        for (Class<? extends T> candidate : candidates) {
            try {
                return factory.create(candidate);
            } catch (TransportNotApplicableException e) {
                LOG.debug("The {} {} is not applicable.", kind, candidate.getName(), e);
                declined.add(candidate.getName() + ": " + e.getMessage());
                causes.add(e);
            } catch (Exception e) {
                throw new IllegalStateException(failureMessage, e);
            }
        }
        final String error = "No " + kind + " implementation for communication type '" + name
            + "' is applicable: " + String.join("; ", declined) + ".";
        LOG.error("{}{}{}", error, System.lineSeparator(), availability.get());
        final IllegalStateException failure =
            new IllegalStateException(error + " " + ServiceLoaderSupport.SEE_LOG_HINT);
        causes.forEach(failure::addSuppressed);
        throw failure;
    }

    /**
     * Removes the given {@link Njams} instance from the given shared receiver and, if that was the last user,
     * evicts the receiver from the shared-receiver cache — both steps performed atomically under this factory's
     * shared-receiver lock, so that a later {@link Njams} concurrently calling {@link #getReceiver(Njams)} for the
     * same receiver type can never observe the receiver as still cached after it has genuinely stopped. Without
     * this atomicity, {@link ShareableReceiver#removeNjams(Njams)} reporting "last user" and the cache eviction
     * would be two separate, un-synchronized steps, leaving a window in which a concurrently constructing
     * {@code Njams} is handed the doomed instance right before it is evicted (SDK-375 final-review finding).
     * <p>
     * A stopped {@link AbstractReceiver} cannot be reconnected: its {@code connectBegun} flag is a one-shot,
     * {@code final} field that {@link AbstractReceiver#beginConnect()} never resets, and once {@code
     * shouldShutdown} is set its reconnect loop permanently refuses to run. Evicting the dead instance — rather
     * than attempting to reset and reuse it — is therefore the only way a later {@link Njams} sharing the same
     * receiver type can connect at all.
     * <p>
     * This method is {@code public} because {@link Njams}, which needs to call it, lives in a different package;
     * the communication layer as a whole remains internal SDK infrastructure and not public API (see the
     * project's API design rules).
     *
     * @param receiver the shared receiver instance the given {@code njams} is being removed from.
     * @param njams the {@link Njams} instance to remove.
     * @return {@code true} if this was the last user, i.e., the receiver has genuinely stopped and been evicted;
     *         {@code false} if other instances still use it.
     * @since 6.1.0
     */
    public static boolean removeNjamsFromSharedReceiver(ShareableReceiver<?> receiver, Njams njams) {
        synchronized (sharedReceivers) {
            boolean reallyStopped = receiver.removeNjams(njams);
            if (reallyStopped) {
                sharedReceivers.remove(receiver.getClass(), receiver);
            }
            return reallyStopped;
        }
    }

    /**
     * Test-support only: unconditionally clears the entire shared-receiver cache, regardless of whether any
     * cached receiver has genuinely stopped. Production code must never call this.
     * <p>
     * It exists so lifecycle tests in a different package (e.g. a {@code SharedReceiverRestartSpecTest} under
     * {@code com.im.njams.sdk.communication.lifecycle}) can reset this static state unconditionally in their
     * teardown, without depending on every test's {@code Njams.stop()} call having actually run — a test whose
     * {@code Njams.start()} unexpectedly returns {@code false} would otherwise skip {@code stop()} and leave a
     * shut-down shared receiver cached, poisoning every later shared-receiver test in the same JVM (SDK-375
     * final-review finding on test static-state fragility). {@code public} only because that test lives in a
     * different package ({@code com.im.njams.sdk.communication.lifecycle}); kept to this single, narrow method
     * rather than exposing the cache itself.
     */
    public static void clearSharedReceiversForTesting() {
        synchronized (sharedReceivers) {
            sharedReceivers.clear();
        }
    }

    /**
     * Returns the Sender specified by the value of {@value NjamsSettings#PROPERTY_COMMUNICATION} (or its
     * alternative {@value NjamsSettings#PROPERTY_COMMUNICATION_TYPE}) specified in the CommunicationProperties
     * in the Settings
     *
     * @return new initialized Sender
     */
    public AbstractSender getSender() {
        final String requiredSenderName = requireSenderName();
        Class<? extends AbstractSender> senderClass = resolvedSenderClass;
        if (senderClass == null) {
            synchronized (senderResolutionLock) {
                senderClass = resolvedSenderClass;
                if (senderClass == null) {
                    final AbstractSender sender = selectSender(requiredSenderName);
                    resolvedSenderClass = sender.getClass();
                    return sender;
                }
            }
        }
        return createSender(senderClass, requiredSenderName);
    }

    private AbstractSender createSender(Class<? extends AbstractSender> senderClass, String requiredSenderName) {
        try {
            return createInitializedSender(senderClass);
        } catch (Exception e) {
            throw new IllegalStateException(senderCreationFailure(requiredSenderName), e);
        }
    }

    private static String senderCreationFailure(String requiredSenderName) {
        return "Unable to create new " + requiredSenderName + " instance";
    }

    private AbstractSender createInitializedSender(Class<? extends AbstractSender> senderClass)
        throws ReflectiveOperationException {
        LOG.debug("Create sender {}", senderClass.getName());
        final AbstractSender newInstance = newValidatedInstance(senderClass);
        newInstance.init(settings);
        return newInstance;
    }

    private AbstractSender selectSender(String requiredSenderName) {
        final ServiceLoaderSupport<AbstractSender> senders = getSenderLoader();
        return this.<AbstractSender>selectApplicable("sender", requiredSenderName,
                findSenderCandidates(senders, requiredSenderName),
                this::createInitializedSender, senderCreationFailure(requiredSenderName),
                () -> senders.describeAvailability(AbstractSender::getName));
    }

    /**
     * Checks the settings for creating a sender, without creating one: the communication type must be
     * configured, and at least one implementation must be registered for it. Whether such an implementation is
     * applicable in this environment is only found out when the sender is created.
     *
     * @throws IllegalStateException if the communication type is not configured, or no sender implementation is
     *         registered for it.
     */
    public void validateSenderConfiguration() {
        findSenderCandidates(getSenderLoader(), requireSenderName());
    }

    /**
     * @return the configured communication type.
     * @throws IllegalStateException if neither {@value NjamsSettings#PROPERTY_COMMUNICATION} nor its alternative
     *         {@value NjamsSettings#PROPERTY_COMMUNICATION_TYPE} is set.
     */
    private String requireSenderName() {
        final String requiredSenderName = settings.getPropertyWithAlternativeKey(
                NjamsSettings.PROPERTY_COMMUNICATION, NjamsSettings.PROPERTY_COMMUNICATION_TYPE);
        if (requiredSenderName == null) {
            throw new IllegalStateException("Unable to find " + NjamsSettings.PROPERTY_COMMUNICATION
                    + " (or its alternative " + NjamsSettings.PROPERTY_COMMUNICATION_TYPE
                    + ") in settings properties");
        }
        return requiredSenderName;
    }

    /**
     * Looks up the registered senders for the given communication type. Several may share one name; they are
     * returned in the order of the services files. The returned classes are only candidates: {@link #getSender()}
     * creates the one used.
     *
     * @param senders the loader to look up the senders with.
     * @param requiredSenderName the configured communication type.
     * @return the registered sender implementations for that type, never empty.
     * @throws IllegalStateException if no implementation is registered for that type.
     */
    private List<Class<? extends AbstractSender>> findSenderCandidates(
        final ServiceLoaderSupport<AbstractSender> senders, final String requiredSenderName) {
        final List<Class<? extends AbstractSender>> candidates = new ArrayList<>();
        for (AbstractSender sender : senders.findAll(s -> s.getName().equalsIgnoreCase(requiredSenderName))) {
            candidates.add(sender.getClass());
        }
        if (!candidates.isEmpty()) {
            return candidates;
        }
        final String error = "Unable to find sender implementation for communication type '" + requiredSenderName
            + "' configured by setting " + NjamsSettings.PROPERTY_COMMUNICATION + ".";
        LOG.error("{}{}{}", error, System.lineSeparator(), senders.describeAvailability(AbstractSender::getName));
        throw new IllegalStateException(error + " " + ServiceLoaderSupport.SEE_LOG_HINT);
    }

}
