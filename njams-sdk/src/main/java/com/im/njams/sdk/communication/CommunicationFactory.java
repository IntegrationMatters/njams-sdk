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

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.settings.Settings;

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
    private final Njams njams;
    private ServiceLoader<Receiver> receiverList;
    private ServiceLoader<Sender> senderList;

    /**
     * Create a new CommunicationFactory
     *
     * @param njams Njams to add
     * @param settings Settings to add
     */
    public CommunicationFactory(Njams njams, Settings settings) {
        this.njams = njams;
        this.settings = settings;
        receiverList = ServiceLoader.load(Receiver.class);
        senderList = ServiceLoader.load(Sender.class);
    }

    /**
     * Returns the Receiver specified by the value of {@value #COMMUNICATION}
     * specified in the CommunicationProperties in the Settings
     *
     * @return new initialized Receiver
     */
    public Receiver getReceiver() {
        if (settings.containsKey(COMMUNICATION)) {
            final Iterator<Receiver> iterator = receiverList.iterator();
            final String requiredReceiverName = settings.getProperty(COMMUNICATION);
            while (iterator.hasNext()) {
                final Receiver receiver = iterator.next();
                if (receiver.getName().equals(requiredReceiverName)) {
                    try {
                        // create a new instance
                        LOG.info("Create Receiver {}", receiver.getName());
                        Receiver newInstance = receiver.getClass().newInstance();
                        newInstance.validate();
                        newInstance.setNjams(njams);
                        newInstance.init(settings.getAllProperties());
                        return newInstance;
                    } catch (Exception e) {
                        throw new UnsupportedOperationException(
                                "Unable to create new " + requiredReceiverName + " instance", e);
                    }
                }
            }
            String available = StreamSupport
                    .stream(Spliterators.spliteratorUnknownSize(
                            ServiceLoader.load(Receiver.class).iterator(),
                            Spliterator.ORDERED), false)
                    .map(cp -> cp.getName()).collect(Collectors.joining(", "));
            throw new UnsupportedOperationException("Unable to find receiver implementation for " + requiredReceiverName
                    + ", available are: " + available);
        } else {
            throw new UnsupportedOperationException("Unable to find " + COMMUNICATION + " in settings properties");
        }
    }

    /**
     * Returns the Sender specified by the value of {@value #COMMUNICATION}
     * specified in the CommunicationProperties in the Settings
     *
     * @return new initialized Sender
     */
    public Sender getSender() {
        if (settings.containsKey(COMMUNICATION)) {
            final Iterator<Sender> iterator = senderList.iterator();
            final String requiredSenderName = settings.getProperty(COMMUNICATION);
            while (iterator.hasNext()) {
                final Sender sender = iterator.next();
                if (sender.getName().equals(requiredSenderName)) {
                    try {
                        // create a new instance
                        LOG.info("Create sender {}", sender.getName());
                        Sender newInstance = sender.getClass().newInstance();
                        newInstance.validate();
                        newInstance.setNjams(njams);
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
                            ServiceLoader.load(Sender.class).iterator(),
                            Spliterator.ORDERED), false)
                    .map(cp -> cp.getName()).collect(Collectors.joining(", "));
            throw new UnsupportedOperationException(
                    "Unable to find sender implementation for " + requiredSenderName + ", available are: " + available);
        } else {
            throw new UnsupportedOperationException("Unable to find " + COMMUNICATION + " in settings properties");
        }
    }
}