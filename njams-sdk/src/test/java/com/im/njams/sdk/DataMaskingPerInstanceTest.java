package com.im.njams.sdk;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.settings.ClientSettings;

@SuppressWarnings("removal")
public class DataMaskingPerInstanceTest {

    private final List<Njams> created = new ArrayList<>();

    @Before
    public void clearJvmWidePatterns() {
        DataMasking.removePatterns();
    }

    @After
    public void stopAll() {
        created.forEach(n -> {
            if (n.isStarted()) {
                n.stop();
            }
        });
        DataMasking.removePatterns();
    }

    @Test
    public void patternsOfOneInstanceDoNotAffectAnother() {
        Njams a = start("A", settingsWithRegex("secretA"));
        Njams b = start("B", settingsWithRegex("secretB"));

        assertEquals("******* secretB", recordAttribute(a, "secretA secretB"));
        assertEquals("secretA *******", recordAttribute(b, "secretA secretB"));
    }

    @Test
    public void restartReplacesConfiguredPatterns() {
        Njams njams = newNjams("R", TestSender.getSettings());
        // start() replaces configuration().get() with what the provider loads; the memory provider caches the
        // Configuration it creates on first load, so edit that object
        njams.configuration().get().getConfigurationProvider().loadConfiguration()
            .setDataMasking(Collections.singletonList("old"));
        njams.start();
        assertEquals("*** new", njams.configuration().dataMasking().maskString("old new"));

        njams.stop();
        njams.configuration().get().setDataMasking(Collections.singletonList("new"));
        njams.start();

        assertEquals("old ***", njams.configuration().dataMasking().maskString("old new"));
    }

    @Test
    public void clientPatternAddedBeforeStartSurvivesStart() {
        Njams njams = newNjams("C", settingsWithRegex("configured"));
        njams.configuration().dataMasking().addPattern("client");
        njams.start();

        assertEquals("****** **********", recordAttribute(njams, "client configured"));
    }

    @Test
    public void disabledMaskingOnOneInstanceOnly() {
        ClientSettings disabled = settingsWithRegex("secret");
        disabled.put(NjamsSettings.PROPERTY_DATA_MASKING_ENABLED, "false");
        Njams off = start("Off", disabled);
        Njams on = start("On", settingsWithRegex("secret"));

        assertEquals("secret", recordAttribute(off, "secret"));
        assertEquals("******", recordAttribute(on, "secret"));
    }

    @Test
    public void jvmWidePatternsStillApplyToEveryInstance() {
        DataMasking.addPattern("legacy");
        Njams njams = start("L", TestSender.getSettings());

        assertEquals("******", recordAttribute(njams, "legacy"));
    }

    private Njams start(String name, ClientSettings settings) {
        Njams njams = newNjams(name, settings);
        njams.start();
        return njams;
    }

    private Njams newNjams(String name, ClientSettings settings) {
        Njams njams = new Njams(Path.of("SDK490", name), "1.0.0", "SDK", settings);
        njams.model().create("PROCESS");
        created.add(njams);
        return njams;
    }

    private static String recordAttribute(Njams njams, String value) {
        Job job = njams.model().get("PROCESS").createJob();
        job.attributes().add("key", value);
        return job.attributes().get("key");
    }

    private static ClientSettings settingsWithRegex(String regex) {
        ClientSettings settings = TestSender.getSettings();
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX + "r", regex);
        return settings;
    }
}
