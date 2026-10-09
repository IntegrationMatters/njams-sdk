/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
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
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.logmessage;

import java.util.List;

import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.SubProcessActivityModel;

/**
 * Activity which represents a group in a process. A group can have child
 * activities.
 * <p>
 * <b>Thread safety.</b> Like any {@link Activity}, a {@code Group} instance is <b>thread-confined</b>:
 * it is expected to be accessed by a single thread, and the SDK does not synchronize mutation of an
 * individual instance — including {@link #iterate()} and child-activity creation on this group. A
 * caller that genuinely shares one group instance across threads (for example, iterating it from
 * several threads at once) must synchronize those calls itself. The enclosing {@link Job} remains
 * safe for concurrent activity creation; see {@link Job} for the overall contract.
 *
 * @author pnientiedt
 */
public interface Group extends Activity {

    /**
     * Creates a builder for a child activity of this group in the group's current iteration. If the
     * given model is a {@link GroupModel} or {@link SubProcessActivityModel}, this delegates to
     * {@link #createChildGroup(GroupModel)} or {@link #createChildSubProcess(SubProcessActivityModel)}.
     * <p>
     * <b>Note:</b> this method does not necessarily create a new instance. If the job already holds an
     * activity of the same model at this group's nesting level and iteration, the returned builder wraps
     * that existing instance: it is moved into this group, and everything set through the builder
     * changes the existing activity. Use {@link #newChildActivity(ActivityModel)} to always get a new
     * instance.
     *
     * @param childActivityModel the model of the child activity
     * @return a builder for the new or reused child activity
     */
    public ActivityBuilder createChildActivity(ActivityModel childActivityModel);

    /**
     * Creates a builder for a child group of this group in the group's current iteration.
     * <p>
     * <b>Note:</b> this method does not necessarily create a new instance. If the job already holds a
     * group of the same model at this group's nesting level and iteration, the returned builder wraps
     * that existing instance: it is moved into this group, and everything set through the builder
     * changes the existing group. Use {@link #newChildActivity(ActivityModel)} to always get a new
     * instance.
     *
     * @param childGroupModel the model of the child group
     * @return a builder for the new or reused child group
     */
    public GroupBuilder createChildGroup(GroupModel childGroupModel);

    /**
     * Creates a builder for a child sub-process activity of this group in the group's current iteration.
     * <p>
     * <b>Note:</b> this method does not necessarily create a new instance. If the job already holds a
     * sub-process activity of the same model at this group's nesting level and iteration, the returned
     * builder wraps that existing instance: it is moved into this group, and everything set through the
     * builder changes the existing activity. Use {@link #newChildActivity(ActivityModel)} to always get
     * a new instance.
     *
     * @param childSubProcessModel the model of the child sub-process activity
     * @return a builder for the new or reused child sub-process activity
     */
    public ActivityBuilder createChildSubProcess(SubProcessActivityModel childSubProcessModel);

    /**
     * Increase the iteration counter of the group. All activities added
     * afterwards will be added to a new iteration
     * @return The current (new) iteration
     */
    public long iterate();

    /**
     * return all child activities in an unmodifiable list
     *
     * @return all child activities
     */
    public List<Activity> getChildActivities();

    /**
     * Creates a builder for a new child activity of this group in the group's current iteration.
     * Unlike {@link #createChildActivity(ActivityModel)}, this never reuses an existing activity
     * instance: each call results in a separate activity once the builder is built. If the given model
     * is a {@link GroupModel} or {@link SubProcessActivityModel}, the returned builder is a
     * {@link GroupBuilder} or {@link SubProcessActivityBuilder}.
     *
     * @param childActivityModel the model of the child activity
     * @return a builder for a new child activity
     * @since 6.1.0
     */
    public ActivityBuilder newChildActivity(ActivityModel childActivityModel);
}
