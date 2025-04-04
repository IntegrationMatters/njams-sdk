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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.ClasspathValidator;
import com.im.njams.sdk.utils.ServiceLoaderSupport;

/**
 * Factory for creating Sender and Receiver
 *
 * @author pnientiedt
 */
public class CommunicationFactory {
    private static final Logger LOG = LoggerFactory.getLogger(CommunicationFactory.class);

    private final Settings settings;
    private final ServiceLoaderSupport<Receiver> receivers;
    private final ServiceLoaderSupport<AbstractSender> senders;
    private static final Map<Class<? extends Receiver>, ShareableReceiver<?>> sharedReceivers = new HashMap<>();

    /**
     * Create a new CommunicationFactory
     *
     * @param settings Settings to add
     */
    public CommunicationFactory(Settings settings) {
        this(settings, new ServiceLoaderSupport<>(Receiver.class),
            new ServiceLoaderSupport<>(AbstractSender.class));
    }

    CommunicationFactory(Settings settings, ServiceLoaderSupport<Receiver> receivers,
        ServiceLoaderSupport<AbstractSender> senders) {
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
            Collection<String> available = receivers.stream().map(Receiver::getName).sorted()
                .collect(Collectors.toSet());
            throw new IllegalStateException(
                "Unable to find receiver implementation for " + requiredReceiverName + ", available are: "
                    + available);
        }
        throw new IllegalStateException("Unable to find " + NjamsSettings.PROPERTY_COMMUNICATION
            + " in settings properties");
    }

    private Class<? extends Receiver> findReceiverType(String name, boolean wantsSharable) {
        Receiver found =
            receivers.find(r -> r.getName().equalsIgnoreCase(name) && wantsSharable == r instanceof ShareableReceiver);
        if (found == null) {
            found = receivers.find(r -> r.getName().equalsIgnoreCase(name));
            if (wantsSharable && found != null) {
                LOG.warn("The requested communication type '{}' does not support sharing the receiver instance. "
                    + "Creating a dedicated instance instead.", found.getName());
            }
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
        final String requiredSenderName = settings.getProperty(NjamsSettings.PROPERTY_COMMUNICATION);
        final AbstractSender sender = senders.find(s -> s.getName().equalsIgnoreCase(requiredSenderName));
        if (sender != null) {
            try {
                // create a new instance
                LOG.info("Create sender {}", sender.getName());
                AbstractSender newInstance = sender.getClass().getDeclaredConstructor().newInstance();
                if (newInstance instanceof ClasspathValidator) {
                    ((ClasspathValidator) newInstance).validate();
                }

                newInstance.init(settings.getAllProperties());
                newInstance.startup();
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
