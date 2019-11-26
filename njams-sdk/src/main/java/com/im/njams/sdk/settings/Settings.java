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
package com.im.njams.sdk.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The settings contains settings needed for
 * {@link com.im.njams.sdk.Njams}
 *
 * @author bwand
 *
 */
public class Settings {

    //The Logger
    private static final Logger LOG = LoggerFactory.getLogger(Settings.class);

    private Properties properties;

    /**
     * Property njams.client.sdk.clientname
     */
    public static final String PROPERTY_CLIENT_NAME = "njams.client.sdk.clientname";
    /**
     * Property njams.client.sdk.clientversion
     */
    public static final String PROPERTY_CLIENT_VERSION = "njams.client.sdk.clientversion";
    /**
     * Property njams.client.sdk.flushsize
     */
    public static final String PROPERTY_FLUSH_SIZE = "njams.client.sdk.flushsize";
    /**
     * Property njams.client.sdk.flush_interval
     */
    public static final String PROPERTY_FLUSH_INTERVAL = "njams.client.sdk.flush_interval";
    /**
     * Property njams.client.sdk.minqueuelength
     */
    public static final String PROPERTY_MIN_QUEUE_LENGTH = "njams.client.sdk.minqueuelength";
    /**
     * Property njams.client.sdk.maxqueuelength
     */
    public static final String PROPERTY_MAX_QUEUE_LENGTH = "njams.client.sdk.maxqueuelength";
    /**
     * Property njams.client.sdk.senderidletime
     */
    public static final String PROPERTY_SENDER_THREAD_IDLE_TIME = "njams.client.sdk.senderthreadidletime";
    /**
     * Property njams.client.sdk.discardpolicy
     */
    public static final String PROPERTY_DISCARD_POLICY = "njams.client.sdk.discardpolicy";
    /**
     * Property njams.client.sdk.instantpush
     */
    public static final String PROPERTY_INSTANT_PUSH = "njams.client.sdk.instantpush";

    /**
     * @return client properties
     */
    public Properties getProperties() {
        if (properties == null) {
            properties = new Properties();
        }
        return properties;
    }

    /**
     * @param properties client properties
     */
    public void setProperties(final Properties properties) {
        this.properties = properties;
    }

    /**
     * This method prints all properties, but the values of all keys that contains
     * "password" or "credentials" are changed to "****".
     */
    public void printPropertiesWithoutPasswords() {
        printPropertiesWithoutPasswords(LOG);
    }

    /**
     * This method prints all properties to the given logger, but the values of all keys that contain
     * "password" or "credentials" are changed to "****".
     * @param logger The logger used for printing properties.
     */
    public void printPropertiesWithoutPasswords(Logger logger) {
        List<String> list = new ArrayList<>();
        properties.keySet().forEach(key -> list.add((String) key));
        Collections.sort(list);
        list.forEach((key) -> {
            String toCheck = key.toLowerCase();
            if (toCheck.contains("password") || toCheck.contains("credentials")) {
                logger.info("***      {} = {}", key, "****");
            }
            else {
                logger.info("***      {} = {}", key, properties.getProperty(key));
            }
        });
    }
}
