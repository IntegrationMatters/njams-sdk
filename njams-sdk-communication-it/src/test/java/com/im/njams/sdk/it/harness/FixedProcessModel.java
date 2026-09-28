package com.im.njams.sdk.it.harness;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

/** The one, deliberately trivial process shape every scenario in this module drives. Never varied per scenario. */
public final class FixedProcessModel {

    public static final String PROCESS_PATH = "CommunicationIT";
    public static final String ACTIVITY_MODEL_ID = "single-activity";

    private FixedProcessModel() {
    }

    public static ProcessModel build(Njams njams) {
        ProcessModel model = njams.model().create(PROCESS_PATH);
        ActivityModel activity = model.createActivity(ACTIVITY_MODEL_ID, "Single Activity", "startType");
        activity.setStarter(true);
        return model;
    }
}
