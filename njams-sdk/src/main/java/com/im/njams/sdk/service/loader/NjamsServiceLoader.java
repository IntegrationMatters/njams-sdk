package com.im.njams.sdk.service.loader;

import com.im.njams.sdk.service.NjamsService;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class NjamsServiceLoader {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsServiceLoader.class);

    private ServiceLoader<? extends NjamsService> availableServiceDummies;

    public NjamsServiceLoader(Class<? extends NjamsService> serviceToLoad) {
        this.availableServiceDummies = ServiceLoader.load(serviceToLoad);
    }

    public NjamsService getServiceDummy(String serviceName) {
        if (StringUtils.isNotBlank(serviceName)) {
            NjamsService serviceDummy = searchForMatchingService(serviceName);
            if (serviceDummy == null) {
                String allServicesAsString = concatAllAvailableServices();
                throw new UnsupportedOperationException("Unable to find NjamsService with name " + serviceName + ", available are: " + allServicesAsString);
            }
            return serviceDummy;
        } else {
            throw new UnsupportedOperationException("The serviceName shouldn't be empty or null.");
        }
    }

    private NjamsService searchForMatchingService(String serviceName) {
        NjamsService matchingService = null;
        Iterator<? extends NjamsService> iterator = availableServiceDummies.iterator();
        while (iterator.hasNext() && matchingService == null) {
            NjamsService possibleService = iterator.next();
            if (possibleService.getName().equals(serviceName)) {
                String concreteClassOfPossibleService = possibleService.getClass().getSimpleName();
                LOG.debug("Found NjamsService implementation {}", concreteClassOfPossibleService);
                matchingService = possibleService;
            }
        }
        return matchingService;
    }

    private String concatAllAvailableServices() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(availableServiceDummies.iterator(),
                Spliterator.ORDERED), false)
                .map(cp -> cp.getName()).collect(Collectors.joining(", "));
    }
}
