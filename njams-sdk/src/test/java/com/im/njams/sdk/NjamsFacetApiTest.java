package com.im.njams.sdk;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.TestReceiver;

/**
 * Tests the new facet API of SDK-359: accessor identity, phase guards, behavioral parity
 * with the legacy API (mirror tests suffixed _viaFacet), and the lenient behavior of the
 * deprecated legacy methods.
 */
public class NjamsFacetApiTest {

    private Njams njams;

    @Before
    public void setUp() {
        njams = new Njams(Path.of("SDK4", "TEST"), "4.1.1", "sdk4", TestReceiver.getSettings());
    }

    @After
    public void tearDown() {
        if (njams.isStarted()) {
            njams.stop();
        }
    }

    @Test
    public void accessorsReturnTheSameInstanceEveryTime() {
        assertSame(njams.metadata(), njams.metadata());
        assertSame(njams.jobs(), njams.jobs());
        assertSame(njams.processes(), njams.processes());
        assertSame(njams.features(), njams.features());
        assertSame(njams.serializers(), njams.serializers());
        assertSame(njams.replay(), njams.replay());
        assertSame(njams.commands(), njams.commands());
        assertSame(njams.argos(), njams.argos());
        assertSame(njams.configuration(), njams.configuration());
    }

    // --- metadata guards ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newAddGlobalVariablesThrowsAfterStart() {
        njams.start();
        Map<String, String> vars = new HashMap<>();
        vars.put("late", "x");
        njams.metadata().addGlobalVariables(vars);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetGlobalVariablesPatternThrowsAfterStart() {
        njams.start();
        njams.metadata().setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void newSetRuntimeVersionThrowsAfterStart() {
        njams.start();
        njams.metadata().setRuntimeVersion("late");
    }

    @Test
    public void newMetadataMutatorsWorkBeforeStart() {
        njams.metadata().setRuntimeVersion("rt");
        Map<String, String> vars = new HashMap<>();
        vars.put("a", "1");
        njams.metadata().addGlobalVariables(vars);
        njams.metadata().setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)");
        assertEquals("rt", njams.metadata().getRuntimeVersion());
        assertEquals("1", njams.metadata().getGlobalVariables().get("a"));
    }

    @Test
    public void metadataMutatorsAreChainable() {
        Map<String, String> vars = new HashMap<>();
        vars.put("a", "1");
        NjamsMetadata result = njams.metadata()
            .setRuntimeVersion("rt")
            .addGlobalVariables(vars)
            .setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)");
        assertSame(njams.metadata(), result);
        assertEquals("rt", njams.metadata().getRuntimeVersion());
        assertEquals("1", njams.metadata().getGlobalVariables().get("a"));
    }

    @Test
    public void deprecatedMetadataMutatorsStayLenientAfterStart() {
        njams.start();
        njams.setRuntimeVersion("late"); // WARN, no throw
        Map<String, String> vars = new HashMap<>();
        vars.put("late", "x");
        njams.addGlobalVariables(vars); // WARN, no throw
        assertEquals("late", njams.getRuntimeVersion());
        assertEquals("x", njams.getGlobalVariables().get("late"));
    }

    // --- metadata parity mirrors ---

    @Test
    public void categoryIsUppercased_viaFacet() {
        assertEquals("SDK4", njams.metadata().getCategory());
    }

    @Test
    public void clientPathIsReturned_viaFacet() {
        assertEquals(Path.of("SDK4", "TEST"), njams.metadata().getClientPath());
    }

    @Test
    public void clientVersionComesFromConstructorWhenNoVersionFile_viaFacet() {
        assertEquals("4.1.1", njams.metadata().getClientVersion());
    }

    @Test
    public void sdkVersionIsNeverNull_viaFacet() {
        assertNotNull(njams.metadata().getSdkVersion());
    }

    @Test
    public void machineIsNeverNull_viaFacet() {
        assertNotNull(njams.metadata().getMachine());
    }

    @Test
    public void runtimeVersionIsSettable_viaFacet() {
        assertNull(njams.metadata().getRuntimeVersion());
        njams.metadata().setRuntimeVersion("rt-1");
        assertEquals("rt-1", njams.metadata().getRuntimeVersion());
    }

    @Test
    public void addGlobalVariablesMergesIntoExisting_viaFacet() {
        Map<String, String> first = new HashMap<>();
        first.put("a", "1");
        njams.metadata().addGlobalVariables(first);
        Map<String, String> second = new HashMap<>();
        second.put("b", "2");
        second.put("a", "overwritten");
        njams.metadata().addGlobalVariables(second);
        assertEquals("overwritten", njams.metadata().getGlobalVariables().get("a"));
        assertEquals("2", njams.metadata().getGlobalVariables().get("b"));
    }

    @Test
    public void clientSessionIdMatchesBothLegacyGetters_viaFacet() {
        // pins the intentional unification of getClientSessionId()/getCommunicationSessionId()
        assertNotNull(njams.metadata().getClientSessionId());
        assertEquals(njams.metadata().getClientSessionId(), njams.getClientSessionId());
        assertEquals(njams.metadata().getClientSessionId(), njams.getCommunicationSessionId());
    }

    @Test
    public void setGlobalVariablesPattern_acceptsValidPatternAndIsReturnedByGetter_viaFacet() {
        String pattern = "(?<full>%%(?<name>[^%]+)%%)";
        njams.metadata().setGlobalVariablesPattern(pattern);
        assertEquals(pattern, njams.metadata().getGlobalVariablesPattern());
    }

    @Test
    public void setGlobalVariablesPattern_acceptsPatternWithOptionalDefaultGroup_viaFacet() {
        String pattern = "(?<full>\\{\\{\\??(?<name>(?:(?:sys|env):)?[^}:]+)(?::(?<default>[^}]+))?\\}\\})";
        njams.metadata().setGlobalVariablesPattern(pattern);
        assertEquals(pattern, njams.metadata().getGlobalVariablesPattern());
    }

    @Test
    public void setGlobalVariablesPattern_nullClearsThePattern_viaFacet() {
        njams.metadata().setGlobalVariablesPattern("(?<full>%%(?<name>[^%]+)%%)");
        njams.metadata().setGlobalVariablesPattern(null);
        assertNull(njams.metadata().getGlobalVariablesPattern());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setGlobalVariablesPattern_rejectsInvalidRegex_viaFacet() {
        njams.metadata().setGlobalVariablesPattern("(?<full>(?<name>[^%]+");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setGlobalVariablesPattern_rejectsMissingNameGroup_viaFacet() {
        njams.metadata().setGlobalVariablesPattern("(?<full>%%[^%]+%%)");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setGlobalVariablesPattern_rejectsMissingFullGroup_viaFacet() {
        njams.metadata().setGlobalVariablesPattern("%%(?<name>[^%]+)%%");
    }
}
