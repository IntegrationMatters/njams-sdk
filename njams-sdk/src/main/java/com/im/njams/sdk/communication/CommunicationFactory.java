/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.ClasspathValidator;

/**
 * Factory for creating Sender and Receiver
 *
 * @author pnientiedt
 */
public class CommunicationFactory {
    private static final Logger LOG = LoggerFactory.getLogger(CommunicationFactory.class);

    private final Settings settings;
    private final CommunicationServiceLoader<Receiver> receivers;
    private final CommunicationServiceLoader<AbstractSender> senders;
    private static final Map<Class<? extends Receiver>, ShareableReceiver<?>> sharedReceivers = new HashMap<>();

    /**
     * Create a new CommunicationFactory
     *
     * @param settings Settings to add
     */
    public CommunicationFactory(Settings settings) {
        this(settings, new CommunicationServiceLoader<>(Receiver.class),
                new CommunicationServiceLoader<>(AbstractSender.class));
    }

    CommunicationFactory(Settings settings, CommunicationServiceLoader<Receiver> receivers,
            CommunicationServiceLoader<AbstractSender> senders) {
        this.settings = settings;
        this.receivers = receivers;
        this.senders = senders;
    }

    /**
     * Returns the Receiver specified by the value of {@value NjamsSettings#PROPERTY_COMMUNICATION}
     * specified in the CommunicationProperties in the Settings
     *
     * @param njams The {@link Njams} client instance for that messages shall be received.
     * @return new initialized Receiver
     */
    public Receiver getReceiver(Njams njams) {
        if (settings.containsKey(NjamsSettings.PROPERTY_COMMUNICATION)) {
            String requiredReceiverName = settings.getProperty(NjamsSettings.PROPERTY_COMMUNICATION);
            if ("HTTPS".equalsIgnoreCase(requiredReceiverName)) {
                requiredReceiverName = "HTTP";
            }
            final boolean shared =
                    "true".equalsIgnoreCase(settings.getPropertyWithDeprecationWarning(
                            NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, NjamsSettings.OLD_SHARED_COMMUNICATIONS));
            Class<? extends Receiver> type = findReceiverType(requiredReceiverName, shared);
            if (type != null) {
                final Receiver newInstance = createReceiver(type, njams, shared, requiredReceiverName);
                newInstance.setNjams(njams);

                return newInstance;
            }
            Collection<String> available =
                    getAvailableImplementations(receivers).stream().map(Receiver::getName).sorted()
                            .collect(Collectors.toSet());
            throw new IllegalStateException(
                    "Unable to find receiver implementation for " + requiredReceiverName + ", available are: "
                            + available);
        }
        throw new IllegalStateException("Unable to find " + NjamsSettings.PROPERTY_COMMUNICATION
                + " in settings properties");
    }

    private Class<? extends Receiver> findReceiverType(String name, boolean wantsSharable) {
        Receiver found = null;
        for (Receiver receiver : getAvailableImplementations(receivers)) {
            if (receiver.getName().equalsIgnoreCase(name)) {
                final boolean implementsSharable = receiver instanceof ShareableReceiver;
                if (wantsSharable == implementsSharable) {
                    LOG.debug("Found receiver for criteria name={}, sharable={}: {}", name, wantsSharable,
                            receiver.getClass());
                    return receiver.getClass();
                }
                // keep this as last resort, but maybe we find a better one
                found = receiver;
            }
        }
        if (wantsSharable && found != null) {
            LOG.info("The requested communication type '{}' does not support sharing the receiver instance. "
                    + "Creating a dedicated instance instead.", found.getName());
        }
        LOG.debug("Found receiver for criteria name={}, sharable={}: {}", name, wantsSharable,
                found == null ? null : found.getClass());
        return found == null ? null : found.getClass();
    }

    private Receiver createReceiver(Class<? extends Receiver> clazz, Njams njams, boolean shared, String name) {
        try {
            Properties properties = settings.getAllProperties();
            properties.setProperty(Settings.INTERNAL_PROPERTY_CLIENTPATH, njams.getClientPath().toString());
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
                    receiver.init(properties);
                    return receiver;
                }
            }
            LOG.debug("Creating dedicated receiver {}", clazz);
            receiver = clazz.getDeclaredConstructor().newInstance();
            if (receiver instanceof ClasspathValidator) {
                ((ClasspathValidator) receiver).validate();
            }
            receiver.init(properties);
            return receiver;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create new receiver " + name + " instance.", e);
        }
    }

    /**
     * Returns the Sender specified by the value of {@value NjamsSettings#PROPERTY_COMMUNICATION}
     * specified in the CommunicationProperties in the Settings
     *
     * @return new initialized Sender
     */
    public AbstractSender getSender() {
        if (!settings.containsKey(NjamsSettings.PROPERTY_COMMUNICATION)) {
            throw new IllegalStateException("Unable to find " + NjamsSettings.PROPERTY_COMMUNICATION
                    + " in settings properties");
        }
        final Collection<AbstractSender> availableSenders = getAvailableImplementations(senders);
        final String requiredSenderName = settings.getProperty(NjamsSettings.PROPERTY_COMMUNICATION);
        final AbstractSender sender = availableSenders.stream()
                .filter(s -> s.getName().equalsIgnoreCase(requiredSenderName)).findAny().orElse(null);
        if (sender != null) {
            try {
                // create a new instance
                LOG.info("Create sender {}", sender.getName());
                AbstractSender newInstance = sender.getClass().getDeclaredConstructor().newInstance();
                if (newInstance instanceof ClasspathValidator) {
                    ((ClasspathValidator) newInstance).validate();
                }

                newInstance.init(settings.getAllProperties());
                return newInstance;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Unable to create new " + requiredSenderName + " instance", e);
            }
        }
        final Collection<String> availableNames =
                availableSenders.stream().map(AbstractSender::getName).collect(Collectors.toSet());
        throw new IllegalStateException(
                "Unable to find sender implementation for " + requiredSenderName + ", available are: "
                        + availableNames);
    }

    private <T> Collection<T> getAvailableImplementations(CommunicationServiceLoader<T> loader) {
        if (loader == null) {
            return Collections.emptyList();
        }
        final Iterator<T> iterator = loader.iterator();
        final Collection<T> available = new ArrayList<>();

        while (true) {
            try {
                // iterator.hasNext() may throw an exception if the class cannot be loaded!
                if (!iterator.hasNext()) {
                    break;
                }
                available.add(iterator.next());
            } catch (Throwable error) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Error while trying to lazy load {} service: ", loader.getServiceType().getSimpleName(),
                            error);
                } else {
                    LOG.info("Could not load {} service implementation: {}", loader.getServiceType().getSimpleName(),
                            error.toString());
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Available {} implementations {}", loader.getServiceType().getSimpleName(),
                    available.stream().map(Object::getClass).map(Class::getSimpleName)
                            .collect(Collectors.joining(",")));
        }
        return available;
    }
}
