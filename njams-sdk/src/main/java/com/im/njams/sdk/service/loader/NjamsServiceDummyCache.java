package com.im.njams.sdk.service.loader;

import com.im.njams.sdk.service.NjamsService;

import java.util.HashMap;
import java.util.Map;

public class NjamsServiceDummyCache {

    private Map<String, NjamsService> alreadyFoundServiceDummies;

    private NjamsServiceLoader serviceLoaderToGetDummies;

    public NjamsServiceDummyCache(Class<? extends NjamsService> serviceToCache) {
        this.alreadyFoundServiceDummies = new HashMap<>();
        this.serviceLoaderToGetDummies = new NjamsServiceLoader(serviceToCache);
    }

    public NjamsService getServiceDummy(String serviceName) {
        NjamsService serviceInCache = searchForDummyInCache(serviceName);
        if (serviceInCache == null) {
            serviceInCache = createAndAddServiceDummyToCache(serviceName);
        }
        return serviceInCache;
    }

    private NjamsService searchForDummyInCache(String serviceName) {
        return alreadyFoundServiceDummies.get(serviceName);
    }

    private NjamsService createAndAddServiceDummyToCache(String serviceName) {
        NjamsService newlyCreatedServiceDummy = serviceLoaderToGetDummies.getServiceDummy(serviceName);
        alreadyFoundServiceDummies.put(serviceName, newlyCreatedServiceDummy);

        return newlyCreatedServiceDummy;
    }

}
