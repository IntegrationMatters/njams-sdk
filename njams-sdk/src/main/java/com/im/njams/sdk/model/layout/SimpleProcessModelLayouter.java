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
package com.im.njams.sdk.model.layout;

import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SimpleLayouter gets a ProcessModel and transforms positioning and group sizes
 * to an eye friendly Layout.
 *
 * It starts at startActivity and iterates through flow via successor
 * attributes.
 *
 * @author hsiegeln
 *
 */
public class SimpleProcessModelLayouter implements ProcessModelLayouter {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleProcessModelLayouter.class);

    private static final int ACTIVITY_HORIZONTAL_OFFSET = 100;
    private static final int ACTIVITY_VERTICAL_OFFSET = 100;
    private static final int START_HORIZONTAL_OFFSET = 0;
    private static final int START_VERTICAL_OFFSET = 0;

    /**
     * Layouts the given ProcessModel
     *
     * @param processModel processModel
     */
    @Override
    public void layout(ProcessModel processModel) {
        List<ActivityModel> startActivityies = processModel.getStartActivities();

        if (startActivityies != null && startActivityies.size() > 1) {
            LOG.warn("{}: SimpleProcessModelLayouter does not support "
                    + "ProcessModels with more than one start activity", processModel.getPath());
        }

        if (startActivityies != null && !startActivityies.isEmpty()) {
            ActivityModel startActivity = startActivityies.get(0);
            LOG.debug("StartActivity for {} is {}", processModel.getName(), startActivity.getId());
            setPositionOfStartActivity(startActivity);
            List<ActivityModel> successors = startActivity.getSuccessors();
            if (successors == null) {
                return;
            }
            positionSuccessors(startActivity);
        }
    }

    private void positionSuccessors(ActivityModel activity) {
        int yoffset = 0;
        // depth-first algorithm
        List<ActivityModel> successors = activity.getSuccessors();
        for (ActivityModel element : successors) {
            if (activity instanceof GroupModel) {
                element.setX(activity.getX() + ((GroupModel) activity).getWidth() + ACTIVITY_HORIZONTAL_OFFSET / 2);
                element.setY(activity.getY() + yoffset);
                yoffset += ((GroupModel) activity).getHeight() + ACTIVITY_VERTICAL_OFFSET;
                LOG.debug("Group {} position: {}:{}", element.getName(), element.getX(), element.getY());
            } else {
                element.setX(activity.getX() + ACTIVITY_HORIZONTAL_OFFSET);
                element.setY(activity.getY() + yoffset);
                yoffset += ACTIVITY_VERTICAL_OFFSET;
                LOG.debug("Activity {} position: {}:{}", element.getName(), element.getX(), element.getY());
            }
            // next step is a group
            if (element instanceof GroupModel && !((GroupModel) element).getStartActivities().isEmpty()) {
                ((GroupModel) element).setWidth(0);
                ((GroupModel) element).setHeight(0);
                LOG.debug("Reset width/height of group {}", element.getName());
                if (((GroupModel) element).getStartActivities().size() > 1) {
                    LOG.warn("{}: SimpleProcessModelLayouter does not support "
                            + "GroupModels with more than one start activity", element.getId());
                }
                ActivityModel groupStarter = ((GroupModel) element).getStartActivities().get(0);
                LOG.debug("GroupStarter for {} is {}", element.getId(), groupStarter.getId());
                groupStarter.setX(element.getX() + ACTIVITY_HORIZONTAL_OFFSET / 2);
                groupStarter.setY(element.getY() + ACTIVITY_VERTICAL_OFFSET / 2);
                LOG.debug("GroupStarter {} position: {}:{}", groupStarter.getName(), groupStarter.getX(), groupStarter.getY());
                positionSuccessors(groupStarter);
            }
            resizeParent((ActivityModel) element);
        }

        for (ActivityModel element : successors) {
            positionSuccessors(element);
        }

    }

    private void resizeParent(ActivityModel element) {
        LOG.debug("Call resizeParent for {} with {}:{}", element.getName(), element.getX(), element.getY());
        if (element.getParent() == null) {
            // nothing to do
            return;
        }
        GroupModel parent = (GroupModel) element.getParent();

        if (element instanceof GroupModel) {
            parent.setWidth(((GroupModel) element).getWidth() + ACTIVITY_HORIZONTAL_OFFSET);
            parent.setHeight(((GroupModel) element).getHeight() + ACTIVITY_VERTICAL_OFFSET);
            LOG.debug("Group {} size: {}:{}", parent.getName(), parent.getWidth(), parent.getHeight());
        } else {
            if (element.getX() + ACTIVITY_HORIZONTAL_OFFSET > (parent.getX() + parent.getWidth())) {
                parent.setWidth(element.getX() - parent.getX() + ACTIVITY_HORIZONTAL_OFFSET);
                LOG.debug("Group {} width: {}", parent.getName(), parent.getWidth());
            }
            if (element.getY() + ACTIVITY_VERTICAL_OFFSET > (parent.getY() + parent.getHeight())) {
                parent.setHeight(element.getY() - parent.getY() + ACTIVITY_VERTICAL_OFFSET);
                LOG.debug("Group {} height: {}", parent.getName(), parent.getHeight());
            }
        }

        // resize grandparents, etc
        resizeParent(parent);
    }

    private void setPositionOfStartActivity(ActivityModel activity) {
        activity.setX(START_HORIZONTAL_OFFSET);
        activity.setY(START_VERTICAL_OFFSET);
    }

}
