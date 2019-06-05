package com.im.njams.sdk.service.factories;

import com.im.njams.sdk.service.NjamsService;
import com.im.njams.sdk.service.loader.NjamsServiceDummyCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceFactory.class);

    private String serviceName;

    private static NjamsServiceDummyCache serviceDummyCache;

    public ServiceFactory(String serviceName, Class<? extends NjamsService> classOfServiceToCreate) {
        this.serviceName = serviceName;
        if(serviceDummyCache == null) {
            this.serviceDummyCache = new NjamsServiceDummyCache(classOfServiceToCreate);
        }
    }

    public <T extends NjamsService> T getInstance(){
        NjamsService serviceDummy = serviceDummyCache.getServiceDummy(serviceName);
        Class<? extends NjamsService> classOfServiceDummy = serviceDummy.getClass();
        try {
            return (T) classOfServiceDummy.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            LOG.error("Can't create new instance of {}", classOfServiceDummy.getCanonicalName());
        }
        return null;
    }
}
