package com.im.njams.sdk.model.layout;

import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CommonBfsModelLayouterTest {

    private CommonBfsModelLayouter layouter;

    @Before
    public void setUp() {
        layouter = new CommonBfsModelLayouter();
    }

    private ProcessModel createProcess() {
        com.im.njams.sdk.settings.Settings settings = com.im.njams.sdk.communication.TestSender.getSettings();
        com.im.njams.sdk.Njams njams = new com.im.njams.sdk.Njams(
            com.im.njams.sdk.Path.of("TEST"), "1.0", "TEST", settings);
        return njams.processes().create("proc");
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

        int expectedMinWidth = CommonBfsModelLayouter.ACTIVITY_SIZE + 2 * CommonBfsModelLayouter.GROUP_PADDING;
        assertTrue("group width >= activity + padding",
            group.getWidth() >= expectedMinWidth);
        int expectedMinHeight = CommonBfsModelLayouter.ACTIVITY_SIZE
            + CommonBfsModelLayouter.GROUP_HEADER_HEIGHT + 2 * CommonBfsModelLayouter.GROUP_PADDING;
        assertTrue("group height >= activity + header + padding",
            group.getHeight() >= expectedMinHeight);
    }

    @Test
    public void group_widthIncludesHorizontalMarginOnBothSides() {
        ProcessModel model = createProcess();
        GroupModel group = model.createGroup("G", "G", null);
        ActivityModel child = group.createChildActivity("ch", "ch", null);
        child.setStarter(true);
        group.addStartActivity(child);
        group.setStarter(true);

        layouter.layout(model);

        int expectedMinWidth = CommonBfsModelLayouter.ACTIVITY_SIZE
            + 2 * CommonBfsModelLayouter.GROUP_PADDING
            + 2 * CommonBfsModelLayouter.GROUP_MARGIN_HORIZONTAL;
        assertTrue("group width must include horizontal margins on both sides",
            group.getWidth() >= expectedMinWidth);
    }

    @Test
    public void group_heightIncludesBottomMargin() {
        ProcessModel model = createProcess();
        GroupModel group = model.createGroup("G", "G", null);
        ActivityModel child = group.createChildActivity("ch", "ch", null);
        child.setStarter(true);
        group.addStartActivity(child);
        group.setStarter(true);

        layouter.layout(model);

        int expectedMinHeight = CommonBfsModelLayouter.ACTIVITY_SIZE
            + CommonBfsModelLayouter.GROUP_HEADER_HEIGHT
            + 2 * CommonBfsModelLayouter.GROUP_PADDING
            + CommonBfsModelLayouter.GROUP_MARGIN_BOTTOM;
        assertTrue("group height must include bottom margin",
            group.getHeight() >= expectedMinHeight);
    }

    @Test
    public void chainAfterBranch_staysOnSameRow() {
        // Start → A (row 0) and Start → B → C → D (row 1)
        // B, C, D must all have the same Y — the chain must not fall back to row 0
        ProcessModel model = createProcess();
        ActivityModel start = model.createActivity("Start", "Start", null);
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        ActivityModel c = model.createActivity("C", "C", null);
        ActivityModel d = model.createActivity("D", "D", null);
        start.setStarter(true);
        model.createTransition("Start", "A");
        model.createTransition("Start", "B");
        model.createTransition("B", "C");
        model.createTransition("C", "D");

        layouter.layout(model);

        assertEquals("B and C must share the same row", b.getY(), c.getY());
        assertEquals("C and D must share the same row", c.getY(), d.getY());
        assertNotEquals("A and B must be on different rows", a.getY(), b.getY());
    }

    @Test
    public void branch_prefersAboveWhenSpaceAvailable() {
        // Start → A (row 0), Start → B (row 1). B → C (row 1 continuation), B → D (branch).
        // D must land above C (lower Y) because B is on row 1 and row 0 is free above it.
        ProcessModel model = createProcess();
        ActivityModel start = model.createActivity("Start", "Start", null);
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        ActivityModel c = model.createActivity("C", "C", null);
        ActivityModel d = model.createActivity("D", "D", null);
        start.setStarter(true);
        model.createTransition("Start", "A");
        model.createTransition("Start", "B");
        model.createTransition("B", "C");
        model.createTransition("B", "D");

        layouter.layout(model);

        assertTrue("D must be above C (lower Y) since B is on row 1 and row 0 is free",
            d.getY() < c.getY());
    }

    @Test
    public void group_childrenStartPastHorizontalMargin() {
        ProcessModel model = createProcess();
        GroupModel group = model.createGroup("G", "G", null);
        ActivityModel child = group.createChildActivity("ch", "ch", null);
        child.setStarter(true);
        group.addStartActivity(child);
        group.setStarter(true);

        layouter.layout(model);

        int expectedMinChildX = group.getX() + CommonBfsModelLayouter.GROUP_PADDING
            + CommonBfsModelLayouter.GROUP_MARGIN_HORIZONTAL;
        assertTrue("child x must be at least padding + horizontal margin from group left edge",
            child.getX() >= expectedMinChildX);
    }
}
