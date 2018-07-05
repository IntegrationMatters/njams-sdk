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

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.IdUtil;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.logmessage.Activity;
import com.im.njams.sdk.logmessage.ActivityImpl;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.logmessage.JobImpl;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.TransitionModel;
import com.im.njams.sdk.model.image.ImageSupplier;
import com.im.njams.sdk.model.image.URLImageSupplier;

import java.net.MalformedURLException;
import java.net.URL;

public class BW6Sample {

    Njams njams = new Njams(new Path("domain", "application", "appspace", "appNode"), "2.1.0", "bw6", null);

    public void onIconsAdded() throws MalformedURLException {
        njams.addImage("bw6.start", "/images/start.png");
        njams.addImage(new URLImageSupplier("bw6.log", new URL("bundle://xyz/log.png")));
        njams.addImage(new ImageSupplier("bw.end") {

            @Override
            public String getBase64Image() {
                // get from BW...
                return encodeBase64(new byte[0]);
            }
        });
        // send flush????
    }

    // application is deployed into appspace
    // one client per application -> one client per node (JVM)???

    // domain -> appspace -> appnode -> application -> process
    // domain -> appspace -> application -> appnode -> process
    public void onNewProcessDeployed(String node, String process) throws MalformedURLException {

        ProcessModel pm = njams.createProcess(new Path(node, process));
        pm.setSvg("<svg>...</svg>");
        //TODO pm.setStarter(true);

        ActivityModel act = pm.createActivity("Start", "Start", "bw6.start");
        act = pm.createActivity("End", "End", "bw6.end");
        act.setConfig("My config");
        act.setMapping("my Mapping");
        //        act.setSettings("My config", Type.JSON);
        //        act.setInputMapping("my Mapping", Type.XML);

        TransitionModel tran = pm.createTransition("fromId", "toId", IdUtil.getTransitionModelId("from", "to"));
        tran.setName("Hello");

        njams.start();
    }

    public void beforeProcess() {
        ProcessModel model = njams.getProcessModel(new Path("new", "process"));
        Job job = model.createJob("bw-external-id");

        //job.setCorrelationId("") ?????
        //job.setStartTime("") ?????
        job.start();
    }

    public void afterProcess() {
        Job job = njams.getJobById("BW job ID");
        //        job.setStatus(ERROR); ???
        job.end();
    }

    public void beforeActivity() {
        Job job = njams.getJobById("BW job ID");
        Activity a = job.createActivity("Model ID").setActivityStatus(ActivityStatus.RUNNING).build();
        a.processInput("testdata");
    }

    public void afterActivity() {
        Job job = njams.getJobById("BW job ID");
        Activity act = job.getActivityByInstanceId("bw-6 instance id");
        act.setActivityStatus(ActivityStatus.SUCCESS); // implictily marks activity as finished/complete/flush
        act.processOutput("Hello world");
        act.end();
        //act.end()???? flush???

    }

    public void onTransition() {
        Job job = njams.getJobById("BW job ID");
        Activity to = job.getActivityByInstanceId("bw-6 to id");
        if (to == null) {
            to = new ActivityImpl((JobImpl)job);
            ///act.setInstanceId("from to id"); ????
            //act.setModeldId("");???
            job.addActivity(to); // requires instance and model ID
        }

        to.addPredecessor("from", IdUtil.getTransitionModelId("from", "to"));

    }

}
