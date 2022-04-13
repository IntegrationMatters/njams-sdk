package com.im.njams.sdk.argos;

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
    public void addCollector(ArgosMultiCollector collector) {
        argosCollectors.add(collector);
        argosSender.addArgosCollector(collector);
    }

    public void remove(ArgosMultiCollector collector) {
        argosCollectors.remove(collector);
        argosSender.removeArgosCollector(collector);
    }

    public void stop() {
        argosCollectors.forEach(argosSender::removeArgosCollector);
        argosCollectors.clear();
    }
}
