/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.client;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.im.njams.sdk.NjamsFactory;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.njams.metadata.NjamsMetadataFactory;
import com.im.njams.sdk.settings.Settings;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Tracepoint;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.Activity;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.ProcessModel;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.configuration.TracepointExt;
import com.im.njams.sdk.utils.JsonUtils;

public class CleanTracepointsTaskTest extends AbstractTest {

    private static final NjamsFactory anotherNjamsFactory = new NjamsFactory(new Path("A"), "SDK", new Settings());
    private static final Njams anotherNjams = new Njams(anotherNjamsFactory);

    private static TraceMessage message = null;

    private final String FULLPROCESSPATHNAME;

    public CleanTracepointsTaskTest() {
        super();
        TestSender.setSenderMock(new SenderMock());
        njams.start();
        createDefaultActivity(createDefaultStartedJob());
        FULLPROCESSPATHNAME = njamsFactory.getNjamsMetadata().getClientPath().add(PROCESSPATHNAME).toString();
    }

    @BeforeClass
    public static void decreaseDelayAndInterval(){
        CleanTracepointsTask.setDelay(0);
        CleanTracepointsTask.setInterval(10);
    }

    @Before
    public void testStopAll() {
        CleanTracepointsTask.getCleanTracePointsTaskEntries().forEach(njams -> CleanTracepointsTask.stop(
                njams.getInstanceMetadata()));
    }

