/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.client;

import com.faizsiegeln.njams.messageformat.v4.tracemessage.*;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class helps to build a TraceMessage
 */
public class TraceMessageBuilder {

    private final NjamsMetadata njamsMetadata;
    private Map<String, List<Activity>> processes = new HashMap<>();

    public TraceMessageBuilder(NjamsMetadata njamsMetadata){
        this.njamsMetadata = njamsMetadata;
    }

    /**
     * This method builds a TraceMessage, if there is atleast one TracePoint to delete.
     *
     * @return A TraceMessage with all Processes that have Activities, whose TracePoints will be deleted, if
     * there are any, otherwise null.
     */
    public TraceMessage build() {
        TraceMessage msg = null;
        if(!processes.isEmpty()){
            msg = new TraceMessage();
            //Set CommonMessage fields
            msg.setClientVersion(njamsMetadata.getClientVersion());
            msg.setSdkVersion(njamsMetadata.getSdkVersion());
            msg.setCategory(njamsMetadata.getCategory());
            msg.setPath(njamsMetadata.getClientPath().toString());
            for(String processPath : processes.keySet()){
                ProcessModel processModel = new ProcessModel();
                processModel.setProcessPath(processPath);
                processModel.setActivities(processes.get(processPath));
                msg.addProcess(processModel);
            }
            processes.clear();
        }
        return msg;
    }

    /**
     * This method adds an activity with it's Tracepoint to the List of Activities, that
     * will be sent in the next TraceMessage.
     *
     * @param processPath the ProcessPath of the Process where the activity belongs to
     * @param act the Activity, whose TracePoint is expired.
     */
    public void addActivity(String processPath, Activity act) {
        List<Activity> activities = processes.get(processPath);
        if(activities == null){
            activities = new ArrayList<>();
            processes.put(processPath, activities);
        }
        activities.add(act);
    }
}
