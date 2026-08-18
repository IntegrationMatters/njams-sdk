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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
    private static final Map<Class<? extends Receiver>, ShareableReceiver<?>> sharedReceivers = new HashMap<>();

    /**
     * Create a new CommunicationFactory
     *
     * @param settings Settings to add
     */
    public CommunicationFactory(ClientSettings settings) {
        this.settings = settings;
    }

    ServiceLoaderSupport<Receiver> getReceiverLoader() {
        return new ServiceLoaderSupport<>(Receiver.class);
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
    public Receiver getReceiver(Njams njams) {
        String requiredReceiverName = settings.getPropertyWithAlternativeKey(
                NjamsSettings.PROPERTY_COMMUNICATION, NjamsSettings.PROPERTY_COMMUNICATION_TYPE);
        if (requiredReceiverName != null) {
            if ("HTTPS".equalsIgnoreCase(requiredReceiverName)) {
                requiredReceiverName = "HTTP";
            }
            final boolean shared = settings.getBool(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, false);
            Class<? extends Receiver> type = findReceiverType(requiredReceiverName, shared);
            if (type != null) {
                final Receiver newInstance = createReceiver(type, njams, shared, requiredReceiverName);
                newInstance.setNjams(njams);

                return newInstance;
            }
            Collection<String> available = getReceiverLoader().stream().map(Receiver::getName).sorted()
                    .collect(Collectors.toSet());
            throw new IllegalStateException(
                    "Unable to find receiver implementation for " + requiredReceiverName + ", available are: "
                            + available);
        }
        throw new IllegalStateException("Unable to find " + NjamsSettings.PROPERTY_COMMUNICATION
                + " (or its alternative " + NjamsSettings.PROPERTY_COMMUNICATION_TYPE + ") in settings properties");
    }

    private Class<? extends Receiver> findReceiverType(String name, boolean wantsSharable) {
        final ServiceLoaderSupport<Receiver> receivers = getReceiverLoader();
        Receiver found =
                receivers.find(
                        r -> r.getName().equalsIgnoreCase(name) && wantsSharable == r instanceof ShareableReceiver);
        if (found == null) {
            found = receivers.find(r -> r.getName().equalsIgnoreCase(name));
            if (wantsSharable && found != null) {
                LOG.info("Communication type '{}' uses a dedicated receiver instance per client; sharing applies "
                    + "to the sender only.", found.getName());
            }
        }
        LOG.debug("Found receiver for criteria name={}, sharable={}: {}", name, wantsSharable,
                found == null ? null : found.getClass());
        return found == null ? null : found.getClass();
    }

    private Receiver createReceiver(Class<? extends Receiver> clazz, Njams njams, boolean shared, String name) {
        try {
            Map<String, String> copy = new LinkedHashMap<>();
            settings.forEach(e -> copy.put(e.getKey(), e.getValue()));
            ClientSettings receiverSettings = ClientSettings.from(copy);
            receiverSettings.put(INTERNAL_PROPERTY_CLIENTPATH, njams.getClientPath().toString());
            Receiver receiver;
            if (shared && ShareableReceiver.class.isAssignableFrom(clazz)) {
                synchronized (sharedReceivers) {
                    receiver = sharedReceivers.get(clazz);
                    if (receiver != null) {
                        LOG.debug("Reusing shared receiver {}", clazz);
                        return receiver;
                    }
                    LOG.debug("Creating shared receiver {}", clazz);
                    receiver = clazz.getDeclaredConstructor().newInstance();
                    if (receiver instanceof ClasspathValidator) {
                        ((ClasspathValidator) receiver).validate();
                    }
                    sharedReceivers.put(clazz, (ShareableReceiver<?>) receiver);
                    receiver.init(receiverSettings);
                    return receiver;
                }
            }
            LOG.debug("Creating dedicated receiver {}", clazz);
            receiver = clazz.getDeclaredConstructor().newInstance();
            if (receiver instanceof ClasspathValidator) {
                ((ClasspathValidator) receiver).validate();
            }
            receiver.init(receiverSettings);
            return receiver;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create new receiver " + name + " instance.", e);
        }
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
     * @since 6.0.0
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
        final String requiredSenderName = settings.getPropertyWithAlternativeKey(
                NjamsSettings.PROPERTY_COMMUNICATION, NjamsSettings.PROPERTY_COMMUNICATION_TYPE);
        if (requiredSenderName == null) {
            throw new IllegalStateException("Unable to find " + NjamsSettings.PROPERTY_COMMUNICATION
                    + " in settings properties");
        }
        final ServiceLoaderSupport<AbstractSender> senders = getSenderLoader();
        final AbstractSender sender = senders.find(s -> s.getName().equalsIgnoreCase(requiredSenderName));
        if (sender != null) {
            try {
                // create a new instance
                LOG.info("Create sender {}", sender.getName());
                AbstractSender newInstance = sender.getClass().getDeclaredConstructor().newInstance();
                if (newInstance instanceof ClasspathValidator) {
                    ((ClasspathValidator) newInstance).validate();
                }

                newInstance.init(settings);
                return newInstance;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Unable to create new " + requiredSenderName + " instance", e);
            }
        }
        final Collection<String> availableNames =
                senders.getAll().stream().map(AbstractSender::getName).sorted().collect(Collectors.toSet());
        throw new IllegalStateException(
                "Unable to find sender implementation for " + requiredSenderName + ", available are: "
                        + availableNames);
    }

}
