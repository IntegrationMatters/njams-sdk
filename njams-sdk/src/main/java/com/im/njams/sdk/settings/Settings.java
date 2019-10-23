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
     * This property is a flush criterium with a default of 5mb.
     * If the flush size of the logmessage exceedes this threshold, the message will be flushed
     */
    public static final String PROPERTY_FLUSH_SIZE = "njams.client.sdk.flushsize";
    /**
     * This property is a flush criterium with a default of 30s.
     * If no logmessage has been sent in the last 30 seconds, the logmessage will be flushed now
     */
    public static final String PROPERTY_FLUSH_INTERVAL = "njams.client.sdk.flush_interval";
    /**
     * This property's default is 1 sender thread as core thread
     * (that means it can't be closed even if its idle time has been exceeded)
     * that can send project and log messages to the server.
     */
    public static final String PROPERTY_MIN_QUEUE_LENGTH = "njams.client.sdk.minqueuelength";
    /**
     * This property's default is 8 sender threads as maximum threads that can be used.
     * This means if there are more messages to handle than there are sender threads at the moment
     * and the threshold hasn't exceeded, a new thread will be started. If the thread isn't in use for
     * (look below njams.client.sdk.senderthreadidletime), the thread will be removed.
     */
    public static final String PROPERTY_MAX_QUEUE_LENGTH = "njams.client.sdk.maxqueuelength";
    /**
     * This property's default is 10000 (ms) that means that idle sender threads that haven't send any
     * message in the last 10 seconds and are not core threads will be removed.
     */
    public static final String PROPERTY_SENDER_THREAD_IDLE_TIME = "njams.client.sdk.senderthreadidletime";
    /**
     * This property decides what to do with a logmessage that couldn't be delivered (because of connection loss, full queue, etc.)
     * Possible values are: none|onconnectionloss|discard (Default is none)
     */
    public static final String PROPERTY_DISCARD_POLICY = "njams.client.sdk.discardpolicy";

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
     * This method prints all Properties, but the values of all keys that contains
     * "password" or "credentials" are changed to "****".
     */
    public void printPropertiesWithoutPasswords(){   
        List<String> list = new ArrayList<>();
        properties.keySet().forEach(key -> list.add((String)key));
        Collections.sort(list); 
        list.forEach((key) -> {
            String toCheck = ((String)key).toLowerCase();
            if (toCheck.contains("password") || toCheck.contains("credentials")) {
                LOG.info("***      {} = {}", key, "****");
            }
            else{
                LOG.info("***      {} = {}", key, properties.getProperty((String) key));
            }
        });
    }
}
