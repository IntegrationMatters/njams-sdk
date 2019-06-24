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
package com.im.njams.sdk.logmessage;

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.common.DateTimeUtility;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * This class tests some methods of the ActivityImpl class.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.6
 */
public class ActivityImplTest extends AbstractTest {
    
    /**
     * This constructor calls super().
     */
    public ActivityImplTest(){
        super();
    }

    /**
     * This method tests the setEventStatus(..) method.
     */
    @Test
    public void testSetEventStatus() {
        JobImpl job = super.createDefaultJob();
        //Initial there is no EventStatus and the JobStatus is CREATED.
        assertEquals(JobStatus.CREATED, job.getStatus());
        job.start();
        ActivityImpl act = (ActivityImpl)createDefaultActivity(job);

        //Initial there is no EventStatus and the JobStatus is CREATED.
        assertEquals(null, act.getEventStatus());
        assertEquals(JobStatus.RUNNING, job.getStatus());

        //Here an invalid Status is tested. After that it should stay the same
        //as before
        act.setEventStatus(Integer.MIN_VALUE);
        assertEquals(null, act.getEventStatus());
        assertEquals(JobStatus.RUNNING, job.getStatus());

        //Here the EventStatus is set to INFO and the JobStatus hasn't changed,
        //because there is no corresponding JobStatus.
        act.setEventStatus(0);
        assertTrue(0 == act.getEventStatus());
        assertEquals(JobStatus.RUNNING, job.getStatus());

        //Here the EventStatus is set to SUCCESS and the JobStatus likewise.
        act.setEventStatus(1);
        assertTrue(1 == act.getEventStatus());
        assertEquals(JobStatus.SUCCESS, job.getStatus());

        //Here the EventStatus is set to null, but the JobStatus is not affected.
        //The JobStatus is still SUCCESS.
        act.setEventStatus(null);
        assertEquals(null, act.getEventStatus());
        assertEquals(JobStatus.SUCCESS, job.getStatus());

        //Here the EventStatus is set to INFO, but the JobStatus is not affected.
        //The JobStatus is still SUCCESS.
        act.setEventStatus(0);
        assertTrue(0 == act.getEventStatus());
        assertEquals(JobStatus.SUCCESS, job.getStatus());

        //Here an invalid Status is tested again. After that the EventStatus
        //is still INFO and the JobStatus is still SUCCESS.
        act.setEventStatus(Integer.MIN_VALUE);
        assertTrue(0 == act.getEventStatus());
        assertEquals(JobStatus.SUCCESS, job.getStatus());

        //Here the Event Status is set to WARNING and the JobStatus likewise.
        act.setEventStatus(2);
        assertTrue(2 == act.getEventStatus());
        assertEquals(JobStatus.WARNING, job.getStatus());

        //Here the Event Status is set to ERROR and the JobStatus likewise.
        act.setEventStatus(3);
        assertTrue(3 == act.getEventStatus());
        assertEquals(JobStatus.ERROR, job.getStatus());

        //Here the Event Status is set back to WARNING and the JobStatus likewise.
        act.setEventStatus(2);
        assertTrue(2 == act.getEventStatus());
        assertEquals(JobStatus.WARNING, job.getStatus());
    }
    
    /**
     * This method tests if the Attributes can first be get and after that be set
     * in the map. This would bypass the datamasking! [SDK-94]
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testInjectUnmaskedAttributesWithGetAttributes(){
        JobImpl job = super.createDefaultJob();
        job.start();
        ActivityImpl act = (ActivityImpl)createDefaultActivity(job);
        act.addAttribute("a", "b");
        assertTrue(!act.getAttributes().isEmpty());
        Map<String, String> attributes = act.getAttributes();
        attributes.put("b", "c"); //This should throw an UnsupportedOperationException
    }
    
    /**
     * This method tests if the unmodifiable map is consistent to the actual map.
     */
    @Test
    public void testIsUnmodifiableMapConsistent(){
        JobImpl job = super.createDefaultJob();
        job.start();
        ActivityImpl act = (ActivityImpl)createDefaultActivity(job);
        act.addAttribute("a", "b");
        assertTrue(!act.getAttributes().isEmpty());
        Map<String, String> attributes = act.getAttributes();
        act.addAttribute("b", "c");
        assertTrue(attributes.containsKey("b"));
    }
    
