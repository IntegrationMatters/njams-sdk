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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.model.layout;

import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.TransitionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * General-purpose layouter for nJAMS process models. Coordinates are absolute; groups are sized to
 * contain their children; nested groups recurse correctly. The algorithm runs in two passes: a
 * depth-first sizing pass that computes each group's width/height from its contents bottom-up, and a
 * top-down placement pass that assigns absolute coordinates to every activity using per-column widths
 * (so a wide group forces subsequent siblings past its right edge).
 *
 * <p>This is the default {@link ProcessModelLayouter} used by {@link com.im.njams.sdk.Njams}.
 */
public class CommonModelLayouter implements ProcessModelLayouter {

    private static final Logger LOG = LoggerFactory.getLogger(CommonModelLayouter.class);

    /** Horizontal distance between activity centres in adjacent columns. */
    static final int HORIZONTAL_STEP = 150;
    /** Vertical distance between activity centres in adjacent rows. */
    static final int VERTICAL_STEP = 100;
    /** Padding inside a group between the group border and its children. */
    static final int GROUP_PADDING = 20;
    /** Header band height drawn above the child area of a group. */
    static final int GROUP_HEADER_HEIGHT = 20;
    /** Visual width/height of a plain activity. */
    static final int ACTIVITY_SIZE = 50;

    /**
     * Lays out the given process model by assigning absolute x/y coordinates to all activities
     * and sizing all groups to contain their children.
     *
     * @param processModel the process model to lay out
     */
    @Override
    public void layout(ProcessModel processModel) {
        List<ActivityModel> starters = processModel.getStartActivities();
        if (starters.isEmpty()) {
            LOG.warn("No start activity defined for '{}' — skipping layout", processModel.getPath());
            return;
        }

        List<ActivityModel> roots = rootChildren(processModel);

        // Pass 1 (depth-first): size every group from the innermost out.
        for (ActivityModel a : roots) {
            if (a instanceof GroupModel) {
                sizeGroup((GroupModel) a);
            }
        }

        // Pass 2 (top-down): place root-level activities, then recurse into groups.
        placeContainer(roots, rootTransitions(processModel), starters.get(0), 0, 0);
    }

    /**
     * Recursively assigns {@code group.width} and {@code group.height} based on its child layout.
     * Inner groups are sized first (depth-first), then the current group is sized from a trial
     * placement of its now-sized children.
     */
    private void sizeGroup(GroupModel group) {
        for (ActivityModel child : group.getChildActivities()) {
            if (child instanceof GroupModel) {
                sizeGroup((GroupModel) child);
            }
        }
        Grid grid = computeGrid(group.getChildActivities(), childTransitions(group),
            firstStartActivity(group));
        int width;
        int height;
        if (group.getChildActivities().isEmpty()) {
            width = HORIZONTAL_STEP + 2 * GROUP_PADDING;
            height = VERTICAL_STEP + GROUP_HEADER_HEIGHT + 2 * GROUP_PADDING;
        } else {
            width = grid.totalWidth() + 2 * GROUP_PADDING;
            height = grid.totalHeight() + GROUP_HEADER_HEIGHT + 2 * GROUP_PADDING;
        }
        group.setWidth(width);
        group.setHeight(height);
        LOG.trace("Sized group '{}' to {}x{}", group.getId(), width, height);
    }

    /**
     * Places all activities in a container at absolute coordinates, then recurses into any group
     * children to place their interior. The container's origin (where its child area begins, in
     * absolute coordinates) is given by {@code originX} / {@code originY}.
     */
    private void placeContainer(List<ActivityModel> children, List<TransitionModel> transitions,
        ActivityModel starter, int originX, int originY) {
        if (children.isEmpty()) {
            return;
        }
        Grid grid = computeGrid(children, transitions, starter);
        for (ActivityModel a : children) {
            Grid.Cell cell = grid.cellOf(a.getId());
            if (cell == null) {
                continue;
            }
            a.setX(originX + grid.xOf(cell.col));
            a.setY(originY + grid.yOf(cell.row));
        }
        // Recurse into nested groups now that their absolute position is known.
        for (ActivityModel a : children) {
            if (a instanceof GroupModel) {
                GroupModel g = (GroupModel) a;
                int childOriginX = g.getX() + GROUP_PADDING;
                int childOriginY = g.getY() + GROUP_HEADER_HEIGHT + GROUP_PADDING;
                placeContainer(g.getChildActivities(), childTransitions(g),
                    firstStartActivity(g), childOriginX, childOriginY);
            }
        }
    }

