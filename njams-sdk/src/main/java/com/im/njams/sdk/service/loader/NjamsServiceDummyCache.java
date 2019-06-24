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
