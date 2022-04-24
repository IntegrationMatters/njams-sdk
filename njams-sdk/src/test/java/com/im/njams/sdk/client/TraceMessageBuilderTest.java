/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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

import com.faizsiegeln.njams.messageformat.v4.projectmessage.Tracepoint;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.Activity;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.ProcessModel;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.configuration.TracepointExt;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/**
 * This class tests the TraceMessageBuilder
 */
public class TraceMessageBuilderTest extends AbstractTest {

    private TraceMessageBuilder builder = new TraceMessageBuilder(njamsFactory.getNjamsMetadata());

    private String FULLPROCESSPATHNAME;

    public TraceMessageBuilderTest(){
        FULLPROCESSPATHNAME = njamsFactory.getNjamsMetadata().getClientPath().add(PROCESSPATHNAME).toString();
    }

    @Test
    public void testBuildWithoutActivity() {
        TraceMessage msg = builder.build();
        assertNull(msg);
    }

    @Test
    public void testMultipleTimes(){
        this.addActivity(ACTIVITYMODELID);
        TraceMessage build1 = builder.build();
        assertNotNull(build1);
        TraceMessage build2 = builder.build();
        assertNull(build2);
    }

    @Test
    public void testBuild() {
        this.addActivity(ACTIVITYMODELID);
        TraceMessage msg = builder.build();
        checkTraceMessage(msg, LocalDateTime.MIN, LocalDateTime.MAX);
    }

    private void addActivity(String id){
        Activity act = new Activity();
        act.setActivityId(id);
        TracepointExt tp = new TracepointExt();
        tp.setDeeptrace(true);
        tp.setIterations(new Integer(30));
        tp.setEndtime(LocalDateTime.MAX);
        tp.setStarttime(LocalDateTime.MIN);
        act.setTracepoint(tp);
        builder.addActivity(FULLPROCESSPATHNAME, act);
    }

    private void checkTraceMessage(TraceMessage message, LocalDateTime ldt1, LocalDateTime ldt2) {
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

        Tracepoint messageTP= act.getTracepoint();
        assertEquals(messageTP.getStarttime(), ldt1);
        assertEquals(messageTP.getEndtime(), ldt2);
        assertEquals(new Integer(30), messageTP.getIterations());
        assertEquals(messageTP.isDeeptrace(), true);
    }
}