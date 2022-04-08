package com.im.njams.sdk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NjamsFeatures {


    // features
    private final List<String> features = Collections
        .synchronizedList(
            new ArrayList<>(Arrays.asList(Njams.Feature.EXPRESSION_TEST.toString(), Njams.Feature.PING.toString())));

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
    public void add(Njams.Feature feature) {
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
    public void remove(Njams.Feature feature) {
        remove(feature.key());
    }

    /**
     * @return the list of features this client has
     */
    public List<String> get() {
        return Collections.unmodifiableList(features);
    }
}
