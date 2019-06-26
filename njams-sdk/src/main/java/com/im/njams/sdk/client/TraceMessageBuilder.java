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

import com.faizsiegeln.njams.messageformat.v4.tracemessage.Activity;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.ProcessModel;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class helps to build a TraceMessage
 *
 * @author krautenberg
 * @version 4.0.6
 */
public class TraceMessageBuilder {

    private String clientVersion;

    private String sdkVersion;

    private String category;

    private String path;

    private Map<String, List<Activity>> processesToSend;



    public TraceMessageBuilder() {
        this.processesToSend = new HashMap<>();
    }

    /**
     * This method builds a TraceMessage, if there is atleast one TracePoint to delete.
     *
     * @return A TraceMessage with all Processes that have Activities, whose TracePoints will be deleted, if
     * there are any, otherwise null.
     */
    public TraceMessage build() {
        TraceMessage traceMessageToBuild = new TraceMessage();
        //Set CommonMessage fields
        for (String processPath : processesToSend.keySet()) {
            ProcessModel processModel = new ProcessModel();
            processModel.setProcessPath(processPath);
            processModel.setActivities(processesToSend.get(processPath));
            traceMessageToBuild.addProcess(processModel);
        }
        traceMessageToBuild.setClientVersion(clientVersion);
        traceMessageToBuild.setSdkVersion(sdkVersion);
        traceMessageToBuild.setCategory(category);
        traceMessageToBuild.setPath(path);
        clearAll();
        return traceMessageToBuild;
    }

    private void clearAll(){
        clientVersion = null;
        sdkVersion = null;
        category = null;
        path = null;
        processesToSend.clear();
    }

    /**
     * This method adds an activity with it's Tracepoint to the List of Activities, that
     * will be sent in the next TraceMessage.
     *
     * @param processPath the ProcessPath of the Process where the activity belongs to
     * @param act         the Activity, whose TracePoint is expired.
     */
    public TraceMessageBuilder addActivity(String processPath, Activity act) {
        List<Activity> activities = processesToSend.get(processPath);
        if (activities == null) {
            activities = new ArrayList<>();
            processesToSend.put(processPath, activities);
        }
        activities.add(act);
        return this;
    }

    public boolean isEmpty() {
        boolean isNothingSet = processesToSend.isEmpty() && clientVersion == null && sdkVersion == null && path == null && category == null;
        return isNothingSet;
    }

    public TraceMessageBuilder setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
        return this;
    }

    public TraceMessageBuilder setSdkVersion(String sdkVersion) {
        this.sdkVersion = sdkVersion;
        return this;
    }

    public TraceMessageBuilder setCategory(String category) {
        this.category = category;
        return this;
    }

    public TraceMessageBuilder setPath(String path) {
        this.path = path;
        return this;
    }
}
