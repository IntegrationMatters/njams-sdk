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
package com.im.njams.sdk.client;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.time.LocalDateTime;
import java.util.Properties;

import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.http.HttpSender;
import com.im.njams.sdk.communication.jms.JmsConstants;
import com.im.njams.sdk.logmessage.Activity;
import com.im.njams.sdk.logmessage.ActivityImpl;
import com.im.njams.sdk.logmessage.Group;
import com.im.njams.sdk.logmessage.GroupImpl;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.logmessage.JobImpl;
import com.im.njams.sdk.logmessage.SubProcessActivity;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.SubProcessActivityModel;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.settings.encoding.Transformer;

/**
 *
 * @author bwand
 */
public class NjamsSampleTest {

    @Test
    public void testWithoutModel() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties properties = new Properties();
        properties.put(CommunicationFactory.COMMUNICATION, "HTTP");
        properties.put(HttpSender.SENDER_URL, "http://localhost:8083/event");
        properties.put(HttpSender.SENDER_USERNAME, "admin");
        properties.put(HttpSender.SENDER_PASSWORD, "admin");

        // Create client config
        Settings config = new Settings();
        config.setProperties(properties);

        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);

        Path processPath = new Path("PROCESSES", "testWithoutModel");

        //Creates an empty process model
        ProcessModel process = njams.createProcess(processPath);

        // Start client and flush resources
        njams.start();

        // Create a Log Message???
        Job job = process.createJob("myJobId");
        job.start();

        //Create activitys
        Activity a = job.createActivity("a").setExecution(LocalDateTime.now()).build();
        a.processInput("testdata");
        a.processOutput("testdata");
        assertThat(a, notNullValue());
        assertThat(a.getInstanceId(), notNullValue());
        assertThat(a.getModelId(), is("a"));

        //step to b
        Activity b = a.stepTo("b").setExecution(LocalDateTime.now()).build();
        b.processInput("testdata");
        b.processOutput("testdata");
        assertThat(b, notNullValue());
        assertThat(b.getInstanceId(), notNullValue());
        assertThat(b.getModelId(), is("b"));

        ActivityImpl bExt = (ActivityImpl) b;
        assertThat(bExt.getPredecessors().size(), is(1));
        assertThat(bExt.getPredecessors().get(0).getFromInstanceId(), is(a.getInstanceId()));
        assertThat(bExt.getPredecessors().get(0).getModelId(), is(a.getModelId() + "::" + b.getModelId()));

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }

    @Test
    public void testWithModel() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(CommunicationFactory.COMMUNICATION, "JMS");
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
        assertThat(startModel, notNullValue());
        assertThat(startModel.getId(), is("start"));
        assertThat(startModel.getName(), is("Start"));

        //step
        ActivityModel logModel = startModel.transitionTo("log", "Log", "stepType");
        assertThat(logModel, notNullValue());
        assertThat(logModel.getId(), is("log"));
        assertThat(logModel.getName(), is("Log"));
        assertThat(logModel.getIncomingTransitionFrom("start"), notNullValue());
        assertThat(logModel.getIncomingTransitionFrom("start").getId(), is("start::log"));
        assertThat(logModel.getIncomingTransitionFrom("start").getFromActivity(), is(startModel));
        assertThat(logModel.getIncomingTransitionFrom("start").getFromActivity().getId(), is("start"));
        assertThat(logModel.getIncomingTransitionFrom("start").getToActivity(), is(logModel));
        assertThat(logModel.getIncomingTransitionFrom("start").getToActivity().getId(), is("log"));

        //step
        ActivityModel endModel = logModel.transitionTo("end", "End", "endType");
        assertThat(endModel, notNullValue());
        assertThat(endModel.getId(), is("end"));
        assertThat(endModel.getName(), is("End"));
        assertThat(endModel.getIncomingTransitionFrom("log"), notNullValue());
        assertThat(endModel.getIncomingTransitionFrom("log").getId(), is("log::end"));
        assertThat(endModel.getIncomingTransitionFrom("log").getFromActivity(), is(logModel));

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
        assertThat(start.getModelId(), is("start"));
        assertThat(start.getSequence(), is(1L));
        assertThat(start.getInstanceId(), is("start$1"));

        Activity log = start.stepTo(logModel).build();
        log.processInput("testdata");
        log.processOutput("testdata");
        assertThat(log.getModelId(), is("log"));
        assertThat(log.getSequence(), is(2L));
        assertThat(log.getInstanceId(), is("log$2"));

        Activity end = log.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");
        assertThat(end.getModelId(), is("end"));
        assertThat(end.getSequence(), is(3L));
        assertThat(end.getInstanceId(), is("end$3"));

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }

    @Test
    public void testWithModelWithBranches() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(CommunicationFactory.COMMUNICATION, "JMS");
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
        assertThat(startModel, notNullValue());
        assertThat(startModel.getId(), is("start"));
        assertThat(startModel.getName(), is("Start"));

        //step to log
        ActivityModel logModel = startModel.transitionTo("log", "Log", "stepType");
        assertThat(logModel, notNullValue());
        assertThat(logModel.getId(), is("log"));
        assertThat(logModel.getName(), is("Log"));
        assertThat(logModel.getIncomingTransitionFrom("start"), notNullValue());
        assertThat(logModel.getIncomingTransitionFrom("start").getId(), is("start::log"));
        assertThat(logModel.getIncomingTransitionFrom("start").getFromActivity(), is(startModel));
        assertThat(logModel.getIncomingTransitionFrom("start").getFromActivity().getId(), is("start"));
        assertThat(logModel.getIncomingTransitionFrom("start").getToActivity(), is(logModel));
        assertThat(logModel.getIncomingTransitionFrom("start").getToActivity().getId(), is("log"));

        //step to end
        ActivityModel endModel = logModel.transitionTo("end", "End", "endType");
        assertThat(endModel, notNullValue());
        assertThat(endModel.getId(), is("end"));
        assertThat(endModel.getName(), is("End"));
        assertThat(endModel.getIncomingTransitionFrom("log"), notNullValue());
        assertThat(endModel.getIncomingTransitionFrom("log").getId(), is("log::end"));
        assertThat(endModel.getIncomingTransitionFrom("log").getFromActivity(), is(logModel));

        //step to null
        ActivityModel nullModel = startModel.transitionTo("null", "Null", "nullType");
        assertThat(nullModel, notNullValue());
        assertThat(nullModel.getId(), is("null"));
        assertThat(nullModel.getName(), is("Null"));
        assertThat(nullModel.getIncomingTransitionFrom("start"), notNullValue());
        assertThat(nullModel.getIncomingTransitionFrom("start").getId(), is("start::null"));
        assertThat(nullModel.getIncomingTransitionFrom("start").getFromActivity(), is(startModel));
        assertThat(nullModel.getIncomingTransitionFrom("start").getFromActivity().getId(), is("start"));
        assertThat(nullModel.getIncomingTransitionFrom("start").getToActivity(), is(nullModel));
        assertThat(nullModel.getIncomingTransitionFrom("start").getToActivity().getId(), is("null"));

        //step to null
        endModel = nullModel.transitionTo("end", "End", "endType");
        assertThat(endModel, notNullValue());
        assertThat(endModel.getId(), is("end"));
        assertThat(endModel.getName(), is("End"));
        assertThat(endModel.getIncomingTransitionFrom("null"), notNullValue());
        assertThat(endModel.getIncomingTransitionFrom("null").getId(), is("null::end"));
        assertThat(endModel.getIncomingTransitionFrom("null").getFromActivity(), is(nullModel));

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
        assertThat(start.getModelId(), is("start"));
        assertThat(start.getSequence(), is(1L));
        assertThat(start.getInstanceId(), is("start$1"));

        Activity log = start.stepTo(logModel).build();
        log.processInput("testdata");
        log.processOutput("testdata");
        assertThat(log.getModelId(), is("log"));
        assertThat(log.getSequence(), is(2L));
        assertThat(log.getInstanceId(), is("log$2"));

        Activity end = log.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");
        assertThat(end.getModelId(), is("end"));
        assertThat(end.getSequence(), is(3L));
        assertThat(end.getInstanceId(), is("end$3"));

        Activity nullA = start.stepTo(nullModel).build();
        nullA.processInput("testdata");
        nullA.processOutput("testdata");
        assertThat(nullA.getModelId(), is("null"));
        assertThat(nullA.getSequence(), is(4L));
        assertThat(nullA.getInstanceId(), is("null$4"));

        end = nullA.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");
        assertThat(end.getModelId(), is("end"));
        assertThat(end.getSequence(), is(3L));
        assertThat(end.getInstanceId(), is("end$3"));

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }

    @Test
    public void testGroupWithoutModel() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(CommunicationFactory.COMMUNICATION, "JMS");
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

        //Creates an empty process model
        ProcessModel process = njams.createProcess(processPath);

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
        assertThat(child1Impl.getParent(), is(group));
        assertThat(child1Impl.getIteration(), is(1L));

        Activity child2 = child1.stepTo("child2").build();
        child2.processInput("testdata");
        child2.processOutput("testdata");
        ActivityImpl child2Impl = (ActivityImpl) child2;
        assertThat(child2Impl.getParent(), is(group));
        assertThat(child1Impl.getIteration(), is(1L));

        group.iterate();

        Activity child1_2 = group.createChildActivity("child1").build();
        child1_2.processInput("testdata");
        child1_2.processOutput("testdata");
        ActivityImpl child1_2Impl = (ActivityImpl) child1_2;
        assertThat(child1_2Impl.getParent(), is(group));
        assertThat(child1_2Impl.getIteration(), is(2L));

        Activity child2_2 = child1_2.stepTo("child2").build();
        child2_2.processInput("testdata");
        child2_2.processOutput("testdata");
        ActivityImpl child2_2Impl = (ActivityImpl) child2_2;
        assertThat(child2_2Impl.getParent(), is(group));
        assertThat(child2_2Impl.getIteration(), is(2L));

        Group parent = child2_2.getParent();
        assertThat(parent, is(group));

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
        communicationProperties.put(CommunicationFactory.COMMUNICATION, "JMS");
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
        assertThat(startModel.getParent(), nullValue());

        GroupModel groupModel = startModel.transitionToGroup("group", "Group", "groupType");
        assertThat(groupModel.getParent(), nullValue());
        assertThat(groupModel.getChildActivities().size(), is(0));

        ActivityModel groupStartModel = groupModel.createChildActivity("groupStart", "GroupStart", "stepType");
        groupStartModel.setStarter(true);
        assertThat(groupStartModel.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(1));

        ActivityModel upperModel = groupStartModel.transitionTo("upper", "Upper", "stepType");
        assertThat(upperModel.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(2));

        ActivityModel groupEndModel = upperModel.transitionTo("groupEnd", "GroupEnd", "stepType");
        assertThat(groupEndModel.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(3));

        ActivityModel lowerModel = groupStartModel.transitionTo("lower", "Lower", "stepType");
        assertThat(lowerModel.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(4));

        ActivityModel groupEndModel2 = lowerModel.transitionTo("groupEnd", "GroupEnd", "stepType");
        assertThat(groupEndModel2, is(groupEndModel));
        assertThat(groupEndModel2.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(4));

        GroupModel parentModel = groupEndModel.getParent();
        assertThat(parentModel.getParent(), nullValue());
        assertThat(parentModel, is(groupModel));

        ActivityModel endModel = groupEndModel.getParent().transitionTo("end", "End", "endType");
        assertThat(endModel.getParent(), nullValue());

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
        assertThat(groupStartImpl.getParent(), is(group));
        assertThat(groupStartImpl.getIteration(), is(1L));

        Activity upper = groupStart.stepTo(upperModel).build();
        upper.processInput("testdata");
        upper.processOutput("testdata");
        ActivityImpl upperImpl = (ActivityImpl) upper;
        assertThat(upperImpl.getParent(), is(group));
        assertThat(upperImpl.getIteration(), is(1L));

        Activity groupEnd = upper.stepTo(groupEndModel).build();
        groupEnd.processInput("testdata");
        groupEnd.processOutput("testdata");
        ActivityImpl groupEndImpl = (ActivityImpl) groupEnd;
        assertThat(groupEndImpl.getParent(), is(group));
        assertThat(groupEndImpl.getIteration(), is(1L));

        group.iterate();

        Activity groupStart_2 = group.createChildActivity(groupStartModel).build();
        groupStart_2.processInput("testdata");
        groupStart_2.processOutput("testdata");
        ActivityImpl groupStartImpl2 = (ActivityImpl) groupStart_2;
        assertThat(groupStartImpl2.getParent(), is(group));
        assertThat(groupStartImpl2.getIteration(), is(2L));

        Activity lower = groupStart_2.stepTo(lowerModel).build();
        lower.processInput("testdata");
        lower.processOutput("testdata");
        ActivityImpl lowerImpl = (ActivityImpl) lower;
        assertThat(lowerImpl.getParent(), is(group));
        assertThat(lowerImpl.getIteration(), is(2L));

        Activity groupEnd_2 = lower.stepTo(groupEndModel).build();
        groupEnd_2.processInput("testdata");
        groupEnd_2.processOutput("testdata");
        ActivityImpl groupEndImpl_2 = (ActivityImpl) groupEnd_2;
        assertThat(groupEndImpl_2.getParent(), is(group));
        assertThat(groupEndImpl_2.getIteration(), is(2L));

        Group parent = groupEnd_2.getParent();
        assertThat(parent, is(group));

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
        communicationProperties.put(CommunicationFactory.COMMUNICATION, "JMS");
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
        assertThat(startModel.getParent(), nullValue());

        GroupModel groupModel = startModel.transitionToGroup("group", "Group", "groupType");
        assertThat(groupModel.getParent(), nullValue());
        assertThat(groupModel.getChildActivities().size(), is(0));

        ActivityModel child1Model = groupModel.createChildActivity("child1", "Child1", "stepType");
        child1Model.setStarter(true);
        assertThat(child1Model.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(1));

        GroupModel subgroupModel = child1Model.transitionToGroup("subgroup", "SubGroup", "subgroupType");
        assertThat(subgroupModel.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(2));

        ActivityModel subchild1Model = subgroupModel.createChildActivity("subchild1", "SubChild1", "stepType");
        subchild1Model.setStarter(true);
        assertThat(subchild1Model.getParent(), is(subgroupModel));
        assertThat(subgroupModel.getChildActivities().size(), is(1));

        ActivityModel subchild2Model = subchild1Model.transitionTo("subchild2", "SubChild2", "stepType");
        assertThat(subchild2Model.getParent(), is(subgroupModel));
        assertThat(subgroupModel.getChildActivities().size(), is(2));

        ActivityModel child2Model = subchild2Model.getParent().transitionTo("child2", "Child2", "stepType");
        assertThat(child2Model.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(3));

        GroupModel parentModel = child2Model.getParent();
        assertThat(parentModel.getParent(), nullValue());
        assertThat(parentModel, is(groupModel));

        ActivityModel endModel = child2Model.getParent().transitionTo("end", "End", "endType");
        assertThat(endModel.getParent(), nullValue());

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
        assertThat(child1Impl.getParent(), is(group));
        assertThat(child1Impl.getIteration(), is(1L));

        Group subGroup = child1.stepToGroup(subgroupModel).build();
        subGroup.processInput("testdata");
        subGroup.processOutput("testdata");
        GroupImpl groupImpl = (GroupImpl) subGroup;
        assertThat(groupImpl.getParent(), is(group));
        assertThat(groupImpl.getIteration(), is(1L));

        //subchild
        Activity subChild1 = subGroup.createChildActivity(subchild1Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");
        ActivityImpl subChild1Impl = (ActivityImpl) subChild1;
        assertThat(subChild1Impl.getParent(), is(subGroup));
        assertThat(subChild1Impl.getIteration(), is(1L));

        Activity subChild2 = subChild1.stepTo(subchild2Model).build();
        subChild2.processInput("testdata");
        subChild2.processOutput("testdata");
        ActivityImpl subChild2Impl = (ActivityImpl) subChild2;
        assertThat(subChild2Impl.getParent(), is(subGroup));
        assertThat(subChild2Impl.getIteration(), is(1L));

        Activity child2 = subChild1.getParent().stepTo(child2Model).build();
        child2.processInput("testdata");
        child2.processOutput("testdata");
        ActivityImpl child2Impl = (ActivityImpl) child2;
        assertThat(child2Impl.getParent(), is(group));
        assertThat(child1Impl.getIteration(), is(1L));

        group.iterate();

        //iteration 2
        Activity child1_2 = group.createChildActivity(child1Model).build();
        child1_2.processInput("testdata");
        child1_2.processOutput("testdata");
        ActivityImpl child1_2Impl = (ActivityImpl) child1_2;
        assertThat(child1_2Impl.getParent(), is(group));
        assertThat(child1_2Impl.getIteration(), is(2L));

        Group subGroup_2 = child1_2.stepToGroup(subgroupModel).build();
        subGroup_2.processInput("testdata");
        subGroup_2.processOutput("testdata");
        GroupImpl groupImpl_2 = (GroupImpl) subGroup_2;
        assertThat(groupImpl_2.getParent(), is(group));
        assertThat(groupImpl_2.getIteration(), is(2L));

        //subchild
        Activity subChild1_2 = subGroup_2.createChildActivity(subchild1Model).build();
        subChild1_2.processInput("testdata");
        subChild1_2.processOutput("testdata");
        ActivityImpl subChild1Impl_2 = (ActivityImpl) subChild1_2;
        assertThat(subChild1Impl_2.getParent(), is(subGroup_2));
        assertThat(subChild1Impl_2.getIteration(), is(1L));

        Activity subChild2_2 = subChild1_2.stepTo(subchild2Model).build();
        subChild2_2.processInput("testdata");
        subChild2_2.processOutput("testdata");
        ActivityImpl subChild2Impl_2 = (ActivityImpl) subChild2_2;
        assertThat(subChild2Impl_2.getParent(), is(subGroup_2));
        assertThat(subChild2Impl_2.getIteration(), is(1L));

        Activity child2_2 = subChild2_2.getParent().stepTo(child2Model).build();
        child2_2.processInput("testdata");
        child2_2.processOutput("testdata");
        ActivityImpl child2_2Impl = (ActivityImpl) child2_2;
        assertThat(child2_2Impl.getParent(), is(group));
        assertThat(child2_2Impl.getIteration(), is(2L));

        Group parent = child2_2.getParent();
        assertThat(parent, is(group));

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
        communicationProperties.put(CommunicationFactory.COMMUNICATION, "JMS");
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
        SubProcessActivityModel subProcessModel
                = startModel.transitionToSubProcess("subProcess", "SubProcess", "stepType");

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
        communicationProperties.put(CommunicationFactory.COMMUNICATION, "JMS");
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
        assertThat(startModel.getParent(), nullValue());

        GroupModel groupModel = startModel.transitionToGroup("group", "Group", "groupType");
        assertThat(groupModel.getParent(), nullValue());
        assertThat(groupModel.getChildActivities().size(), is(0));

        ActivityModel child1Model = groupModel.createChildActivity("child1", "Child1", "stepType");
        child1Model.setStarter(true);
        assertThat(child1Model.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(1));

        GroupModel subgroupModel = child1Model.transitionToGroup("subgroup", "SubGroup", "subgroupType");
        assertThat(subgroupModel.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(2));

        ActivityModel subchild1Model = subgroupModel.createChildActivity("subchild1", "SubChild1", "stepType");
        subchild1Model.setStarter(true);
        assertThat(subchild1Model.getParent(), is(subgroupModel));
        assertThat(subgroupModel.getChildActivities().size(), is(1));

        ActivityModel subchild2Model = subchild1Model.transitionTo("subchild2", "SubChild2", "stepType");
        assertThat(subchild2Model.getParent(), is(subgroupModel));
        assertThat(subgroupModel.getChildActivities().size(), is(2));

        ActivityModel child2Model = subchild2Model.getParent().transitionTo("child2", "Child2", "stepType");
        assertThat(child2Model.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(3));

        GroupModel parentModel = child2Model.getParent();
        assertThat(parentModel.getParent(), nullValue());
        assertThat(parentModel, is(groupModel));

        ActivityModel endModel = child2Model.getParent().transitionTo("end", "End", "endType");
        assertThat(endModel.getParent(), nullValue());

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
        assertThat(job.getActivities().size(), is(1));

        //step to group
        Group group = a.stepToGroup(groupModel).setExecution(LocalDateTime.now()).build();
        group.processInput("testdata");
        group.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(1));

        //iteration 1
        Activity child1 = group.createChildActivity(child1Model).build();
        child1.processInput("testdata");
        child1.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(2));

        Group subGroup = child1.stepToGroup(subgroupModel).build();
        subGroup.processInput("testdata");
        subGroup.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(2));

        //subchild
        Activity subChild1 = subGroup.createChildActivity(subchild1Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(3));

        subChild1.stepTo(subchild2Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(3));

        subChild1.getParent().stepTo(child2Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(2));

        group.iterate();

        //iteration 2
        Activity child1_2 = group.createChildActivity(child1Model).build();
        child1_2.processInput("testdata");
        child1_2.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(3));

        Group subGroup_2 = child1_2.stepToGroup(subgroupModel).build();
        subGroup_2.processInput("testdata");
        subGroup_2.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(3));

        //subchild
        Activity subChild1_2 = subGroup_2.createChildActivity(subchild1Model).build();
        subChild1_2.processInput("testdata");
        subChild1_2.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(4));

        Activity subChild2_2 = subChild1_2.stepTo(subchild2Model).build();
        subChild2_2.processInput("testdata");
        subChild2_2.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(4));

        Activity child2_2 = subChild2_2.getParent().stepTo(child2Model).build();
        child2_2.processInput("testdata");
        child2_2.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(3));

        Group parent = child2_2.getParent();

        Activity end = parent.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(1));

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }

    @Test
    public void testSubprocessSpawned() throws Exception {
        Path clientPath = new Path("SDK4", "TEST");

        Properties communicationProperties = new Properties();
        communicationProperties.put(CommunicationFactory.COMMUNICATION, "JMS");
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
        SubProcessActivityModel subProcessModel
                = startModel.transitionToSubProcess("subProcess", "SubProcess", "stepType");
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
        communicationProperties.put(CommunicationFactory.COMMUNICATION, Transformer.encode("JMS"));
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
        assertThat(startModel.getParent(), nullValue());

        GroupModel groupModel = startModel.transitionToGroup("group", "Group", "groupType");
        assertThat(groupModel.getParent(), nullValue());
        assertThat(groupModel.getChildActivities().size(), is(0));

        ActivityModel child1Model = groupModel.createChildActivity("child1", "Child1", "stepType");
        child1Model.setStarter(true);
        assertThat(child1Model.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(1));

        GroupModel subgroupModel = child1Model.transitionToGroup("subgroup", "SubGroup", "subgroupType");
        assertThat(subgroupModel.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(2));

        ActivityModel subchild1Model = subgroupModel.createChildActivity("subchild1", "SubChild1", "stepType");
        subchild1Model.setStarter(true);
        assertThat(subchild1Model.getParent(), is(subgroupModel));
        assertThat(subgroupModel.getChildActivities().size(), is(1));

        ActivityModel subchild2Model = subchild1Model.transitionTo("subchild2", "SubChild2", "stepType");
        assertThat(subchild2Model.getParent(), is(subgroupModel));
        assertThat(subgroupModel.getChildActivities().size(), is(2));

        ActivityModel child2Model = subchild2Model.getParent().transitionTo("child2", "Child2", "stepType");
        assertThat(child2Model.getParent(), is(groupModel));
        assertThat(groupModel.getChildActivities().size(), is(3));

        GroupModel parentModel = child2Model.getParent();
        assertThat(parentModel.getParent(), nullValue());
        assertThat(parentModel, is(groupModel));

        ActivityModel endModel = child2Model.getParent().transitionTo("end", "End", "endType");
        assertThat(endModel.getParent(), nullValue());

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
        assertThat(job.getActivities().size(), is(1));

        //step to group
        Group group = a.stepToGroup(groupModel).setExecution(LocalDateTime.now()).build();
        group.processInput("testdata");
        group.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(1));

        //iteration 1
        Activity child1 = group.createChildActivity(child1Model).build();
        child1.processInput("testdata");
        child1.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(2));

        Group subGroup = child1.stepToGroup(subgroupModel).build();
        subGroup.processInput("testdata");
        subGroup.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(2));

        //subchild
        Activity subChild1 = subGroup.createChildActivity(subchild1Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(3));

        subChild1.stepTo(subchild2Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(3));

        subChild1.getParent().stepTo(child2Model).build();
        subChild1.processInput("testdata");
        subChild1.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(2));

        group.iterate();

        //iteration 2
        Activity child1_2 = group.createChildActivity(child1Model).build();
        child1_2.processInput("testdata");
        child1_2.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(3));

        Group subGroup_2 = child1_2.stepToGroup(subgroupModel).build();
        subGroup_2.processInput("testdata");
        subGroup_2.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(3));

        //subchild
        Activity subChild1_2 = subGroup_2.createChildActivity(subchild1Model).build();
        subChild1_2.processInput("testdata");
        subChild1_2.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(4));

        Activity subChild2_2 = subChild1_2.stepTo(subchild2Model).build();
        subChild2_2.processInput("testdata");
        subChild2_2.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(4));

        Activity child2_2 = subChild2_2.getParent().stepTo(child2Model).build();
        child2_2.processInput("testdata");
        child2_2.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(3));

        Group parent = child2_2.getParent();

        Activity end = parent.stepTo(endModel).build();
        end.processInput("testdata");
        end.processOutput("testdata");

        ((JobImpl) job).flush();
        assertThat(job.getActivities().size(), is(1));

        job.end();

        Thread.sleep(1000);

        // If you are finished with processing or the application goes down, stop client...
        njams.stop();

    }
}
