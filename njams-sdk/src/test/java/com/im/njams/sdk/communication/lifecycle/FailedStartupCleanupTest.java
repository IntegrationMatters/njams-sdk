package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.argos.ArgosComponent;
import com.im.njams.sdk.argos.ArgosMetric;
import com.im.njams.sdk.argos.ArgosMultiCollector;
import com.im.njams.sdk.argos.ArgosSender;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.configuration.ConfigurationInstructionListener;

/**
 * SDK-442: a failed {@link Njams#start()} must leave nothing behind that the client would need to clean up, or that
 * a retried start would duplicate.
 */
public class FailedStartupCleanupTest extends AbstractLifecycleSpecTest {

    private Njams njams;
    private ArgosMultiCollector<?> collector;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
        if (collector != null) {
            ArgosSender.getInstance().removeArgosCollector(collector);
        }
    }

    private Njams newNjams() {
        return new Njams(Path.of("test", "failedstartup"), "1.0", "test", LifecycleTestTransport.settings());
    }

    @Test
    public void retriedStartAfterFailureHasNoDuplicateListeners() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams();
        assertFalse(njams.start());
        assertTrue("a failed start() must not leave instruction listeners registered",
            njams.commands().list().isEmpty());

        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        assertTrue("a retried start() must succeed once the connection is available", njams.start());
        List<InstructionListener> listeners = njams.commands().list();
        assertEquals(1, listeners.stream().filter(l -> !(l instanceof ConfigurationInstructionListener)).count());
        assertEquals(1, listeners.stream().filter(l -> l instanceof ConfigurationInstructionListener).count());
    }

    @Test
    public void failedStartRemovesArgosCollectors() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams();
        collector = new ArgosMultiCollector<ArgosMetric>(
            new ArgosComponent("id", "name", "container", "measurement", "type")) {
            @Override
            protected Collection<ArgosMetric> createAll() {
                return Collections.emptyList();
            }
        };
        njams.argos().add(collector);

        assertFalse(njams.start());
        assertFalse("a failed start() must deregister the instance's Argos collectors",
            ArgosSender.getInstance().removeArgosCollector(collector));
    }
}
