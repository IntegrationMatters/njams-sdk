/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.model;

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.settings.Settings;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * This class tests the ProcessModel
 */
public class ProcessModelTest extends AbstractTest {

    /**
     * This method tests if the created job has a logId, a jobId and that they are neither null
     * nor empty and that they are equal.
     */
    @Test
    public void testCreateJob(){
        Job job = process.createJob();
        String actualJobId = job.getJobId();
        String actualLogId = job.getLogId();
        assertNotNull(actualJobId);
        assertNotEquals("", actualJobId);
        assertNotNull(actualLogId);
        assertNotEquals("", actualLogId);
        assertEquals(actualJobId, actualLogId);
    }

    /**
     * This method tests if the created job has a logId and a jobId. The jobId has been
     * explicitly set and it is tested that it is different than the logId.
     */
    @Test
    public void testCreateJobWithJobId(){
        final String testJobId = "testJobId";
        Job job = process.createJob(testJobId);
        String actualJobId = job.getJobId();
        String actualLogId = job.getLogId();
        assertEquals(testJobId, actualJobId);
        assertNotNull(actualLogId);
        assertNotEquals("", actualLogId);
        assertNotEquals(actualJobId, actualLogId);
    }

    /**
     * This method tests if the created job has a logId and a jobId, both are explicitly set.
     * It is tested if the jobId as well as the logId have been set accordingly.
     */
    @Test
    public void testCreateJobWithExplicitLogId(){
        final String testLogId = "testLogId";
        final String testJobId = "testJobId";
        Job job = process.createJobWithExplicitLogId(testJobId, testLogId);
        assertEquals(testLogId, job.getLogId());
        assertEquals(testJobId, job.getJobId());
    }

    @Test
    public void testCreateTransition_withoutExplicitName_nameIsNull() {
        process.createActivity("baseFrom", "From", "step");
        process.createActivity("baseTo", "To", "step");
        TransitionModel t = process.createTransition("baseFrom", "baseTo", "baseFrom_baseTo");
        assertNull("Transition created without explicit name must have null name", t.getName());
    }

    @Test
    public void testCreateTransition_withServerCompatibility61_usesIdAsName() {
        Settings settings = TestSender.getSettings();
        settings.put(NjamsSettings.PROPERTY_SERVER_COMPATIBILITY, "6.1");
        Njams compatNjams = new Njams(Path.of("SDK4", "COMPAT61"), "TEST", "SDK4", settings);
        ProcessModel compatProcess = compatNjams.createProcess(Path.of("COMPAT_PROC"));
        compatNjams.start();

        compatProcess.createActivity("cfrom", "From", "step");
        compatProcess.createActivity("cto", "To", "step");
        TransitionModel t = compatProcess.createTransition("cfrom", "cto", "cfrom_cto");
        assertEquals("6.1 compat mode: transition name must equal its ID", "cfrom_cto", t.getName());
    }
}