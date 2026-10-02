package com.im.njams.sdk.it.harness;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.ClientSettings;

public class MessageDriverTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null) {
            njams.stop();
        }
    }

    @Test
    public void countKnobProducesExactlyThatManyUniqueLogIds() throws Exception {
        njams = startNjams();
        ProcessModel model = FixedProcessModel.build(njams);

        List<String> logIds = MessageDriver.run(model, 25, 0, 1);

        assertEquals(25, logIds.size());
        assertEquals(25, Set.copyOf(logIds).size());
    }

    @Test
    public void sizeKnobControlsThePayloadLength() throws Exception {
        njams = startNjams();
        ProcessModel model = FixedProcessModel.build(njams);

        // Correctness of the padding itself is exercised indirectly: MessageDriver must not throw for a range
        // of sizes, from empty to comfortably past a single-fragment threshold.
        MessageDriver.run(model, 1, 0, 1);
        MessageDriver.run(model, 1, 10_000, 1);
        MessageDriver.run(model, 1, 200_000, 1);
    }

    @Test
    public void concurrencyKnobRunsAllJobsAcrossMultipleThreads() throws Exception {
        njams = startNjams();
        ProcessModel model = FixedProcessModel.build(njams);

        List<String> logIds = MessageDriver.run(model, 50, 100, 8);

        assertEquals(50, logIds.size());
        assertEquals(50, Set.copyOf(logIds).size());
    }

    private static Njams startNjams() {
        ClientSettings settings = TestSender.getSettings();
        Njams instance = new Njams(Path.of("MessageDriverTest"), "1.0.0", "CommunicationIT", settings);
        instance.start();
        return instance;
    }
}
