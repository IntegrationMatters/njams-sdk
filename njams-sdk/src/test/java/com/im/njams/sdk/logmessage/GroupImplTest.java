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

import static org.junit.Assert.assertFalse;

import java.util.List;

import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

/**
 *
 * @author krautenberg@integrationmatters.com
 */
public class GroupImplTest {

    /**
     * Test of removeNotRunningChildActivities method, of class GroupImpl.
     */
    @Test
    public void testRemoveNotRunningChildActivities() {
        Path clientPath = new Path();

        Settings config = new Settings();

        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);
        Path processPath = new Path();
        ProcessModel process = njams.createProcess(processPath);

        Job job = process.createJob("myJob");

        //Create a group with four children
        GroupImpl group = (GroupImpl) job.createGroup("start").build();
        Activity child1 = group.createChildActivity("child1").build();
        Activity child2 = group.createChildActivity("child2").build();
        Activity child3 = group.createChildActivity("child3").build();
        Activity child4 = group.createChildActivity("child4").build();
        //This shouldn't remove any child, because they are all RUNNING
        group.removeNotRunningChildActivity(child1.getInstanceId());
        group.removeNotRunningChildActivity(child2.getInstanceId());
        group.removeNotRunningChildActivity(child3.getInstanceId());
        group.removeNotRunningChildActivity(child4.getInstanceId());
        List<Activity> childActivities = group.getChildActivities();
        assertFalse(childActivities.contains(child1));
        assertFalse(childActivities.contains(child2));
        assertFalse(childActivities.contains(child3));
        assertFalse(childActivities.contains(child4));
    }

}
