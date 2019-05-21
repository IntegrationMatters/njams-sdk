package com.im.njams.sdk.communication.factories;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.connectable.Connectable;
import com.im.njams.sdk.communication.connectable.receiver.Receiver;
import com.im.njams.sdk.communication.connectable.sender.Sender;
import com.im.njams.sdk.communication.connector.Connector;
import com.im.njams.sdk.communication.validator.ClasspathValidatable;
import com.im.njams.sdk.communication.validator.ClasspathValidator;
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

public class ConnectableFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectableFactory.class);

    public static final String COMMUNICATION = "njams.sdk.communication";

    private ServiceLoader<Receiver> receiverList;
    private ServiceLoader<Sender> senderList;

    private final Properties properties;
    private final Njams njams;

    private static final Set<Class<?>> alreadyChecked = new HashSet<>();
    private static final ClasspathValidator validator = new ClasspathValidator();

    /**
     * This class loads a Factory for either Receiver or Sender connectables.
     * @param njams
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
