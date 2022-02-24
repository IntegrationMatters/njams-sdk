package com.im.njams.sdk.communication;

import java.util.Iterator;
import java.util.ServiceLoader;

class CommunicationServiceLoader<S> {

    private final ServiceLoader<S> serviceLoader;

    CommunicationServiceLoader(Class<S> serviceInterface) {
        serviceLoader = ServiceLoader.load(serviceInterface);
    }

    public Iterator<S> iterator() {
        serviceLoader.reload();

        return serviceLoader.iterator();
    }
}
