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
package com.im.test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication_to_merge.Communication;
import com.im.njams.sdk.communication_to_merge.http.HttpConstants;
import com.im.njams.sdk.communication_to_merge.jms.JmsConstants;
import com.im.njams.sdk.logmessage.*;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.SubProcessActivityModel;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.settings.encoding.Transformer;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Properties;



/**
 *
 * @author bwand
 */
public class NjamsSampleTest {
    @Test
    public void testWithoutModel() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties properties = new Properties();
        properties.put(Communication.COMMUNICATION, "HTTP");
        properties.put(HttpConstants.SENDER_URL, "http://localhost:8083/event");
        properties.put(HttpConstants.SENDER_USERNAME, "admin");
        properties.put(HttpConstants.SENDER_PASSWORD, "admin");

        // Create client config
        Settings config = new Settings();
        config.setProperties(properties);

        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);

        Path processPath = new Path("PROCESSES", "testWithoutModel");

        //Creates an empty process model
        ProcessModel process = njams.createProcess(processPath);
        process.createActivity("a", "a", "a").transitionTo("b", "b", "b");
        // Start client and flush resources
        njams.start();

        // Create a Log Message???
        Job job = process.createJob("myJobId");
        job.start();

        //Create activitys
        Activity a = job.createActivity("a").setExecution(LocalDateTime.now()).build();
        a.processInput("testdata");
        a.processOutput("testdata");
        Assert.assertThat(a, CoreMatchers.notNullValue());
        Assert.assertThat(a.getInstanceId(), CoreMatchers.notNullValue());
        Assert.assertThat(a.getModelId(), CoreMatchers.is("a"));

        //step to b
        Activity b = a.stepTo("b").setExecution(LocalDateTime.now()).build();
        b.processInput("testdata");
        b.processOutput("testdata");
        Assert.assertThat(b, CoreMatchers.notNullValue());
        Assert.assertThat(b.getInstanceId(), CoreMatchers.notNullValue());
        Assert.assertThat(b.getModelId(), CoreMatchers.is("b"));

        ActivityImpl bExt = (ActivityImpl) b;
        Assert.assertThat(bExt.getPredecessors().size(), CoreMatchers.is(1));
        Assert.assertThat(bExt.getPredecessors().get(0).getFromInstanceId(), CoreMatchers.is(a.getInstanceId()));
        Assert.assertThat(bExt.getPredecessors().get(0).getModelId(), CoreMatchers.is(a.getModelId() + "::" + b.getModelId()));

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }

    @Test
    public void testWithModel() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(Communication.COMMUNICATION, "JMS");
        communicationProperties.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        communicationProperties.put(JmsConstants.SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(JmsConstants.PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        communicationProperties.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(JmsConstants.USERNAME, "njams");
        communicationProperties.put(JmsConstants.PASSWORD, "njams");
        communicationProperties.put(JmsConstants.DESTINATION, "njams.endurance");

        // Create client config
        Settings config = new Settings();
        config.setProperties(communicationProperties);

        // Instantiate client for first application
        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);

        Path processPath = new Path("PROCESSES", "testWithModel");

        //Creates an empty process model
        ProcessModel process = njams.createProcess(processPath);

        //start model
        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);
        Assert.assertThat(startModel, CoreMatchers.notNullValue());
        Assert.assertThat(startModel.getId(), CoreMatchers.is("start"));
        Assert.assertThat(startModel.getName(), CoreMatchers.is("Start"));

        //step
        ActivityModel logModel = startModel.transitionTo("log", "Log", "stepType");
        Assert.assertThat(logModel, CoreMatchers.notNullValue());
        Assert.assertThat(logModel.getId(), CoreMatchers.is("log"));
        Assert.assertThat(logModel.getName(), CoreMatchers.is("Log"));
        Assert.assertThat(logModel.getIncomingTransitionFrom("start"), CoreMatchers.notNullValue());
        Assert.assertThat(logModel.getIncomingTransitionFrom("start").getId(), CoreMatchers.is("start::log"));
        Assert.assertThat(logModel.getIncomingTransitionFrom("start").getFromActivity(), CoreMatchers.is(startModel));
        Assert.assertThat(logModel.getIncomingTransitionFrom("start").getFromActivity().getId(), CoreMatchers.is("start"));
        Assert.assertThat(logModel.getIncomingTransitionFrom("start").getToActivity(), CoreMatchers.is(logModel));
        Assert.assertThat(logModel.getIncomingTransitionFrom("start").getToActivity().getId(), CoreMatchers.is("log"));

        //step
        ActivityModel endModel = logModel.transitionTo("end", "End", "endType");
        Assert.assertThat(endModel, CoreMatchers.notNullValue());
        Assert.assertThat(endModel.getId(), CoreMatchers.is("end"));
        Assert.assertThat(endModel.getName(), CoreMatchers.is("End"));
        Assert.assertThat(endModel.getIncomingTransitionFrom("log"), CoreMatchers.notNullValue());
        Assert.assertThat(endModel.getIncomingTransitionFrom("log").getId(), CoreMatchers.is("log::end"));
        Assert.assertThat(endModel.getIncomingTransitionFrom("log").getFromActivity(), CoreMatchers.is(logModel));

        njams.addImage("startType", "images/njams_java_sdk_process_start.png");
        njams.addImage("stepType", "images/njams_java_sdk_process_step.png");
        njams.addImage("endType", "images/njams_java_sdk_process_end.png");

        // Start client and flush resources
        njams.start();

        // Create a Log Message
        Job job = process.createJob();
        job.start();
        Activity start = job.createActivity(startModel).build();
        start.processInput("testdata");
        start.processOutput("testdata");
        Assert.assertThat(start.getModelId(), CoreMatchers.is("start"));
        Assert.assertThat(start.getSequence(), CoreMatchers.is(1L));
        Assert.assertThat(start.getInstanceId(), CoreMatchers.is("start$1"));

        Activity log = start.stepTo(logModel).build();
        log.processInput("testdata");
        log.processOutput("testdata");
        Assert.assertThat(log.getModelId(), CoreMatchers.is("log"));
        Assert.assertThat(log.getSequence(), CoreMatchers.is(2L));
        Assert.assertThat(log.getInstanceId(), CoreMatchers.is("log$2"));

        Activity end = log.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");
        Assert.assertThat(end.getModelId(), CoreMatchers.is("end"));
        Assert.assertThat(end.getSequence(), CoreMatchers.is(3L));
        Assert.assertThat(end.getInstanceId(), CoreMatchers.is("end$3"));

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }

    @Test
    public void testWithModelWithBranches() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(Communication.COMMUNICATION, "JMS");
        communicationProperties.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        communicationProperties.put(JmsConstants.SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(JmsConstants.PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        communicationProperties.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(JmsConstants.USERNAME, "njams");
        communicationProperties.put(JmsConstants.PASSWORD, "njams");
        communicationProperties.put(JmsConstants.DESTINATION, "njams.endurance");

        // Create client config
        Settings config = new Settings();
        config.setProperties(communicationProperties);

        // Instantiate client for first application
        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);

        Path processPath = new Path("PROCESSES", "testWithModelWithBranches");

        //Creates an empty process model
        ProcessModel process = njams.createProcess(processPath);
        process.setStarter(true);

        //start model
        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);
        Assert.assertThat(startModel, CoreMatchers.notNullValue());
        Assert.assertThat(startModel.getId(), CoreMatchers.is("start"));
        Assert.assertThat(startModel.getName(), CoreMatchers.is("Start"));

        //step to log
        ActivityModel logModel = startModel.transitionTo("log", "Log", "stepType");
        Assert.assertThat(logModel, CoreMatchers.notNullValue());
        Assert.assertThat(logModel.getId(), CoreMatchers.is("log"));
        Assert.assertThat(logModel.getName(), CoreMatchers.is("Log"));
        Assert.assertThat(logModel.getIncomingTransitionFrom("start"), CoreMatchers.notNullValue());
        Assert.assertThat(logModel.getIncomingTransitionFrom("start").getId(), CoreMatchers.is("start::log"));
        Assert.assertThat(logModel.getIncomingTransitionFrom("start").getFromActivity(), CoreMatchers.is(startModel));
        Assert.assertThat(logModel.getIncomingTransitionFrom("start").getFromActivity().getId(), CoreMatchers.is("start"));
        Assert.assertThat(logModel.getIncomingTransitionFrom("start").getToActivity(), CoreMatchers.is(logModel));
        Assert.assertThat(logModel.getIncomingTransitionFrom("start").getToActivity().getId(), CoreMatchers.is("log"));

        //step to end
        ActivityModel endModel = logModel.transitionTo("end", "End", "endType");
        Assert.assertThat(endModel, CoreMatchers.notNullValue());
        Assert.assertThat(endModel.getId(), CoreMatchers.is("end"));
        Assert.assertThat(endModel.getName(), CoreMatchers.is("End"));
        Assert.assertThat(endModel.getIncomingTransitionFrom("log"), CoreMatchers.notNullValue());
        Assert.assertThat(endModel.getIncomingTransitionFrom("log").getId(), CoreMatchers.is("log::end"));
        Assert.assertThat(endModel.getIncomingTransitionFrom("log").getFromActivity(), CoreMatchers.is(logModel));

        //step to null
        ActivityModel nullModel = startModel.transitionTo("null", "Null", "nullType");
        Assert.assertThat(nullModel, CoreMatchers.notNullValue());
        Assert.assertThat(nullModel.getId(), CoreMatchers.is("null"));
        Assert.assertThat(nullModel.getName(), CoreMatchers.is("Null"));
        Assert.assertThat(nullModel.getIncomingTransitionFrom("start"), CoreMatchers.notNullValue());
        Assert.assertThat(nullModel.getIncomingTransitionFrom("start").getId(), CoreMatchers.is("start::null"));
        Assert.assertThat(nullModel.getIncomingTransitionFrom("start").getFromActivity(), CoreMatchers.is(startModel));
        Assert.assertThat(nullModel.getIncomingTransitionFrom("start").getFromActivity().getId(), CoreMatchers.is("start"));
        Assert.assertThat(nullModel.getIncomingTransitionFrom("start").getToActivity(), CoreMatchers.is(nullModel));
        Assert.assertThat(nullModel.getIncomingTransitionFrom("start").getToActivity().getId(), CoreMatchers.is("null"));

        //step to null
        endModel = nullModel.transitionTo("end", "End", "endType");
        Assert.assertThat(endModel, CoreMatchers.notNullValue());
        Assert.assertThat(endModel.getId(), CoreMatchers.is("end"));
        Assert.assertThat(endModel.getName(), CoreMatchers.is("End"));
        Assert.assertThat(endModel.getIncomingTransitionFrom("null"), CoreMatchers.notNullValue());
        Assert.assertThat(endModel.getIncomingTransitionFrom("null").getId(), CoreMatchers.is("null::end"));
        Assert.assertThat(endModel.getIncomingTransitionFrom("null").getFromActivity(), CoreMatchers.is(nullModel));

        nullModel.setY(nullModel.getY() + 50);
        logModel.setY(logModel.getY() - 50);

        njams.addImage("startType", "images/njams_java_sdk_process_start.png");
        njams.addImage("stepType", "images/njams_java_sdk_process_step.png");
        njams.addImage("nullType", "images/client.png");
        njams.addImage("endType", "images/njams_java_sdk_process_end.png");

        // Start client and flush resources
        njams.start();

        // Create a Log Message
        Job job = process.createJob();
        job.start();
        Activity start = job.createActivity(startModel).build();
        start.processInput("testdata");
        start.processOutput("testdata");
        Assert.assertThat(start.getModelId(), CoreMatchers.is("start"));
        Assert.assertThat(start.getSequence(), CoreMatchers.is(1L));
        Assert.assertThat(start.getInstanceId(), CoreMatchers.is("start$1"));

        Activity log = start.stepTo(logModel).build();
        log.processInput("testdata");
        log.processOutput("testdata");
        Assert.assertThat(log.getModelId(), CoreMatchers.is("log"));
        Assert.assertThat(log.getSequence(), CoreMatchers.is(2L));
        Assert.assertThat(log.getInstanceId(), CoreMatchers.is("log$2"));

        Activity end = log.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");
        Assert.assertThat(end.getModelId(), CoreMatchers.is("end"));
        Assert.assertThat(end.getSequence(), CoreMatchers.is(3L));
        Assert.assertThat(end.getInstanceId(), CoreMatchers.is("end$3"));

        Activity nullA = start.stepTo(nullModel).build();
        nullA.processInput("testdata");
        nullA.processOutput("testdata");
        Assert.assertThat(nullA.getModelId(), CoreMatchers.is("null"));
        Assert.assertThat(nullA.getSequence(), CoreMatchers.is(4L));
        Assert.assertThat(nullA.getInstanceId(), CoreMatchers.is("null$4"));

        end = nullA.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");
        Assert.assertThat(end.getModelId(), CoreMatchers.is("end"));
        Assert.assertThat(end.getSequence(), CoreMatchers.is(3L));
        Assert.assertThat(end.getInstanceId(), CoreMatchers.is("end$3"));

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }

    @Test
    public void testGroupWithoutModel() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(Communication.COMMUNICATION, "JMS");
        communicationProperties.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        communicationProperties.put(JmsConstants.SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(JmsConstants.PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        communicationProperties.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(JmsConstants.USERNAME, "njams");
        communicationProperties.put(JmsConstants.PASSWORD, "njams");
        communicationProperties.put(JmsConstants.DESTINATION, "njams.endurance");

        // Create client config
        Settings config = new Settings();
        config.setProperties(communicationProperties);

        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);

        Path processPath = new Path("PROCESSES", "testGroupWithoutModel");

        //Create the process model
        ProcessModel process = njams.createProcess(processPath);
        process.createActivity("start", "start", "startType")
                .transitionToGroup("group", "group", "groupType")
                .createChildActivity("child1", "child1", "stepType")
                .transitionTo("child2", "child2", "stepType");
        GroupModel groupModel = process.getGroup("group");
        groupModel.transitionTo("end", "end", "endType");
        process.getActivity("start").setStarter(true);
        process.getActivity("child1").setStarter(true);

        // Start client and flush resources
        njams.start();

        // Create a Log Message???
        Job job = process.createJob("myJobId");
        job.start();

        //Create activitys
        Activity a = job.createActivity("start").setExecution(LocalDateTime.now()).build();
        a.processInput("testdata");
        a.processOutput("testdata");

        //step to group
        Group group = a.stepToGroup("group").setExecution(LocalDateTime.now()).build();
        group.processInput("testdata");
        group.processOutput("testdata");

        Activity child1 = group.createChildActivity("child1").build();
        child1.processInput("testdata");
        child1.processOutput("testdata");
        ActivityImpl child1Impl = (ActivityImpl) child1;
        Assert.assertThat(child1Impl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(child1Impl.getIteration(), CoreMatchers.is(1L));

        Activity child2 = child1.stepTo("child2").build();
        child2.processInput("testdata");
        child2.processOutput("testdata");
        ActivityImpl child2Impl = (ActivityImpl) child2;
        Assert.assertThat(child2Impl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(child1Impl.getIteration(), CoreMatchers.is(1L));

        group.iterate();

        Activity child1_2 = group.createChildActivity("child1").build();
        child1_2.processInput("testdata");
        child1_2.processOutput("testdata");
        ActivityImpl child1_2Impl = (ActivityImpl) child1_2;
        Assert.assertThat(child1_2Impl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(child1_2Impl.getIteration(), CoreMatchers.is(2L));

        Activity child2_2 = child1_2.stepTo("child2").build();
        child2_2.processInput("testdata");
        child2_2.processOutput("testdata");
        ActivityImpl child2_2Impl = (ActivityImpl) child2_2;
        Assert.assertThat(child2_2Impl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(child2_2Impl.getIteration(), CoreMatchers.is(2L));

        Group parent = child2_2.getParent();
        Assert.assertThat(parent, CoreMatchers.is(group));

        parent.stepTo("end").build();
        a.processInput("testdata");
        a.processOutput("testdata");

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }

    @Test
    public void testGroupWithModel() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(Communication.COMMUNICATION, "JMS");
        communicationProperties.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        communicationProperties.put(JmsConstants.SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(JmsConstants.PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        communicationProperties.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(JmsConstants.USERNAME, "njams");
        communicationProperties.put(JmsConstants.PASSWORD, "njams");
        communicationProperties.put(JmsConstants.DESTINATION, "njams.endurance");

        // Create client config
        Settings config = new Settings();
        config.setProperties(communicationProperties);

        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);

        Path processPath = new Path("PROCESSES", "testGroupWithModel");

        //Creates an empty process model
        ProcessModel process = njams.createProcess(processPath);

        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);
        Assert.assertThat(startModel.getParent(), CoreMatchers.nullValue());

        GroupModel groupModel = startModel.transitionToGroup("group", "Group", "groupType");
        Assert.assertThat(groupModel.getParent(), CoreMatchers.nullValue());
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(0));

        ActivityModel groupStartModel = groupModel.createChildActivity("groupStart", "GroupStart", "stepType");
        groupStartModel.setStarter(true);
        Assert.assertThat(groupStartModel.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(1));

        ActivityModel upperModel = groupStartModel.transitionTo("upper", "Upper", "stepType");
        Assert.assertThat(upperModel.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(2));

        ActivityModel groupEndModel = upperModel.transitionTo("groupEnd", "GroupEnd", "stepType");
        Assert.assertThat(groupEndModel.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(3));

        ActivityModel lowerModel = groupStartModel.transitionTo("lower", "Lower", "stepType");
        Assert.assertThat(lowerModel.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(4));

        ActivityModel groupEndModel2 = lowerModel.transitionTo("groupEnd", "GroupEnd", "stepType");
        Assert.assertThat(groupEndModel2, CoreMatchers.is(groupEndModel));
        Assert.assertThat(groupEndModel2.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(4));

        GroupModel parentModel = groupEndModel.getParent();
        Assert.assertThat(parentModel.getParent(), CoreMatchers.nullValue());
        Assert.assertThat(parentModel, CoreMatchers.is(groupModel));

        ActivityModel endModel = groupEndModel.getParent().transitionTo("end", "End", "endType");
        Assert.assertThat(endModel.getParent(), CoreMatchers.nullValue());

        // Start client and flush resources
        njams.start();

        // Create a Log Message???
        Job job = process.createJob("myJobId");
        job.start();

        //Create activitys
        Activity a = job.createActivity(startModel).setExecution(LocalDateTime.now()).build();
        a.processInput("testdata");
        a.processOutput("testdata");

        //step to group
        Group group = a.stepToGroup(groupModel).setExecution(LocalDateTime.now()).build();
        group.processInput("testdata");
        group.processOutput("testdata");

        Activity groupStart = group.createChildActivity(groupStartModel).build();
        groupStart.processInput("testdata");
        groupStart.processOutput("testdata");
        ActivityImpl groupStartImpl = (ActivityImpl) groupStart;
        Assert.assertThat(groupStartImpl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(groupStartImpl.getIteration(), CoreMatchers.is(1L));

        Activity upper = groupStart.stepTo(upperModel).build();
        upper.processInput("testdata");
        upper.processOutput("testdata");
        ActivityImpl upperImpl = (ActivityImpl) upper;
        Assert.assertThat(upperImpl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(upperImpl.getIteration(), CoreMatchers.is(1L));

        Activity groupEnd = upper.stepTo(groupEndModel).build();
        groupEnd.processInput("testdata");
        groupEnd.processOutput("testdata");
        ActivityImpl groupEndImpl = (ActivityImpl) groupEnd;
        Assert.assertThat(groupEndImpl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(groupEndImpl.getIteration(), CoreMatchers.is(1L));

        group.iterate();

        Activity groupStart_2 = group.createChildActivity(groupStartModel).build();
        groupStart_2.processInput("testdata");
        groupStart_2.processOutput("testdata");
        ActivityImpl groupStartImpl2 = (ActivityImpl) groupStart_2;
        Assert.assertThat(groupStartImpl2.getParent(), CoreMatchers.is(group));
        Assert.assertThat(groupStartImpl2.getIteration(), CoreMatchers.is(2L));

        Activity lower = groupStart_2.stepTo(lowerModel).build();
        lower.processInput("testdata");
        lower.processOutput("testdata");
        ActivityImpl lowerImpl = (ActivityImpl) lower;
        Assert.assertThat(lowerImpl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(lowerImpl.getIteration(), CoreMatchers.is(2L));

        Activity groupEnd_2 = lower.stepTo(groupEndModel).build();
        groupEnd_2.processInput("testdata");
        groupEnd_2.processOutput("testdata");
        ActivityImpl groupEndImpl_2 = (ActivityImpl) groupEnd_2;
        Assert.assertThat(groupEndImpl_2.getParent(), CoreMatchers.is(group));
        Assert.assertThat(groupEndImpl_2.getIteration(), CoreMatchers.is(2L));

        Group parent = groupEnd_2.getParent();
        Assert.assertThat(parent, CoreMatchers.is(group));

        Activity end = parent.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();
    }

    @Test
    public void testGroupInGroupWithModel() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(Communication.COMMUNICATION, "JMS");
        communicationProperties.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        communicationProperties.put(JmsConstants.SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(JmsConstants.PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        communicationProperties.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(JmsConstants.USERNAME, "njams");
        communicationProperties.put(JmsConstants.PASSWORD, "njams");
        communicationProperties.put(JmsConstants.DESTINATION, "njams.endurance");

        // Create client config
        Settings config = new Settings();
        config.setProperties(communicationProperties);

        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);

        Path processPath = new Path("PROCESSES", "testGroupInGroupWithModel");

        //Creates an empty process model
        ProcessModel process = njams.createProcess(processPath);

        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);
        Assert.assertThat(startModel.getParent(), CoreMatchers.nullValue());

        GroupModel groupModel = startModel.transitionToGroup("group", "Group", "groupType");
        Assert.assertThat(groupModel.getParent(), CoreMatchers.nullValue());
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(0));

        ActivityModel child1Model = groupModel.createChildActivity("child1", "Child1", "stepType");
        child1Model.setStarter(true);
        Assert.assertThat(child1Model.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(1));

        GroupModel subgroupModel = child1Model.transitionToGroup("subgroup", "SubGroup", "subgroupType");
        Assert.assertThat(subgroupModel.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(2));

        ActivityModel subchild1Model = subgroupModel.createChildActivity("subchild1", "SubChild1", "stepType");
        subchild1Model.setStarter(true);
        Assert.assertThat(subchild1Model.getParent(), CoreMatchers.is(subgroupModel));
        Assert.assertThat(subgroupModel.getChildActivities().size(), CoreMatchers.is(1));

        ActivityModel subchild2Model = subchild1Model.transitionTo("subchild2", "SubChild2", "stepType");
        Assert.assertThat(subchild2Model.getParent(), CoreMatchers.is(subgroupModel));
        Assert.assertThat(subgroupModel.getChildActivities().size(), CoreMatchers.is(2));

        ActivityModel child2Model = subchild2Model.getParent().transitionTo("child2", "Child2", "stepType");
        Assert.assertThat(child2Model.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(3));

        GroupModel parentModel = child2Model.getParent();
        Assert.assertThat(parentModel.getParent(), CoreMatchers.nullValue());
        Assert.assertThat(parentModel, CoreMatchers.is(groupModel));

        ActivityModel endModel = child2Model.getParent().transitionTo("end", "End", "endType");
        Assert.assertThat(endModel.getParent(), CoreMatchers.nullValue());

        // Start client and flush resources
        njams.start();

        // Create a Log Message???
        Job job = process.createJob("myJobId");
        job.start();

        //Create activitys
        Activity a = job.createActivity(startModel).setExecution(LocalDateTime.now()).build();
        a.processInput("testdata");
        a.processOutput("testdata");

        //step to group
        Group group = a.stepToGroup(groupModel).setExecution(LocalDateTime.now()).build();
        group.processInput("testdata");
        group.processOutput("testdata");

        //iteration 1
        Activity child1 = group.createChildActivity(child1Model).build();
        child1.processInput("testdata");
        child1.processOutput("testdata");
        ActivityImpl child1Impl = (ActivityImpl) child1;
        Assert.assertThat(child1Impl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(child1Impl.getIteration(), CoreMatchers.is(1L));

        Group subGroup = child1.stepToGroup(subgroupModel).build();
        subGroup.processInput("testdata");
        subGroup.processOutput("testdata");
        GroupImpl groupImpl = (GroupImpl) subGroup;
        Assert.assertThat(groupImpl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(groupImpl.getIteration(), CoreMatchers.is(1L));

        //subchild
        Activity subChild1 = subGroup.createChildActivity(subchild1Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");
        ActivityImpl subChild1Impl = (ActivityImpl) subChild1;
        Assert.assertThat(subChild1Impl.getParent(), CoreMatchers.is(subGroup));
        Assert.assertThat(subChild1Impl.getIteration(), CoreMatchers.is(1L));

        Activity subChild2 = subChild1.stepTo(subchild2Model).build();
        subChild2.processInput("testdata");
        subChild2.processOutput("testdata");
        ActivityImpl subChild2Impl = (ActivityImpl) subChild2;
        Assert.assertThat(subChild2Impl.getParent(), CoreMatchers.is(subGroup));
        Assert.assertThat(subChild2Impl.getIteration(), CoreMatchers.is(1L));

        Activity child2 = subChild1.getParent().stepTo(child2Model).build();
        child2.processInput("testdata");
        child2.processOutput("testdata");
        ActivityImpl child2Impl = (ActivityImpl) child2;
        Assert.assertThat(child2Impl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(child1Impl.getIteration(), CoreMatchers.is(1L));

        group.iterate();

        //iteration 2
        Activity child1_2 = group.createChildActivity(child1Model).build();
        child1_2.processInput("testdata");
        child1_2.processOutput("testdata");
        ActivityImpl child1_2Impl = (ActivityImpl) child1_2;
        Assert.assertThat(child1_2Impl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(child1_2Impl.getIteration(), CoreMatchers.is(2L));

        Group subGroup_2 = child1_2.stepToGroup(subgroupModel).build();
        subGroup_2.processInput("testdata");
        subGroup_2.processOutput("testdata");
        GroupImpl groupImpl_2 = (GroupImpl) subGroup_2;
        Assert.assertThat(groupImpl_2.getParent(), CoreMatchers.is(group));
        Assert.assertThat(groupImpl_2.getIteration(), CoreMatchers.is(2L));

        //subchild
        Activity subChild1_2 = subGroup_2.createChildActivity(subchild1Model).build();
        subChild1_2.processInput("testdata");
        subChild1_2.processOutput("testdata");
        ActivityImpl subChild1Impl_2 = (ActivityImpl) subChild1_2;
        Assert.assertThat(subChild1Impl_2.getParent(), CoreMatchers.is(subGroup_2));
        Assert.assertThat(subChild1Impl_2.getIteration(), CoreMatchers.is(1L));

        Activity subChild2_2 = subChild1_2.stepTo(subchild2Model).build();
        subChild2_2.processInput("testdata");
        subChild2_2.processOutput("testdata");
        ActivityImpl subChild2Impl_2 = (ActivityImpl) subChild2_2;
        Assert.assertThat(subChild2Impl_2.getParent(), CoreMatchers.is(subGroup_2));
        Assert.assertThat(subChild2Impl_2.getIteration(), CoreMatchers.is(1L));

        Activity child2_2 = subChild2_2.getParent().stepTo(child2Model).build();
        child2_2.processInput("testdata");
        child2_2.processOutput("testdata");
        ActivityImpl child2_2Impl = (ActivityImpl) child2_2;
        Assert.assertThat(child2_2Impl.getParent(), CoreMatchers.is(group));
        Assert.assertThat(child2_2Impl.getIteration(), CoreMatchers.is(2L));

        Group parent = child2_2.getParent();
        Assert.assertThat(parent, CoreMatchers.is(group));

        Activity end = parent.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }

    @Test
    public void testSubprocess() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(Communication.COMMUNICATION, "JMS");
        communicationProperties.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        communicationProperties.put(JmsConstants.SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(JmsConstants.PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        communicationProperties.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(JmsConstants.USERNAME, "njams");
        communicationProperties.put(JmsConstants.PASSWORD, "njams");
        communicationProperties.put(JmsConstants.DESTINATION, "njams.endurance");

        // Create client config
        Settings config = new Settings();
        config.setProperties(communicationProperties);

        // Instantiate client for first application
        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);

        Path processPath = new Path("PROCESSES", "SubProcessCaller");

        //Creates an empty process model
        ProcessModel process = njams.createProcess(processPath);

        //start model
        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);

        //step
        SubProcessActivityModel subProcessModel =
                startModel.transitionToSubProcess("subProcess", "SubProcess", "stepType");

        //step
        ActivityModel endModel = subProcessModel.transitionTo("end", "End", "endType");

        //subprocess
        Path subProcessPath = new Path("PROCESSES", "SubProcess");
        ProcessModel subProcess = njams.createProcess(subProcessPath);

        ActivityModel subProcessStartModel = subProcess.createActivity("subProcessstart", "Start", "startType");
        subProcessStartModel.setStarter(true);
        ActivityModel subProcessLogModel = subProcessStartModel.transitionTo("subProcesslog", "Log", "stepType");
        ActivityModel subProcessEndModel = subProcessLogModel.transitionTo("subProcessend", "End", "endType");

        subProcessModel.setSubProcess(subProcess);

        njams.setTreeElementType(new Path("SDK4"), "first");
        njams.setTreeElementType(new Path("SDK4", "TEST"), "second");
        njams.setTreeElementType(new Path("SDK4", "TEST", "PROCESSES"), "third");
        njams.setTreeElementType(new Path("SDK4", "TEST", "PROCESSES", "SubProcess"), "fourth");
        njams.addImage("first", "images/njams_java_sdk_process_start.png");
        njams.addImage("second", "images/njams_java_sdk_process_start.png");
        njams.addImage("third", "images/njams_java_sdk_process_start.png");
        njams.addImage("fourth", "images/njams_java_sdk_process_start.png");
        njams.addImage("startType", "images/njams_java_sdk_process_start.png");
        njams.addImage("stepType", "images/njams_java_sdk_process_step.png");
        njams.addImage("endType", "images/njams_java_sdk_process_end.png");

        // Start client and flush resources
        njams.start();

        // Create a Log Message
        Job job = process.createJob();
        job.start();
        Activity start = job.createActivity(startModel).build();
        start.processInput("testdata");
        start.processOutput("testdata");

        SubProcessActivity subProcessCaller = start.stepToSubProcess(subProcessModel).build();
        subProcessCaller.processInput("testdata");
        subProcessCaller.processOutput("testdata");
        Activity subProcessStart = subProcessCaller.createChildActivity(subProcessStartModel).build();
        subProcessStart.processInput("testdata");
        subProcessStart.processOutput("testdata");

        //        Activity subProcessStart = job.createActivity(subProcessStartModel).build();
        Activity subProcessLog = subProcessStart.stepTo(subProcessLogModel).build();
        subProcessLog.processInput("testdata");
        subProcessLog.processOutput("testdata");
        Activity subProcessEnd = subProcessLog.stepTo(subProcessEndModel).build();
        subProcessEnd.processInput("testdata");
        subProcessEnd.processOutput("testdata");

        Activity end = subProcessCaller.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }

    @Test
    public void testGroupInGroupWithFlushes() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(Communication.COMMUNICATION, "JMS");
        communicationProperties.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        communicationProperties.put(JmsConstants.SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(JmsConstants.PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        communicationProperties.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(JmsConstants.USERNAME, "njams");
        communicationProperties.put(JmsConstants.PASSWORD, "njams");
        communicationProperties.put(JmsConstants.DESTINATION, "njams.endurance");

        // Create client config
        Settings config = new Settings();
        config.setProperties(communicationProperties);

        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);

        Path processPath = new Path("PROCESSES", "testGroupInGroupWithFlushes");

        //Creates an empty process model
        ProcessModel process = njams.createProcess(processPath);

        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);
        Assert.assertThat(startModel.getParent(), CoreMatchers.nullValue());

        GroupModel groupModel = startModel.transitionToGroup("group", "Group", "groupType");
        Assert.assertThat(groupModel.getParent(), CoreMatchers.nullValue());
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(0));

        ActivityModel child1Model = groupModel.createChildActivity("child1", "Child1", "stepType");
        child1Model.setStarter(true);
        Assert.assertThat(child1Model.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(1));

        GroupModel subgroupModel = child1Model.transitionToGroup("subgroup", "SubGroup", "subgroupType");
        Assert.assertThat(subgroupModel.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(2));

        ActivityModel subchild1Model = subgroupModel.createChildActivity("subchild1", "SubChild1", "stepType");
        subchild1Model.setStarter(true);
        Assert.assertThat(subchild1Model.getParent(), CoreMatchers.is(subgroupModel));
        Assert.assertThat(subgroupModel.getChildActivities().size(), CoreMatchers.is(1));

        ActivityModel subchild2Model = subchild1Model.transitionTo("subchild2", "SubChild2", "stepType");
        Assert.assertThat(subchild2Model.getParent(), CoreMatchers.is(subgroupModel));
        Assert.assertThat(subgroupModel.getChildActivities().size(), CoreMatchers.is(2));

        ActivityModel child2Model = subchild2Model.getParent().transitionTo("child2", "Child2", "stepType");
        Assert.assertThat(child2Model.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(3));

        GroupModel parentModel = child2Model.getParent();
        Assert.assertThat(parentModel.getParent(), CoreMatchers.nullValue());
        Assert.assertThat(parentModel, CoreMatchers.is(groupModel));

        ActivityModel endModel = child2Model.getParent().transitionTo("end", "End", "endType");
        Assert.assertThat(endModel.getParent(), CoreMatchers.nullValue());

        // Start client and flush resources
        njams.start();

        // Create a Log Message???
        Job job = process.createJob("myJobId");
        job.start();

        //Create activitys
        Activity a = job.createActivity(startModel).setExecution(LocalDateTime.now()).build();
        a.processInput("testdata");
        a.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(1));

        //step to group
        Group group = a.stepToGroup(groupModel).setExecution(LocalDateTime.now()).build();
        group.processInput("testdata");
        group.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(1));

        //iteration 1
        Activity child1 = group.createChildActivity(child1Model).build();
        child1.processInput("testdata");
        child1.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(2));

        Group subGroup = child1.stepToGroup(subgroupModel).build();
        subGroup.processInput("testdata");
        subGroup.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(2));

        //subchild
        Activity subChild1 = subGroup.createChildActivity(subchild1Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(3));

        subChild1.stepTo(subchild2Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(3));

        subChild1.getParent().stepTo(child2Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(2));

        group.iterate();

        //iteration 2
        Activity child1_2 = group.createChildActivity(child1Model).build();
        child1_2.processInput("testdata");
        child1_2.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(3));

        Group subGroup_2 = child1_2.stepToGroup(subgroupModel).build();
        subGroup_2.processInput("testdata");
        subGroup_2.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(3));

        //subchild
        Activity subChild1_2 = subGroup_2.createChildActivity(subchild1Model).build();
        subChild1_2.processInput("testdata");
        subChild1_2.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(4));

        Activity subChild2_2 = subChild1_2.stepTo(subchild2Model).build();
        subChild2_2.processInput("testdata");
        subChild2_2.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(4));

        Activity child2_2 = subChild2_2.getParent().stepTo(child2Model).build();
        child2_2.processInput("testdata");
        child2_2.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(3));

        Group parent = child2_2.getParent();

        Activity end = parent.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(1));

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }

    @Test
    public void testSubprocessSpawned() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(Communication.COMMUNICATION, "JMS");
        communicationProperties.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        communicationProperties.put(JmsConstants.SECURITY_PRINCIPAL, "njams");
        communicationProperties.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        communicationProperties.put(JmsConstants.PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        communicationProperties.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        communicationProperties.put(JmsConstants.USERNAME, "njams");
        communicationProperties.put(JmsConstants.PASSWORD, "njams");
        communicationProperties.put(JmsConstants.DESTINATION, "njams.endurance");

        // Create client config
        Settings config = new Settings();
        config.setProperties(communicationProperties);

        // Instantiate client for first application
        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);

        Path processPath = new Path("PROCESSES", "SubProcessSpawner");

        //Creates an empty process model
        ProcessModel process = njams.createProcess(processPath);

        //start model
        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);
        SubProcessActivityModel subProcessModel =
                startModel.transitionToSubProcess("subProcess", "SubProcess", "stepType");
        ActivityModel endModel = subProcessModel.transitionTo("end", "End", "endType");

        //subprocess
        Path subProcessPath = new Path("PROCESSES", "SubProcess");
        ProcessModel subProcess = njams.createProcess(subProcessPath);

        ActivityModel subProcessStartModel = subProcess.createActivity("subProcessstart", "Start", "startType");
        subProcessStartModel.setStarter(true);
        ActivityModel subProcessLogModel = subProcessStartModel.transitionTo("subProcesslog", "Log", "stepType");
        ActivityModel subProcessEndModel = subProcessLogModel.transitionTo("subProcessend", "End", "endType");

        subProcessModel.setSubProcess(subProcess.getName(), subProcess.getPath());

        njams.setTreeElementType(new Path("SDK4"), "first");
        njams.setTreeElementType(new Path("SDK4", "TEST"), "second");
        njams.setTreeElementType(new Path("SDK4", "TEST", "PROCESSES"), "third");
        njams.setTreeElementType(new Path("SDK4", "TEST", "PROCESSES", "SubProcess"), "fourth");
        njams.addImage("first", "images/njams_java_sdk_process_start.png");
        njams.addImage("second", "images/njams_java_sdk_process_start.png");
        njams.addImage("third", "images/njams_java_sdk_process_start.png");
        njams.addImage("fourth", "images/njams_java_sdk_process_start.png");
        njams.addImage("startType", "images/njams_java_sdk_process_start.png");
        njams.addImage("stepType", "images/njams_java_sdk_process_step.png");
        njams.addImage("endType", "images/njams_java_sdk_process_end.png");

        // Start client and flush resources
        njams.start();

        // Create a Log Message
        Job job = process.createJob();
        job.start();
        Activity start = job.createActivity(startModel).build();
        start.processInput("testdata");
        start.processOutput("testdata");
        SubProcessActivity subProcessCaller = (SubProcessActivity) start.stepTo(subProcessModel).build();
        subProcessCaller.processInput("testdata");
        subProcessCaller.processOutput("testdata");

        //spawned
        Job sjob = subProcess.createJob();
        sjob.start();
        Activity subProcessStart = sjob.createActivity(subProcessStartModel).build();
        subProcessStart.processInput("testdata");
        subProcessStart.processOutput("testdata");
        Activity subProcessLog = subProcessStart.stepTo(subProcessLogModel).build();
        subProcessLog.processInput("testdata");
        subProcessLog.processOutput("testdata");
        Activity subProcessEnd = subProcessLog.stepTo(subProcessEndModel).build();
        subProcessEnd.processInput("testdata");
        subProcessEnd.processOutput("testdata");
        sjob.end();

        //finishe spawner
        subProcessCaller.setSubProcess(null, null, sjob.getLogId());
        Activity end = subProcessCaller.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }

    @Test
    public void testGroupInGroupWithFlushesAndEncoded() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(Communication.COMMUNICATION, Transformer.encode("JMS"));
        communicationProperties.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                Transformer.encode("com.tibco.tibjms.naming.TibjmsInitialContextFactory"));
        communicationProperties.put(JmsConstants.SECURITY_PRINCIPAL, Transformer.encode("njams"));
        communicationProperties.put(JmsConstants.SECURITY_CREDENTIALS, Transformer.encode("njams"));
        communicationProperties.put(JmsConstants.PROVIDER_URL, Transformer.encode("tibjmsnaming://vslems01:7222"));
        communicationProperties.put(JmsConstants.CONNECTION_FACTORY, Transformer.encode("ConnectionFactory"));
        communicationProperties.put(JmsConstants.USERNAME, Transformer.encode("njams"));
        communicationProperties.put(JmsConstants.PASSWORD, Transformer.encode("njams"));
        communicationProperties.put(JmsConstants.DESTINATION, Transformer.encode("njams.endurance"));

        // Create client config
        Settings config = new Settings();
        config.setProperties(communicationProperties);

        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);

        Path processPath = new Path("PROCESSES", "testGroupInGroupWithFlushesAndEncoded");

        //Creates an empty process model
        ProcessModel process = njams.createProcess(processPath);

        ActivityModel startModel = process.createActivity("start", "Start", "startType");
        startModel.setStarter(true);
        Assert.assertThat(startModel.getParent(), CoreMatchers.nullValue());

        GroupModel groupModel = startModel.transitionToGroup("group", "Group", "groupType");
        Assert.assertThat(groupModel.getParent(), CoreMatchers.nullValue());
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(0));

        ActivityModel child1Model = groupModel.createChildActivity("child1", "Child1", "stepType");
        child1Model.setStarter(true);
        Assert.assertThat(child1Model.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(1));

        GroupModel subgroupModel = child1Model.transitionToGroup("subgroup", "SubGroup", "subgroupType");
        Assert.assertThat(subgroupModel.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(2));

        ActivityModel subchild1Model = subgroupModel.createChildActivity("subchild1", "SubChild1", "stepType");
        subchild1Model.setStarter(true);
        Assert.assertThat(subchild1Model.getParent(), CoreMatchers.is(subgroupModel));
        Assert.assertThat(subgroupModel.getChildActivities().size(), CoreMatchers.is(1));

        ActivityModel subchild2Model = subchild1Model.transitionTo("subchild2", "SubChild2", "stepType");
        Assert.assertThat(subchild2Model.getParent(), CoreMatchers.is(subgroupModel));
        Assert.assertThat(subgroupModel.getChildActivities().size(), CoreMatchers.is(2));

        ActivityModel child2Model = subchild2Model.getParent().transitionTo("child2", "Child2", "stepType");
        Assert.assertThat(child2Model.getParent(), CoreMatchers.is(groupModel));
        Assert.assertThat(groupModel.getChildActivities().size(), CoreMatchers.is(3));

        GroupModel parentModel = child2Model.getParent();
        Assert.assertThat(parentModel.getParent(), CoreMatchers.nullValue());
        Assert.assertThat(parentModel, CoreMatchers.is(groupModel));

        ActivityModel endModel = child2Model.getParent().transitionTo("end", "End", "endType");
        Assert.assertThat(endModel.getParent(), CoreMatchers.nullValue());

        // Start client and flush resources
        njams.start();

        // Create a Log Message???
        Job job = process.createJob("myJobId");
        job.start();

        //Create activitys
        Activity a = job.createActivity(startModel).setExecution(LocalDateTime.now()).build();
        a.processInput("testdata");
        a.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(1));

        //step to group
        Group group = a.stepToGroup(groupModel).setExecution(LocalDateTime.now()).build();
        group.processInput("testdata");
        group.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(1));

        //iteration 1
        Activity child1 = group.createChildActivity(child1Model).build();
        child1.processInput("testdata");
        child1.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(2));

        Group subGroup = child1.stepToGroup(subgroupModel).build();
        subGroup.processInput("testdata");
        subGroup.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(2));

        //subchild
        Activity subChild1 = subGroup.createChildActivity(subchild1Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(3));

        subChild1.stepTo(subchild2Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(3));

        subChild1.getParent().stepTo(child2Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(2));

        group.iterate();

        //iteration 2
        Activity child1_2 = group.createChildActivity(child1Model).build();
        child1_2.processInput("testdata");
        child1_2.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(3));

        Group subGroup_2 = child1_2.stepToGroup(subgroupModel).build();
        subGroup_2.processInput("testdata");
        subGroup_2.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(3));

        //subchild
        Activity subChild1_2 = subGroup_2.createChildActivity(subchild1Model).build();
        subChild1_2.processInput("testdata");
        subChild1_2.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(4));

        Activity subChild2_2 = subChild1_2.stepTo(subchild2Model).build();
        subChild2_2.processInput("testdata");
        subChild2_2.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(4));

        Activity child2_2 = subChild2_2.getParent().stepTo(child2Model).build();
        child2_2.processInput("testdata");
        child2_2.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(3));

        Group parent = child2_2.getParent();

        Activity end = parent.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");

        ((JobImpl) job).flush();
        Assert.assertThat(job.getActivities().size(), CoreMatchers.is(1));

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }
}
