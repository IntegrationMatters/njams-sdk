package com.im.njams.sdk.communication;

import java.util.Iterator;
import java.util.ServiceLoader;

class CommunicationServiceLoader<S> {

    private final Class<S> serviceType;
    private final ServiceLoader<S> serviceLoader;

    CommunicationServiceLoader(Class<S> serviceInterface) {
        serviceType = serviceInterface;
        serviceLoader = ServiceLoader.load(serviceInterface);
    }

    public Iterator<S> iterator() {
        return serviceLoader.iterator();
    }

    public Class<S> getServiceType() {
        return serviceType;
    }

}