    @AfterClass
    public static void cleanUp(){
        CleanTracepointsTask.resetDelay();
        CleanTracepointsTask.resetInterval();
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void testStartWithNullNjams() {
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNull(CleanTracepointsTask.getTimer());
        CleanTracepointsTask.start(null, null, null);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void testStartWithNullClientPathNjams() {
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNull(CleanTracepointsTask.getTimer());

        NjamsMetadata metadata = NjamsMetadataFactory.createMetadataWith(null, null, "SDK");

        CleanTracepointsTask.start(metadata, null, null);
    }

    @Test
    public void testStartNormal() {
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNull(CleanTracepointsTask.getTimer());
        CleanTracepointsTask.start(njamsFactory.getNjamsMetadata(), njams.getConfiguration(), njamsFactory.getNjamsSender());
        assertFalse(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNotNull(CleanTracepointsTask.getTimer());
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().stream().map((entry) -> entry.getInstanceMetadata()).collect(
            Collectors.toList()).contains(njamsFactory.getNjamsMetadata()));
    }

    @Test
    public void testStartNormalWithMultipleNjams() {
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNull(CleanTracepointsTask.getTimer());
        CleanTracepointsTask.start(njamsFactory.getNjamsMetadata(), njams.getConfiguration(), njamsFactory.getNjamsSender());
        assertFalse(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().stream().map((entry) -> entry.getInstanceMetadata()).collect(
            Collectors.toList()).contains(njamsFactory.getNjamsMetadata()));
        assertNotNull(CleanTracepointsTask.getTimer());
        CleanTracepointsTask.start(anotherNjamsFactory.getNjamsMetadata(), anotherNjams.getConfiguration(), anotherNjamsFactory.getNjamsSender());
        assertFalse(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().stream().map((entry) -> entry.getInstanceMetadata()).collect(
            Collectors.toList()).contains(njamsFactory.getNjamsMetadata()));
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().stream().map((entry) -> entry.getInstanceMetadata()).collect(
            Collectors.toList()).contains(anotherNjamsFactory.getNjamsMetadata()));
        assertNotNull(CleanTracepointsTask.getTimer());
    }

    @Test
    public void testStartNormalSeveralTimesWithOneInstance() {
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNull(CleanTracepointsTask.getTimer());
        CleanTracepointsTask.start(njamsFactory.getNjamsMetadata(), njams.getConfiguration(), njamsFactory.getNjamsSender());
        assertFalse(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().stream().map((entry) -> entry.getInstanceMetadata()).collect(
            Collectors.toList()).contains(njamsFactory.getNjamsMetadata()));
        assertNotNull(CleanTracepointsTask.getTimer());
        CleanTracepointsTask.start(njamsFactory.getNjamsMetadata(), njams.getConfiguration(), njamsFactory.getNjamsSender());
        assertFalse(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().stream().map((entry) -> entry.getInstanceMetadata()).collect(
            Collectors.toList()).contains(njamsFactory.getNjamsMetadata()));
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().size() == 1);
        assertNotNull(CleanTracepointsTask.getTimer());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void testStopWithNullNjams() {
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNull(CleanTracepointsTask.getTimer());
        CleanTracepointsTask.stop(null);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void testStopWithNullClientPath() {
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNull(CleanTracepointsTask.getTimer());

        NjamsMetadata metadata = NjamsMetadataFactory.createMetadataWith(null, null, null);
        when(anotherNjamsFactory.getNjamsMetadata()).thenReturn(metadata);
        CleanTracepointsTask.stop(anotherNjamsFactory.getNjamsMetadata());
    }

    @Test
    public void testStopNormal() {
        testStartNormal();
        CleanTracepointsTask.stop(njamsFactory.getNjamsMetadata());
        assertNull(CleanTracepointsTask.getTimer());
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
    }

    @Test
    public void testStopNormalWithoutStarting() {
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNull(CleanTracepointsTask.getTimer());
        CleanTracepointsTask.stop(njamsFactory.getNjamsMetadata());
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNull(CleanTracepointsTask.getTimer());
    }

    @Test
    public void testStopNormalWithMultipleNjams() {
        testStartNormalWithMultipleNjams();
        CleanTracepointsTask.stop(njamsFactory.getNjamsMetadata());
        assertFalse(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNotNull(CleanTracepointsTask.getTimer());
        CleanTracepointsTask.stop(anotherNjamsFactory.getNjamsMetadata());
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNull(CleanTracepointsTask.getTimer());

    }

    @Test
    public void testStopNormalSeveralTimesWithOneInstance() {
        testStartNormalSeveralTimesWithOneInstance();
        CleanTracepointsTask.stop(njamsFactory.getNjamsMetadata());
        assertTrue(CleanTracepointsTask.getCleanTracePointsTaskEntries().isEmpty());
        assertNull(CleanTracepointsTask.getTimer());
    }

    @Test
    public void testStartStopStart() {
        testStopNormal();
        testStartNormal();
    }

    @Test
    public void testRun() throws InterruptedException {
        LocalDateTime ldt1 = DateTimeUtility.now();
        Thread.sleep(1);
        LocalDateTime ldt2 = DateTimeUtility.now();

        fillActivityConfiguration(ldt1, ldt2);

        assertNotNull(njams.getConfiguration().getProcess(FULLPROCESSPATHNAME).getActivity(ACTIVITYMODELID)
                .getTracepoint());
        assertNull(message);
        testStartNormal();

        Thread.sleep(CleanTracepointsTask.getDelay() + CleanTracepointsTask.getInterval());
        assertNotNull(message);
        assertNull(njams.getConfiguration().getProcess(FULLPROCESSPATHNAME).getActivity(ACTIVITYMODELID)
                .getTracepoint());

        checkTraceMessage(ldt1, ldt2);
        printMessageAsJson();
    }

    private void fillActivityConfiguration(LocalDateTime ldt1, LocalDateTime ldt2) {
        ActivityConfiguration ac = new ActivityConfiguration();
        Extract ex = new Extract();
        ex.setName("ExtractTest");
        ac.setExtract(ex);
        TracepointExt tp = new TracepointExt();
        tp.setDeeptrace(true);
        tp.setStarttime(ldt1);
        tp.setEndtime(ldt2);
        tp.setCurrentIterations(30);
        tp.setIterations(30);
        ac.setTracepoint(tp);

        Map<String, ActivityConfiguration> acs = new HashMap<>();
        acs.put(ACTIVITYMODELID, ac);
        fillProcessConfiguration(acs);
    }

    private void fillProcessConfiguration(Map<String, ActivityConfiguration> acs) {
        ProcessConfiguration pc = new ProcessConfiguration();
        pc.setActivities(acs);
        pc.setExclude(true);
        pc.setLogLevel(LogLevel.ERROR);
        pc.setRecording(false);

        Map<String, ProcessConfiguration> pcs = new HashMap<>();
        pcs.put(FULLPROCESSPATHNAME, pc);
        njams.getConfiguration().setProcesses(pcs);
    }

    private void checkTraceMessage(LocalDateTime ldt1, LocalDateTime ldt2) {
        assertEquals(message.getClientVersion(), CLIENTVERSION);
        assertEquals(message.getSdkVersion(), njamsFactory.getNjamsMetadata().getSdkVersion());
        assertEquals(message.getCategory(), CATEGORY);
        assertEquals(message.getPath(), njamsFactory.getNjamsMetadata().getClientPath().toString());

        List<ProcessModel> processes = message.getProcesses();
        assertNotNull(processes);
        assertFalse(processes.isEmpty());

        ProcessModel processModel = processes.get(0);
        assertEquals(processModel.getProcessPath(), FULLPROCESSPATHNAME);

        List<Activity> activities = processModel.getActivities();
        assertNotNull(activities);
        assertFalse(activities.isEmpty());

        Activity act = activities.get(0);
        assertEquals(act.getActivityId(), ACTIVITYMODELID);

        Tracepoint messageTP = act.getTracepoint();
        assertEquals(messageTP.getStarttime(), ldt1);
        assertEquals(messageTP.getEndtime(), ldt2);
        assertEquals(messageTP.getIterations(), new Integer(30));
        assertEquals(messageTP.isDeeptrace(), true);
    }

    private void printMessageAsJson() {
        try {
            String json = JsonUtils.serialize(message);
            System.out.println(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This class is for fetching the messages that would be sent out.
     */
    private static class SenderMock implements Sender {

        /**
         * This method does nothing
         *
         * @param properties nothing to do with these
         */
        @Override
        public void init(Properties properties) {
            //Do nothing
        }

        /**
         * This method safes all sent messages in the messages queue.
         *
         * @param msg
         */
        @Override
        public void send(CommonMessage msg) {
            if (msg instanceof TraceMessage) {
                message = (TraceMessage) msg;
            }
        }

        /**
         * This method does nothing
         */
        @Override
        public void close() {
            //Do nothing
        }

        /**
         * This method returns the name of the TestSender.
         *
         * @return name of the TestSender.
         */
        @Override
        public String getName() {
            return TestSender.NAME;
        }

    }
}