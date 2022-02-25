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

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Factory for creating Sender and Receiver
 *
 * @author pnientiedt
 */
public class CommunicationFactory {

    /**
     * Property key for communication properties which specifies which
     * communication implementation will be used
     */
    public static final String COMMUNICATION = "njams.sdk.communication";
    private static final Logger LOG = LoggerFactory.getLogger(CommunicationFactory.class);

    private final Settings settings;
    private final CommunicationServiceLoader<Receiver> receivers;
    private final CommunicationServiceLoader<AbstractSender> senders;
    private static final Map<Class<? extends Receiver>, ShareableReceiver> sharedReceivers = new HashMap<>();

    /**
     * Create a new CommunicationFactory
     *
     * @param settings Settings to add
     */
    public CommunicationFactory(Settings settings) {
        this(settings, new CommunicationServiceLoader<>(Receiver.class),
            new CommunicationServiceLoader<>(Sender.class));
    }

    CommunicationFactory(Settings settings, CommunicationServiceLoader<Receiver> receivers,
        CommunicationServiceLoader<Sender> senders) {
        this.settings = settings;
        this.receivers = receivers;
        this.senders = senders;
    }

    /**
     * Returns the Receiver specified by the value of {@value #COMMUNICATION}
     * specified in the CommunicationProperties in the Settings
     *
     * @param njams The {@link Njams} client instance for that messages shall be received.
     * @return new initialized Receiver
     */
    public Receiver getReceiver(Njams njams) {
        if (settings.containsKey(COMMUNICATION)) {
            final String requiredReceiverName = settings.getProperty(COMMUNICATION);
            final boolean shared =
                "true".equalsIgnoreCase(settings.getProperty(Settings.PROPERTY_SHARED_COMMUNICATIONS));
            Class<? extends Receiver> type = findReceiverType(requiredReceiverName, shared);
            if (type != null) {
                final Receiver newInstance = createReceiver(type, njams.getClientPath(), shared, requiredReceiverName);
                newInstance.setNjams(njams);

                return newInstance;
            } else {
                String available =
                    StreamSupport.stream(Spliterators.spliteratorUnknownSize(receivers.iterator(), Spliterator.ORDERED),
                        false).map(cp -> cp.getName()).collect(Collectors.joining(", "));
                throw new UnsupportedOperationException(
                    "Unable to find receiver implementation for " + requiredReceiverName + ", available are: "
                        + available);
            }
        } else {
            throw new UnsupportedOperationException("Unable to find " + COMMUNICATION + " in settings properties");
        }
    }

    private Class<? extends Receiver> findReceiverType(String name, boolean sharable) {
        final Iterator<Receiver> iterator = receivers.iterator();
        Receiver found = null;
        while (iterator.hasNext()) {
            try {
                final Receiver receiver = iterator.next();
                if (receiver.getName().equalsIgnoreCase(name)) {
                    final boolean implementsSharable = ShareableReceiver.class.isAssignableFrom(receiver.getClass());
                    if (sharable && implementsSharable || !sharable && !implementsSharable) {
                        return receiver.getClass();
                    }
                    // keep this as last resort, but maybe we find a better one
                    found = receiver;
                }
            } catch (ServiceConfigurationError error) {
                LOG.warn("Error while trying to lazy load receiver: ", error);
            }
        }
        if (sharable && found != null) {
            LOG.info("The requested communication type '{}' does not support sharing the receiver instance. "
                + "Creating a dedicated instance instead.", found.getName());
        }
        return found == null ? null : found.getClass();
    }

    private Receiver createReceiver(Class<? extends Receiver> clazz, Path clientPath, boolean shared, String name) {
        try {
            Properties properties = settings.getAllProperties();
            properties.setProperty(Settings.INTERNAL_PROPERTY_CLIENTPATH, clientPath.toString());
            Receiver receiver;
            if (shared && ShareableReceiver.class.isAssignableFrom(clazz)) {
                synchronized (sharedReceivers) {
                    receiver = sharedReceivers.get(clazz);
                    if (receiver != null) {
                        LOG.debug("Reusing shared receiver {}", clazz);
                        return receiver;
                    }
                    LOG.debug("Creating shared receiver {}", clazz);
                    receiver = clazz.newInstance();
                    receiver.validate();
                    sharedReceivers.put(clazz, (ShareableReceiver<?>) receiver);
                    receiver.init(properties);
                    return receiver;
                }
            }
            LOG.debug("Creating dedicated receiver {}", clazz);
            receiver = clazz.newInstance();
            receiver.validate();
            receiver.init(properties);
            return receiver;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create new receiver " + name + " instance.", e);
        }
    }

    /**
     * Returns the Sender specified by the value of {@value #COMMUNICATION}
     * specified in the CommunicationProperties in the Settings
     *
     * @return new initialized Sender
     */
    public AbstractSender getSender() {
        if (settings.containsKey(COMMUNICATION)) {
            final Iterator<AbstractSender> iterator = senderList.iterator();
            final String requiredSenderName = settings.getProperty(COMMUNICATION);
            while (iterator.hasNext()) {
                final AbstractSender sender = iterator.next();
                if (sender.getName().equalsIgnoreCase(requiredSenderName)) {
                    try {
                        // create a new instance
                        LOG.info("Create sender {}", sender.getName());
                        AbstractSender newInstance = sender.getClass().newInstance();
                        newInstance.validate();
                        newInstance.init(settings.getAllProperties());
                        return newInstance;
                    } catch (Exception e) {
                        throw new UnsupportedOperationException(
                            "Unable to create new " + requiredSenderName + " instance", e);
                    }
                }
            }
            String available = StreamSupport
                    .stream(Spliterators.spliteratorUnknownSize(
                            ServiceLoader.load(AbstractSender.class).iterator(),
                            Spliterator.ORDERED), false)
                    .map(cp -> cp.getName()).collect(Collectors.joining(", "));
            throw new UnsupportedOperationException(
                "Unable to find sender implementation for " + requiredSenderName + ", available are: " + available);
        } else {
            throw new UnsupportedOperationException("Unable to find " + COMMUNICATION + " in settings properties");
        }
    }
}
