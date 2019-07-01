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
package com.im.njams.sdk.service.factories;

import com.im.njams.sdk.service.NjamsService;
import com.im.njams.sdk.service.loader.NjamsServiceDummyCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Todo: Write Doc
 */
public class ServiceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceFactory.class);

    private String serviceName;

    private NjamsServiceDummyCache serviceDummyCache;

    public ServiceFactory(String serviceName, Class<? extends NjamsService> classOfServiceToCreate) {
        this.serviceName = serviceName;
        this.serviceDummyCache = new NjamsServiceDummyCache(classOfServiceToCreate);
    }

    public <T extends NjamsService> T getInstance() {
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
