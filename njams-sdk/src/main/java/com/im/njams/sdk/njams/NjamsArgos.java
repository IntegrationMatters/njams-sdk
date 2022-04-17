package com.im.njams.sdk.njams;

import com.im.njams.sdk.argos.ArgosMetric;
import com.im.njams.sdk.argos.ArgosMultiCollector;
import com.im.njams.sdk.argos.ArgosSender;
import com.im.njams.sdk.settings.Settings;

import java.util.ArrayList;
import java.util.Collection;

public class NjamsArgos {
    private final ArgosSender argosSender;
    private final Collection<ArgosMultiCollector<?>> argosCollectors;

    public NjamsArgos(Settings settings) {
        argosSender = ArgosSender.getInstance();
        argosSender.init(settings);
        argosCollectors = new ArrayList<>();
    }

    /**
     * Adds a collector that will create statistics.
     *
     * @param collector The collector that collects statistics
     */
    public <T extends ArgosMetric> void addCollector(ArgosMultiCollector<T> collector) {
        argosCollectors.add(collector);
        argosSender.addArgosCollector(collector);
    }

    public <T extends ArgosMetric> void remove(ArgosMultiCollector<T> collector) {
        argosCollectors.remove(collector);
        argosSender.removeArgosCollector(collector);
    }

    public void stop() {
        argosCollectors.forEach(argosSender::removeArgosCollector);
        argosCollectors.clear();
    }
}
