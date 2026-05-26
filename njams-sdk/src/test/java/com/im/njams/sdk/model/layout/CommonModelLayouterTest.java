package com.im.njams.sdk.model.layout;

import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CommonModelLayouterTest {

    private CommonModelLayouter layouter;

    @Before
    public void setUp() {
        layouter = new CommonModelLayouter();
    }

    private ProcessModel createProcess() {
        com.im.njams.sdk.settings.Settings settings = com.im.njams.sdk.communication.TestSender.getSettings();
        com.im.njams.sdk.Njams njams = new com.im.njams.sdk.Njams(
            com.im.njams.sdk.Path.of("TEST"), "1.0", "TEST", settings);
        return njams.createProcess(com.im.njams.sdk.Path.of("proc"));
    }

    @Test
    public void noStartActivity_doesNothing() {
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null); // not marked as starter
        a.setX(999);
        a.setY(999);
        layouter.layout(model);
        assertEquals("x must be unchanged when no start activity", 999, a.getX());
        assertEquals("y must be unchanged when no start activity", 999, a.getY());
    }

    @Test
    public void linearSequence_placesActivitiesInIncreasingX() {
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        ActivityModel c = model.createActivity("C", "C", null);
        a.setStarter(true);
        model.createTransition("A", "B");
        model.createTransition("B", "C");

        layouter.layout(model);

        assertTrue("B must be to the right of A", b.getX() > a.getX());
        assertTrue("C must be to the right of B", c.getX() > b.getX());
        assertEquals("A, B, C must be on the same row", a.getY(), b.getY());
        assertEquals("A, B, C must be on the same row", b.getY(), c.getY());
    }

    @Test
    public void parallelBranches_placedInSameColumnDifferentRows() {
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        ActivityModel c = model.createActivity("C", "C", null);
        a.setStarter(true);
        model.createTransition("A", "B");
        model.createTransition("A", "C");

        layouter.layout(model);

        assertEquals("B and C are in the same column", b.getX(), c.getX());
        assertNotEquals("B and C must be in different rows", b.getY(), c.getY());
        assertTrue("B and C must be to the right of A", b.getX() > a.getX());
    }

    @Test
    public void convergence_mergeNodePlacedBeyondBothPredecessors() {
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        ActivityModel c = model.createActivity("C", "C", null);
        ActivityModel d = model.createActivity("D", "D", null);
        a.setStarter(true);
        model.createTransition("A", "B");
        model.createTransition("A", "C");
        model.createTransition("B", "D");
        model.createTransition("C", "D");

        layouter.layout(model);

        assertTrue("D must be to the right of B", d.getX() > b.getX());
        assertTrue("D must be to the right of C", d.getX() > c.getX());
    }

    @Test
    public void group_sizedToContainChildrenPlusPadding() {
        ProcessModel model = createProcess();
        GroupModel group = model.createGroup("G", "G", null);
        ActivityModel child = group.createChildActivity("ch", "ch", null);
        child.setStarter(true);
        group.addStartActivity(child);
        group.setStarter(true);

        layouter.layout(model);

        int expectedMinWidth = CommonModelLayouter.ACTIVITY_SIZE + 2 * CommonModelLayouter.GROUP_PADDING;
        assertTrue("group width >= activity + padding",
            group.getWidth() >= expectedMinWidth);
        int expectedMinHeight = CommonModelLayouter.ACTIVITY_SIZE
            + CommonModelLayouter.GROUP_HEADER_HEIGHT + 2 * CommonModelLayouter.GROUP_PADDING;
        assertTrue("group height >= activity + header + padding",
            group.getHeight() >= expectedMinHeight);
    }
}
