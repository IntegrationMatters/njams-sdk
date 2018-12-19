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

import com.faizsiegeln.njams.messageformat.v4.projectmessage.AttributeType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ExtractRule;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.RuleType;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;

/**
 *
 * @author krautenberg@integrationmatters.com
 */
public class ExtractHandlerTest {

    /**
     * This method tests if the ExtractHandler handles extracts even if there is
     * no sourcedata but a custom attribute.
     */
    @Test
    public void testHandleExtractVALUEWithoutSourceData() {
        final String TESTKEY = "CustomTestAttribute";
        final String TESTVALUE = "TestRule";
        final String ACTIVITYNAME = "act";

        Path clientPath = new Path("SDK4", "TEST");

        Settings config = new Settings();

        Njams njams = spy(new Njams(clientPath, "1.0.0", "sdk4", config));

        Path processPath = new Path("PROCESSES");
        ProcessModel process = njams.createProcess(processPath);

        ActivityModel model = process.createActivity(ACTIVITYNAME, "Act", null);

        //-------- The Rule
        ExtractRule rule = mock(ExtractRule.class);

        when(rule.getAttribute()).thenReturn(TESTKEY);
        when(rule.getAttributeType()).thenReturn(AttributeType.ATTRIBUTE);
        when(rule.getRuleType()).thenReturn(RuleType.VALUE);
        when(rule.getRule()).thenReturn(TESTVALUE);
        when(rule.getInout()).thenReturn("in");

        //-------- The Extract with the rule
        Extract extract = mock(Extract.class);

        when(extract.getName()).thenReturn("Test");
        List<ExtractRule> rules = new ArrayList<>();
        rules.add(rule);
        when(extract.getExtractRules()).thenReturn(rules);
        
        //-------- The ActivityConfiguration with the Extract       
        ActivityConfiguration activity = new ActivityConfiguration();
        
        activity.setTracepoint(null);
        activity.setExtract(extract); 
        
        //-------- The ProcessConfiguration with the ActivityConfiguration
        ProcessConfiguration processConf = new ProcessConfiguration();
        
        processConf.setLogLevel(LogLevel.INFO);
        processConf.setExclude(false);
        processConf.setRecording(true);
        Map<String, ActivityConfiguration> activityMap = new HashMap<>();
        activityMap.put(ACTIVITYNAME, activity);
        processConf.setActivities(activityMap);
        
        //-------- The Configuration with the ProcessConfiguration
        
        Configuration conf = new Configuration();
        conf.setLogMode(LogMode.COMPLETE);
        conf.setDataMasking(new ArrayList<>());
        conf.setRecording(true);
        Map<String, ProcessConfiguration> processes = new HashMap<>();      
        processes.put(njams.getProcessModel(processPath).getPath().toString(), processConf);
        conf.setProcesses(processes);
        //-------- Inject the Configuration instead of the one that was 
        //created by njams
        doReturn(conf).when(njams).getConfiguration();

        //-------- Test
        JobImpl job = (JobImpl) process.createJob();

        ActivityImpl impl = (ActivityImpl) job.createActivity(model).build();
        ExtractHandler.handleExtract(job, impl, "in", null, null);

        Assert.assertNotNull(impl.getAttributes().get(TESTKEY));
        Assert.assertEquals(TESTVALUE, impl.getAttributes().get(TESTKEY));
    }
}
