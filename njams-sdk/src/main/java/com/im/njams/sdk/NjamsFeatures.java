package com.im.njams.sdk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NjamsFeatures {

    /**
     * Defines the standard set of optional features that an nJAMS client may support.
     */
    public enum Feature {
        /**
         * Value indicating that this instance supports replay functionality.
         */
        REPLAY("replay"),
        /**
         * Value indicating that this instance supports the header injection feature.
         */
        INJECTION("injection"),
        /**
         * Value indicating that this instance implements expression test functionality.
         */
        EXPRESSION_TEST("expressionTest"),
        /**
         * Value indicating that this instance implements replying to a "ping" request sent from nJAMS server.
         */
        PING("ping");

        private final String key;

        private Feature(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return key;
        }

        /**
         * Raw string value to be used when sending information to nJAMS.
         *
         * @return Raw string value.
         */
        public String key() {
            return key;
        }

        /**
         * Tries to find the instance according to the given name.
         *
         * @param name The name of the instance that shall be returned.
         * @return The instance for the given name, or <code>null</code> if no matching instance was found.
         */
        public static Feature byName(String name) {
            for (Feature f : values()) {
                if (f.name().equalsIgnoreCase(name) || f.key.equalsIgnoreCase(name)) {
                    return f;
                }
            }
            return null;
        }
    }

    // features
    private final List<String> features = Collections
        .synchronizedList(
            new ArrayList<>(Arrays.asList(Feature.EXPRESSION_TEST.toString(), Feature.PING.toString())));

    /**
     * Adds a new feature to the feature list
     *
     * @param feature to set
     */
    public void add(String feature) {
        if (!features.contains(feature)) {
            features.add(feature);
        }
    }

    /**
     * Adds a new feature to the feature list
     *
     * @param feature to set
     */
    public void add(Feature feature) {
        add(feature.key());
    }

    /**
     * Remove a feature from the feature list
     *
     * @param feature to remove
     */
    public void remove(String feature) {
        features.remove(feature);
    }

    /**
     * Remove a feature from the feature list
     *
     * @param feature to remove
     */
    public void remove(Feature feature) {
        remove(feature.key());
    }

    /**
     * @return the list of features this client has
     */
    public List<String> get() {
        return Collections.unmodifiableList(features);
    }
}
