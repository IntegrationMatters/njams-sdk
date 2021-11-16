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
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.settings.encoding.Transformer;
import com.im.njams.sdk.utils.StringUtils;

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
     * all properties registered here are masked and not printed during startup phase
     * always use lowercase when adding an entry to this set!
     */
    private Set<String> secureProperties = new HashSet<>();

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
    public static final String PROPERTY_MIN_SENDER_THREADS = "njams.client.sdk.minsenderthreads";
    /**
     * This property's default is 8 sender threads as maximum threads that can be used.
     * This means if there are more messages to handle than there are sender threads at the moment
     * and the threshold hasn't exceeded, a new thread will be started. If the thread isn't in use for
     * (look below njams.client.sdk.senderthreadidletime), the thread will be removed.
     */
    public static final String PROPERTY_MAX_SENDER_THREADS = "njams.client.sdk.maxsenderthreads";
    /**
     * This property's default is 8 messages that can be hold in the message Queue before the
     * messages will be discarded or client will stop processing until the queue has space again.
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
     * If set to <code>true</code> communications (senders and receivers) will be shared accross multiple {@link Njams}
     * instances if supported by the configured implementations. By default (or if set to <code>false</code>) each
     * {@link Njams} instance uses a dedicated instance of sender and receiver pools.
     */
    public static final String PROPERTY_SHARED_COMMUNICATIONS = "njams.client.sdk.sharedcommunications";
    /**
     * New field subProcessPath has been added for Messageformat 4.1.0
     * <p>
     * This Property can be set to use deprecated format; this might be used when sending to a server not compatible
     * because he uses an older Messageformat version.
     */
    public static final String PROPERTY_USE_DEPRECATED_PATH_FIELD_FOR_SUBPROCESSES =
            "njams.client.sdk.deprecatedsubprocesspathfield";

    /** Setting for enabling the logAllErrors feature. */
    public static final String PROPERTY_LOG_ALL_ERRORS = "njams.sdk.logAllErrors";
    /** Setting for truncate limit (nJAMS strip-mode). Number of activities/events before messages are truncated.  */
    public static final String PROPERTY_TRUNCATE_LIMIT = "njams.sdk.truncateActivitiesLimit";
    /** Setting for truncating successful jobs, provided that they were processed as single message.  */
    public static final String PROPERTY_TRUNCATE_ON_SUCCESS = "njams.sdk.truncateOnSuccess";

    /** Property added internally for passing the an instance's client path to the communication layer. */
    public static final String INTERNAL_PROPERTY_CLIENTPATH = "njams.$clientPath";

    public Settings() {
        properties = new Properties();
        secureProperties.add("password");
        secureProperties.add("credentials");
        secureProperties.add("secret");
    }

    /**
     * Return decoded property or null if not found
     *
     * @param key to look for
     * @return the found setting value
     */
    public String getProperty(String key) {
        return Transformer.decode(properties.getProperty(key));
    }

    /**
     * * Return decoded property or default value if not found
     *
     * @param key to look for
     * @param defaultValue which is returned if not found
     * @return the value.
     */
    public String getProperty(String key, String defaultValue) {
        return Transformer.decode(properties.getProperty(key, defaultValue));
    }

    /**
     * Check if key is found
     *
     * @param key to check
     * @return true if found else false
     */
    public boolean containsKey(String key) {
        return properties.containsKey(key);
    }

    /**
     * Put a key/value pair to settings.
     *
     * @param key the key
     * @param value the value
     */
    public void put(String key, String value) {
        properties.put(key, value);
    }

    /**
     * Reset the settings, which means everything will be deleted.
     */
    public void reset() {
        properties = new Properties();
    }

    /**
     * This method prints all Properties, but the values of all keys that contains
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
            if (isSecuredKey(key)) {
                logger.info("***      {} = ****", key);
            } else {
                logger.info("***      {} = {}", key, properties.getProperty(key));
            }
        });
    }

    /**
     * Returns <code>true</code> if the value for the given key is private and must be shown in the logs (e.g.,
     * passwords, etc).
     * @param key The properties key to test.
     * @return <code>true</code> if the value for the given key must not be shown.
     */
    public boolean isSecuredKey(final String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        final String lowerKey = key.toLowerCase();
        return secureProperties.stream().anyMatch(s -> lowerKey.contains(s));
    }

    /**
     * Return all Properties.They will be decoded because user cannot know which ones are encoded.
     *
     * @return the properties.
     */
    public Properties getAllProperties() {
        return Transformer.decode(properties);
    }

    /**
     * Return Properties, which contains only the properties starting with a given prefix.
     *
     * @param prefix prefix
     * @return new filtered Properties
     */
    public Properties filter(String prefix) {
        Properties response = new Properties();
        properties.entrySet()
        .stream()
        .filter(e -> String.class.isAssignableFrom(e.getKey().getClass()))
        .filter(e -> ((String) e.getKey()).startsWith(prefix))
        .forEach(e -> response.setProperty((String) e.getKey(), (String) e.getValue()));
        return Transformer.decode(properties);
    }

    /**
     * Return new Properties, which contains only the properties starting with a
     * given prefix, stripped from that prefix.
     *
     * @param prefix prefix
     * @return new filtered and stripped Properties
     */
    public Properties filterAndCut(String prefix) {
        Properties response = new Properties();
        properties.entrySet()
        .stream()
        .filter(e -> String.class.isAssignableFrom(e.getKey().getClass()))
        .filter(e -> ((String) e.getKey()).startsWith(prefix))
        .forEach(e -> response.setProperty(
                ((String) e.getKey()).substring(((String) e.getKey()).indexOf(prefix) + prefix.length()),
                (String) e.getValue()));
        return Transformer.decode(properties);
    }

    public void addSecureProperties(Set<String> secureProperties) {
        secureProperties.forEach(property -> {
            this.secureProperties.add(property.toLowerCase());
        });
    }
}