    /**
     * This method tests if the ActivityImpl copies the Attribute that was set there
     * to the Attributes list of the job.
     */
    @Test
    public void testOverrideJobAttributesWithActivityAttributes() {
        JobImpl job = createDefaultJob();
        job.start();
        ActivityImpl act1 = (ActivityImpl) createDefaultActivity(job);
        ActivityImpl act2 = (ActivityImpl) createDefaultActivity(job);
        String testAttrKey = "TestAttrKey";
        assertNull(job.getAttribute(testAttrKey));
        act1.addAttribute(testAttrKey, "a");
        assertEquals("a", job.getAttribute(testAttrKey));
        act2.addAttribute(testAttrKey, "b");
        assertEquals("b", job.getAttribute(testAttrKey));
        assertNotEquals("a", job.getAttribute(testAttrKey));
        //It has nothing to do with the sequence number, the attribute will be overriden
        //by the last call of addAttribute
        act1.addAttribute(testAttrKey, "c");
        assertEquals("c", job.getAttribute(testAttrKey));
        assertNotEquals("b", job.getAttribute(testAttrKey));
    }

    @Test
    public void testIsExecutionOnlySetIfAnEventIsSet(){
        Activity act = getDefaultActivity();
        assertNull(act.getStackTrace());
        assertNull(act.getEventMessage());
        assertNull(act.getEventCode());
        assertNull(act.getEventPayload());
        assertNull(act.getEventStatus());
        assertNull(act.getExecution());
    }

    @Test
    public void testIsExecutionSetWithPayload(){
        final String testPayload = "TestPayload";
        Activity act = getDefaultActivity();

        assertNull(act.getExecution());
        act.setEventPayload(testPayload);
        assertEquals(act.getEventPayload(), testPayload);
        assertNotNull(act.getExecution());
    }

    @Test
    public void testIsExecutionSetWithCode(){
        final String testCode = "TestCode";
        Activity act = getDefaultActivity();

        assertNull(act.getExecution());
        act.setEventCode(testCode);
        assertEquals(act.getEventCode(), testCode);
        assertNotNull(act.getExecution());
    }

    @Test
    public void testIsExecutionSetWithStacktrace(){
        final String testStacktrace = "TestStacktrace";
        Activity act = getDefaultActivity();

        assertNull(act.getExecution());
        act.setStackTrace(testStacktrace);
        assertEquals(act.getStackTrace(), testStacktrace);
        assertNotNull(act.getExecution());
    }

    @Test
    public void testIsExecutionSetWithMessage(){
        final String testMessage = "TestMessage";
        Activity act = getDefaultActivity();

        assertNull(act.getExecution());
        act.setEventMessage(testMessage);
        assertEquals(act.getEventMessage(), testMessage);
        assertNotNull(act.getExecution());
    }

    @Test
    public void testIsExecutionSetWithStatus(){
        final EventStatus testStatus = EventStatus.SUCCESS;
        final Integer testStatusValue = testStatus.getValue();
        Activity act = getDefaultActivity();

        assertNull(act.getExecution());
        act.setEventStatus(testStatusValue);
        assertEquals(act.getEventStatus(), testStatusValue);
        assertNotNull(act.getExecution());
    }

    @Test
    public void testSetExecutionTime(){
        final LocalDateTime testExecution = DateTimeUtility.now();
        Activity act = getDefaultActivity();

        assertNull(act.getExecution());
        act.setExecution(testExecution);
        assertEquals(act.getExecution(), testExecution);
    }

    @Test
    public void testIsExecutionOverwrittenBySetExecution(){
        final LocalDateTime testExecution = DateTimeUtility.now().minusSeconds(1);
        final String testMessage = "TestMessage";
        Activity act = getDefaultActivity();

        assertNull(act.getExecution());
        act.setEventMessage(testMessage);
        assertEquals(act.getEventMessage(), testMessage);

        assertNotNull(act.getExecution());
        assertNotEquals(testExecution, act.getExecution());
        act.setExecution(testExecution);
        assertEquals(act.getExecution(), testExecution);
    }

    @Test
    public void testIsExecutionNotOverwrittenByAnotherEventCall(){
        final LocalDateTime testExecution = DateTimeUtility.now().minusSeconds(1);
        final String testMessage = "TestMessage";
        Activity act = getDefaultActivity();

        assertNull(act.getExecution());
        act.setExecution(testExecution);
        assertNotNull(act.getExecution());
        assertEquals(act.getExecution(), testExecution);

        act.setEventMessage(testMessage);
        assertEquals(act.getEventMessage(), testMessage);
        assertEquals(act.getExecution(), testExecution);
    }

    @Test
    public void isStartTimeSet(){
        ActivityImpl act = (ActivityImpl)getDefaultActivity();
        assertNotNull(act.getStartTime());
        final LocalDateTime time = DateTimeUtility.now().plusSeconds(1);
        assertNotEquals(act.getStartTime(), time);
        act.setStartTime(time);
        assertEquals(act.getStartTime(), time);
    }

    @Test
    public void isExecutionTimeNotSetWithCreateEvent(){
        Activity act = getDefaultActivity();
        assertNull(act.getExecution());
        Event event = act.createEvent();
        assertNotNull(act.getExecution());
        assertEquals(event.getExecutionTime(), act.getExecution());
    }
}
