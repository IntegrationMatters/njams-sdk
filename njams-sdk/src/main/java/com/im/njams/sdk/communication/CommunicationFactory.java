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
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.configuration.ConfigurationProvider;
import com.im.njams.sdk.settings.PropertyUtil;
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

    /**
     * Create a new CommunicationFactory
     *
     * @param njams Njams to add
     * @param settings Settings to add
     */
    public CommunicationFactory(Njams njams, Settings settings) {
        this.njams = njams;
        this.settings = settings;
    }

    /**
     * Returns the Receiver specified by the value of {@value #COMMUNICATION}
     * specified in the CommunicationProperties in the Settings
     *
     * @return new initialized Receiver
     */
    public Receiver getReceiver() {
        if (settings.getProperties().containsKey(COMMUNICATION)) {
            final ServiceLoader<Receiver> receiverList = ServiceLoader.load(Receiver.class);
            final Iterator<Receiver> iterator = receiverList.iterator();
            final String requiredReceiverName = settings.getProperties().getProperty(COMMUNICATION);
            while (iterator.hasNext()) {
                final Receiver receiver = iterator.next();
                if (receiver.getName().equals(requiredReceiverName)) {
                    LOG.info("Create Receiver {}", receiver.getName());
                    receiver.setNjams(njams);
                    final Properties receiverProperties =
                            PropertyUtil.filter(settings.getProperties(), receiver.getPropertyPrefix());
                    LOG.info("Connection properties for receiver:" + System.lineSeparator() + receiverProperties);

                    receiver.init(receiverProperties);
                    return receiver;
                }
            }
            String available = StreamSupport
                    .stream(Spliterators.spliteratorUnknownSize(
                            ServiceLoader.load(ConfigurationProvider.class).iterator(),
                            Spliterator.ORDERED), false)
                    .map(cp -> cp.getName()).collect(Collectors.joining(", "));
            throw new UnsupportedOperationException("Unable to find sender implementation for " + requiredReceiverName
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
        if (settings.getProperties().containsKey(COMMUNICATION)) {
            final ServiceLoader<Sender> senderList = ServiceLoader.load(Sender.class);
            final Iterator<Sender> iterator = senderList.iterator();
            final String requiredSenderName = settings.getProperties().getProperty(COMMUNICATION);
            while (iterator.hasNext()) {
                final Sender sender = iterator.next();
                if (sender.getName().equals(requiredSenderName)) {
                    LOG.info("Create Sender {}", sender.getName());
                    sender.init(settings.getProperties());
                    return sender;
                }
            }
            String available = StreamSupport
                    .stream(Spliterators.spliteratorUnknownSize(
                            ServiceLoader.load(ConfigurationProvider.class).iterator(),
                            Spliterator.ORDERED), false)
                    .map(cp -> cp.getName()).collect(Collectors.joining(", "));
            throw new UnsupportedOperationException(
                    "Unable to find sender implementation for " + requiredSenderName + ", available are: " + available);
        } else {
            throw new UnsupportedOperationException("Unable to find " + COMMUNICATION + " in settings properties");
        }
    }
}