    /** Activities that are not children of any group. */
    private static List<ActivityModel> rootChildren(ProcessModel processModel) {
        List<ActivityModel> result = new ArrayList<ActivityModel>();
        for (ActivityModel a : processModel.getActivityModels()) {
            if (a.getParent() == null) {
                result.add(a);
            }
        }
        return result;
    }

    /** Transitions whose endpoints are both at root level. */
    private static List<TransitionModel> rootTransitions(ProcessModel processModel) {
        List<TransitionModel> result = new ArrayList<TransitionModel>();
        for (TransitionModel t : processModel.getTransitionModels()) {
            ActivityModel from = t.getFromActivity();
            ActivityModel to = t.getToActivity();
            if (from != null && to != null && from.getParent() == null && to.getParent() == null) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * Transitions whose endpoints are both children of the given group. Iterates the process model's
     * full transition list via {@link ActivityModel#getProcessModel()} because
     * {@link GroupModel#getChildTransitions()} only reflects transitions explicitly parented via
     * {@code addChildTransition}, which most model builders do not call.
     */
    private static List<TransitionModel> childTransitions(GroupModel group) {
        List<TransitionModel> result = new ArrayList<TransitionModel>();
        for (TransitionModel t : group.getProcessModel().getTransitionModels()) {
            ActivityModel from = t.getFromActivity();
            ActivityModel to = t.getToActivity();
            if (from != null && to != null && from.getParent() == group && to.getParent() == group) {
                result.add(t);
            }
        }
        return result;
    }

    private static ActivityModel firstStartActivity(GroupModel group) {
        List<ActivityModel> starts = group.getStartActivities();
        if (starts.isEmpty()) {
            List<ActivityModel> children = group.getChildActivities();
            return children.isEmpty() ? null : children.get(0);
        }
        return starts.get(0);
    }

    /**
     * Builds a column/row grid for one container's children. BFS assigns columns from the starter,
     * with the max-column rule for convergence (a node's column is the deepest predecessor's column +
     * 1). Rows separate parallel branches inside a column. Per-column widths and per-row heights are
     * derived from the children's own sizes (so a wide nested group widens its column).
     */
    private Grid computeGrid(List<ActivityModel> children, List<TransitionModel> transitions,
        ActivityModel starter) {
        Map<String, List<ActivityModel>> successors = new LinkedHashMap<String, List<ActivityModel>>();
        for (TransitionModel t : transitions) {
            ActivityModel from = t.getFromActivity();
            ActivityModel to = t.getToActivity();
            if (from != null && to != null) {
                successors.computeIfAbsent(from.getId(), k -> new ArrayList<ActivityModel>()).add(to);
            }
        }
        Map<String, Integer> columnOf = new LinkedHashMap<String, Integer>();
        List<ActivityModel> traversal = new ArrayList<ActivityModel>();
        if (starter != null) {
            bfs(starter, successors, columnOf, traversal);
        }
        for (ActivityModel a : children) {
            if (!columnOf.containsKey(a.getId())) {
                columnOf.put(a.getId(), 0);
                traversal.add(a);
            }
        }
        Map<Integer, List<String>> byColumn = new LinkedHashMap<Integer, List<String>>();
        for (ActivityModel a : traversal) {
            byColumn.computeIfAbsent(columnOf.get(a.getId()), k -> new ArrayList<String>()).add(a.getId());
        }
        Map<String, Integer> rowOf = new HashMap<String, Integer>();
        for (List<String> ids : byColumn.values()) {
            for (int i = 0; i < ids.size(); i++) {
                rowOf.put(ids.get(i), i);
            }
        }
        Map<String, ActivityModel> byId = new HashMap<String, ActivityModel>();
        for (ActivityModel a : children) {
            byId.put(a.getId(), a);
        }
        int numColumns = byColumn.size();
        int[] colWidth = new int[numColumns];
        int maxRow = 0;
        for (Map.Entry<Integer, List<String>> e : byColumn.entrySet()) {
            int col = e.getKey();
            for (String id : e.getValue()) {
                ActivityModel a = byId.get(id);
                int w = (a instanceof GroupModel) ? ((GroupModel) a).getWidth() : ACTIVITY_SIZE;
                if (w > colWidth[col]) {
                    colWidth[col] = w;
                }
                int row = rowOf.get(id);
                if (row > maxRow) {
                    maxRow = row;
                }
            }
        }
        int numRows = maxRow + 1;
        int[] rowHeight = new int[numRows];
        for (ActivityModel a : children) {
            Integer row = rowOf.get(a.getId());
            if (row == null) {
                continue;
            }
            int h = (a instanceof GroupModel) ? ((GroupModel) a).getHeight() : ACTIVITY_SIZE;
            if (h > rowHeight[row]) {
                rowHeight[row] = h;
            }
        }
        return new Grid(columnOf, rowOf, colWidth, rowHeight);
    }

    private void bfs(ActivityModel starter, Map<String, List<ActivityModel>> successors,
        Map<String, Integer> columnOf, List<ActivityModel> traversal) {
        Queue<ActivityModel> queue = new ArrayDeque<ActivityModel>();
        Set<String> enqueued = new HashSet<String>();
        columnOf.put(starter.getId(), 0);
        queue.add(starter);
        enqueued.add(starter.getId());
        while (!queue.isEmpty()) {
            ActivityModel node = queue.poll();
            traversal.add(node);
            int col = columnOf.get(node.getId());
            List<ActivityModel> succs = successors.getOrDefault(node.getId(), Collections.emptyList());
            for (ActivityModel succ : succs) {
                int candidate = col + 1;
                if (!columnOf.containsKey(succ.getId()) || columnOf.get(succ.getId()) < candidate) {
                    columnOf.put(succ.getId(), candidate);
                }
                if (enqueued.add(succ.getId())) {
                    queue.add(succ);
                }
            }
        }
    }

    /** Result of laying out one container: cell coordinates + per-column widths + per-row heights. */
    private static final class Grid {
        private static final class Cell {
            final int col;
            final int row;

            Cell(int col, int row) {
                this.col = col;
                this.row = row;
            }
        }

        private final Map<String, Cell> cells;
        private final int[] colWidth;
        private final int[] rowHeight;
        private final int[] colX;
        private final int[] rowY;

        Grid(Map<String, Integer> columnOf, Map<String, Integer> rowOf, int[] colWidth, int[] rowHeight) {
            Map<String, Cell> map = new HashMap<String, Cell>();
            for (Map.Entry<String, Integer> e : columnOf.entrySet()) {
                map.put(e.getKey(), new Cell(e.getValue(), rowOf.getOrDefault(e.getKey(), 0)));
            }
            this.cells = Collections.unmodifiableMap(map);
            this.colWidth = colWidth;
            this.rowHeight = rowHeight;
            this.colX = new int[colWidth.length];
            int runningX = 0;
            for (int i = 0; i < colWidth.length; i++) {
                colX[i] = runningX;
                runningX += colWidth[i];
                if (i + 1 < colWidth.length) {
                    runningX += HORIZONTAL_STEP - ACTIVITY_SIZE;
                }
            }
            this.rowY = new int[rowHeight.length];
            int runningY = 0;
            for (int i = 0; i < rowHeight.length; i++) {
                rowY[i] = runningY;
                runningY += rowHeight[i];
                if (i + 1 < rowHeight.length) {
                    runningY += VERTICAL_STEP - ACTIVITY_SIZE;
                }
            }
        }

        Cell cellOf(String id) {
            return cells.get(id);
        }

        int xOf(int col) {
            return colX[col];
        }

        int yOf(int row) {
            return rowY[row];
        }

        int totalWidth() {
            if (colX.length == 0) {
                return 0;
            }
            int last = colX.length - 1;
            return colX[last] + colWidth[last];
        }

        int totalHeight() {
            if (rowY.length == 0) {
                return 0;
            }
            int last = rowY.length - 1;
            return rowY[last] + rowHeight[last];
        }
    }
}
