/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.time.LocalDateTime;
import java.util.Properties;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.AttributeType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ExtractRule;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.RuleType;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

/**
 *
 * @author pnientiedt
 */
public class JobTest {

    @Test
    public void testEventExtract() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties properties = new Properties();
        // Create client config
        Settings config = new Settings();
        config.setProperties(properties);

        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);

        Path processPath = new Path("PROCESSES", "testWithoutModel");

        //Creates an empty process model
        ProcessModel process = njams.createProcess(processPath);
        ActivityModel actModelA = process.createActivity("a", "a", "a");
        ActivityModel actModelB = actModelA.transitionTo("b", "b", "b");
        njams.start();

        ExtractRule rule = new ExtractRule();
        rule.setAttribute("eventType");
        rule.setAttributeType(AttributeType.EVENT);
        rule.setRuleType(RuleType.VALUE);
        rule.setRule("error");
        rule.setInout("out");

        Extract extract = new Extract();
        extract.setName("test");
        extract.getExtractRules().add(rule);

        njams.getConfiguration().getProcesses().put(process.getPath().toString(), new ProcessConfiguration());
        njams.getConfiguration().getProcess(process.getPath().toString()).getActivities().put("b",
                new ActivityConfiguration());
        njams.getConfiguration().getProcess(process.getPath().toString()).getActivity("b").setExtract(extract);

        // Start client and flush resources
        //        njams.start();

        // Create a Log Message???
        Job job = process.createJob("myJobId");

        assertThat(job.getStatus(), is(JobStatus.CREATED));
        assertThat(job.getMaxSeverity(), is(JobStatus.SUCCESS));

        job.start();

        //Create activitys
        Activity a = job.createActivity(actModelA).setExecution(LocalDateTime.now())
                .setActivityStatus(ActivityStatus.SUCCESS)
                .build();
        assertThat(job.getStatus(), is(JobStatus.RUNNING));
        assertThat(job.getMaxSeverity(), is(JobStatus.SUCCESS));

        //step to b
        Activity b = a.stepTo(actModelB).setExecution(LocalDateTime.now()).build();
        b.end();
        assertThat(job.getStatus(), is(JobStatus.RUNNING));
        assertThat(job.getMaxSeverity(), is(JobStatus.ERROR));

        ActivityImpl bExt = (ActivityImpl) b;
        assertThat(job.getStatus(), is(JobStatus.RUNNING));
        assertThat(job.getMaxSeverity(), is(JobStatus.ERROR));
    }
}
