/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication_to_merge.factories;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication_to_merge.connectable.Connectable;
import com.im.njams.sdk.communication_to_merge.connectable.receiver.Receiver;
import com.im.njams.sdk.communication_to_merge.connectable.sender.Sender;
import com.im.njams.sdk.communication_to_merge.connector.Connector;
import com.im.njams.sdk.communication_to_merge.validator.ClasspathValidatable;
import com.im.njams.sdk.communication_to_merge.validator.ClasspathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Todo: Write Doc
 */
public class ConnectableFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectableFactory.class);

    /**
     * Todo: Write Doc
     */
    public static final String COMMUNICATION = "njams.sdk.communication";

    private ServiceLoader<Receiver> receiverList;
    private ServiceLoader<Sender> senderList;

    private final Properties properties;
    private final Njams njams;

    private static final Set<Class<?>> alreadyChecked = new HashSet<>();
    private static final ClasspathValidator validator = new ClasspathValidator();

    /**
     * This class loads a Factory for either Receiver or Sender connectables.
     * @param njams Njams used for sending instructions to
     * @param properties Properties to initialize
     */
    public ConnectableFactory(Njams njams, Properties properties){
        this.njams = njams;
        this.properties = properties;
        this.receiverList = ServiceLoader.load(Receiver.class);
        this.senderList = ServiceLoader.load(Sender.class);
    }

    /**
     * Returns the Receiver specified by the properties
     *
     * @return new initialized Receiver
     */
    public Receiver getReceiver() {
        Receiver receiver = (Receiver) getConnectable(receiverList, Receiver.class);
        return receiver;
    }

    /**
     * Returns the Sender specified by the properties
     *
     * @return new initialized Sender
     */
    public Sender getSender() {
        return (Sender) getConnectable(senderList, Sender.class);
    }

    /**
     * Returns the connectable specified by the properties
     *
     * @return new initialized Connector
     */
    private <T extends Connectable> Connectable getConnectable(ServiceLoader<T> list, Class<T> clazz) {
        if (properties.containsKey(COMMUNICATION)) {
            final Iterator<T> iterator = list.iterator();
            final String requiredConnectableName = properties.getProperty(COMMUNICATION);
            while (iterator.hasNext()) {
                final T t = iterator.next();
                if (t.getName().equals(requiredConnectableName)) {
                    try {
                        // create a new instance
                        Connectable connectable = t.getClass().newInstance();
                        LOG.info("Created new {}", connectable.getClass().getSimpleName());
                        if(connectable instanceof Receiver){
                            ((Receiver) connectable).setNjams(njams);
                        }
                        connectable.init(properties);
                        //Validating here doesn't make any sense, because it has been initialized before anyawys.
                        Connector connector = connectable.getConnector();
                        Class<?> connectorClazzToCheck = connector.getClass();
                        if(!alreadyChecked.contains(connectorClazzToCheck) && connector instanceof ClasspathValidatable){
                            validator.validate(connector);
                            alreadyChecked.add(connectorClazzToCheck);
                        }
                        return connectable;
                    } catch (Exception e) {
                        throw new UnsupportedOperationException(
                                "Unable to create new " + requiredConnectableName + " instance", e);
                    }
                }
            }
            String available = StreamSupport
                    .stream(Spliterators.spliteratorUnknownSize(
                            ServiceLoader.load(clazz).iterator(),
                            Spliterator.ORDERED), false)
                    .map(Connectable::getName).collect(Collectors.joining(", "));
            throw new UnsupportedOperationException(
                    "Unable to find Sender/Receiver implementation for " + requiredConnectableName + ", available are: " + available);
        } else {
            throw new UnsupportedOperationException("Unable to find " + COMMUNICATION + " in settings properties");
        }
    }
}
