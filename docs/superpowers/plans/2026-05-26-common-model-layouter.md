# CommonModelLayouter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the broken `SimpleProcessModelLayouter` as the default layouter in the SDK with a new `CommonModelLayouter` copied from the proven `CamelProcessModelLayouter`.

**Architecture:** `CommonModelLayouter` is a direct copy of `CamelProcessModelLayouter` (from njams-client-camel) adapted to the SDK package. It uses a two-pass BFS algorithm: depth-first sizing for groups, then top-down absolute coordinate placement. `SimpleProcessModelLayouter` is deprecated with a reference to the replacement, and `Njams` is updated to instantiate `CommonModelLayouter` as the default.

**Tech Stack:** Java 11, JUnit 4, Mockito, existing SDK model API (`ProcessModel`, `ActivityModel`, `GroupModel`, `TransitionModel`, `ProcessModelLayouter`).

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `njams-sdk/src/main/java/com/im/njams/sdk/model/layout/CommonModelLayouter.java` | New default layouter |
| Create | `njams-sdk/src/test/java/com/im/njams/sdk/model/layout/CommonModelLayouterTest.java` | Unit tests |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/model/layout/SimpleProcessModelLayouter.java` | Add `@Deprecated` + Javadoc |
| Modify | `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java` | Change default to `CommonModelLayouter` |

---

### Task 1: Create `CommonModelLayouter`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/model/layout/CommonModelLayouter.java`

- [ ] **Step 1: Write the failing test first**

Create `njams-sdk/src/test/java/com/im/njams/sdk/model/layout/CommonModelLayouterTest.java`:

```java
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
        // ProcessModel requires a non-null Njams reference, so use a minimal stub.
        // ProcessModel(String path, Njams njams) — use null-safe constructor via reflection is fragile.
        // Instead use the two-arg constructor: new ProcessModel(njams, path).
        // Since we only need the model structure (not SVG/sending), create a minimal Njams.
        com.im.njams.sdk.settings.Settings settings = com.im.njams.sdk.communication.TestSender.getSettings();
        com.im.njams.sdk.Njams njams = new com.im.njams.sdk.Njams(
            com.im.njams.sdk.Path.of("TEST"), "1.0", "TEST", settings);
        return njams.createProcess(com.im.njams.sdk.Path.of("proc"));
    }

    @Test
    public void noStartActivity_doesNothing() {
        ProcessModel model = createProcess();
        model.createActivity("A", "A", null); // not marked as starter
        // Should not throw; coordinates remain at default (0)
        layouter.layout(model);
        assertEquals(0, model.getActivity("A").getX());
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
        // A -> B -> D, A -> C -> D; D should be in column 2
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
```

- [ ] **Step 2: Run the test to confirm it fails (class does not exist yet)**

```
mvn test -Dtest=CommonModelLayouterTest -pl njams-sdk
```

Expected: compilation failure — `CommonModelLayouter cannot be found`.

- [ ] **Step 3: Create `CommonModelLayouter.java`**

Create `njams-sdk/src/main/java/com/im/njams/sdk/model/layout/CommonModelLayouter.java` with the content below. This is a direct port of `CamelProcessModelLayouter`, adapted: package changed, class renamed, Javadoc updated to describe generic use, Camel-specific wording removed.

```java
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
            if (a instanceof GroupModel g) {
                sizeGroup(g);
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
            if (child instanceof GroupModel inner) {
                sizeGroup(inner);
            }
        }
        Grid grid = computeGrid(group.getChildActivities(), childTransitions(group),
            firstStartActivity(group));
        int width = grid.totalWidth() + 2 * GROUP_PADDING;
        int height = grid.totalHeight() + GROUP_HEADER_HEIGHT + 2 * GROUP_PADDING;
        if (group.getChildActivities().isEmpty()) {
            width = HORIZONTAL_STEP + 2 * GROUP_PADDING;
            height = VERTICAL_STEP + GROUP_HEADER_HEIGHT + 2 * GROUP_PADDING;
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
            a.setX(originX + grid.xOf(cell.col()));
            a.setY(originY + grid.yOf(cell.row()));
        }
        // Recurse into nested groups now that their absolute position is known.
        for (ActivityModel a : children) {
            if (a instanceof GroupModel g) {
                int childOriginX = g.getX() + GROUP_PADDING;
                int childOriginY = g.getY() + GROUP_HEADER_HEIGHT + GROUP_PADDING;
                placeContainer(g.getChildActivities(), childTransitions(g),
                    firstStartActivity(g), childOriginX, childOriginY);
            }
        }
    }

    // --- helpers -----------------------------------------------------------------------------------

    /** Activities that are not children of any group. */
    private static List<ActivityModel> rootChildren(ProcessModel processModel) {
        List<ActivityModel> result = new ArrayList<>();
        for (ActivityModel a : processModel.getActivityModels()) {
            if (a.getParent() == null) {
                result.add(a);
            }
        }
        return result;
    }

    /** Transitions whose endpoints are both at root level. */
    private static List<TransitionModel> rootTransitions(ProcessModel processModel) {
        List<TransitionModel> result = new ArrayList<>();
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
        List<TransitionModel> result = new ArrayList<>();
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
        Map<String, List<ActivityModel>> successors = new LinkedHashMap<>();
        for (TransitionModel t : transitions) {
            ActivityModel from = t.getFromActivity();
            ActivityModel to = t.getToActivity();
            if (from != null && to != null) {
                successors.computeIfAbsent(from.getId(), k -> new ArrayList<>()).add(to);
            }
        }
        Map<String, Integer> columnOf = new LinkedHashMap<>();
        List<ActivityModel> traversal = new ArrayList<>();
        if (starter != null) {
            bfs(starter, successors, columnOf, traversal);
        }
        // Any child not reachable from the starter still needs a slot (defensive).
        for (ActivityModel a : children) {
            if (!columnOf.containsKey(a.getId())) {
                columnOf.put(a.getId(), 0);
                traversal.add(a);
            }
        }
        Map<Integer, List<String>> byColumn = new LinkedHashMap<>();
        for (ActivityModel a : traversal) {
            byColumn.computeIfAbsent(columnOf.get(a.getId()), k -> new ArrayList<>()).add(a.getId());
        }
        Map<String, Integer> rowOf = new HashMap<>();
        for (List<String> ids : byColumn.values()) {
            for (int i = 0; i < ids.size(); i++) {
                rowOf.put(ids.get(i), i);
            }
        }
        // Compute per-column max width and per-row max height from the children's sizes.
        Map<String, ActivityModel> byId = new HashMap<>();
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
                int w = a instanceof GroupModel g ? g.getWidth() : ACTIVITY_SIZE;
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
            int h = a instanceof GroupModel g ? g.getHeight() : ACTIVITY_SIZE;
            if (h > rowHeight[row]) {
                rowHeight[row] = h;
            }
        }
        return new Grid(columnOf, rowOf, colWidth, rowHeight);
    }

    private void bfs(ActivityModel starter, Map<String, List<ActivityModel>> successors,
        Map<String, Integer> columnOf, List<ActivityModel> traversal) {
        Queue<ActivityModel> queue = new ArrayDeque<>();
        Set<String> enqueued = new HashSet<>();
        columnOf.put(starter.getId(), 0);
        queue.add(starter);
        enqueued.add(starter.getId());
        while (!queue.isEmpty()) {
            ActivityModel node = queue.poll();
            traversal.add(node);
            int col = columnOf.get(node.getId());
            for (ActivityModel succ : successors.getOrDefault(node.getId(), List.of())) {
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
        record Cell(int col, int row) {}

        private final Map<String, Cell> cells;
        private final int[] colWidth;
        private final int[] rowHeight;
        private final int[] colX;
        private final int[] rowY;

        Grid(Map<String, Integer> columnOf, Map<String, Integer> rowOf, int[] colWidth, int[] rowHeight) {
            Map<String, Cell> map = new HashMap<>();
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
```

- [ ] **Step 4: Run the tests to verify they pass**

```
mvn test -Dtest=CommonModelLayouterTest -pl njams-sdk
```

Expected: all 5 tests pass.

- [ ] **Step 5: Run Checkstyle to catch any Javadoc gaps**

```
mvn checkstyle:check -pl njams-sdk
```

Expected: BUILD SUCCESS (fix any reported violations before continuing).

- [ ] **Step 6: Commit**

```
git add njams-sdk/src/main/java/com/im/njams/sdk/model/layout/CommonModelLayouter.java
git add njams-sdk/src/test/java/com/im/njams/sdk/model/layout/CommonModelLayouterTest.java
git commit -m "SDK-428 #comment Add CommonModelLayouter as generic default process model layouter"
```

---

### Task 2: Deprecate `SimpleProcessModelLayouter`

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/layout/SimpleProcessModelLayouter.java`

> **Pre-condition:** Before modifying the existing class, invoke the `njams-safe-modification` skill. There are currently no tests for `SimpleProcessModelLayouter`. The deprecation only adds annotations/Javadoc — no behaviour changes — so the safe-modification skill's coverage requirement is satisfied by acknowledging there is nothing behavioural to break. If the skill requires a test, write one that verifies `SimpleProcessModelLayouter` still calls `layout()` without throwing.

- [ ] **Step 1: Write a baseline test for `SimpleProcessModelLayouter`**

Add to `njams-sdk/src/test/java/com/im/njams/sdk/model/layout/SimpleProcessModelLayouterTest.java`:

```java
package com.im.njams.sdk.model.layout;

import com.im.njams.sdk.Path;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class SimpleProcessModelLayouterTest {

    @Test
    public void layout_doesNotThrow() {
        Njams njams = new Njams(Path.of("TEST"), "1.0", "TEST", TestSender.getSettings());
        ProcessModel model = njams.createProcess(Path.of("proc"));
        ActivityModel a = model.createActivity("A", "A", null);
        a.setStarter(true);
        model.createTransition("A", "A"); // no-op self transition to keep it non-empty

        SimpleProcessModelLayouter layouter = new SimpleProcessModelLayouter();
        layouter.layout(model); // must not throw
        assertNotNull(model.getActivity("A"));
    }
}
```

- [ ] **Step 2: Run baseline test**

```
mvn test -Dtest=SimpleProcessModelLayouterTest -pl njams-sdk
```

Expected: PASS. (This confirms the class works before we touch it.)

- [ ] **Step 3: Add `@Deprecated` annotation and Javadoc tag**

In `SimpleProcessModelLayouter.java`, change the class Javadoc and add the annotation:

```java
/**
 * SimpleLayouter gets a ProcessModel and transforms positioning and group sizes
 * to an eye friendly Layout.
 *
 * It starts at startActivity and iterates through flow via successor
 * attributes.
 *
 * @author hsiegeln
 * @deprecated Use {@link CommonModelLayouter} instead. {@code CommonModelLayouter} supports
 *             parallel branches, convergence, and nested groups correctly, and is the default
 *             layouter used by {@link com.im.njams.sdk.Njams} since 6.0.
 */
@Deprecated
public class SimpleProcessModelLayouter implements ProcessModelLayouter {
```

- [ ] **Step 4: Run baseline test again to confirm deprecation did not break anything**

```
mvn test -Dtest=SimpleProcessModelLayouterTest -pl njams-sdk
```

Expected: still PASS.

- [ ] **Step 5: Run Checkstyle**

```
mvn checkstyle:check -pl njams-sdk
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```
git add njams-sdk/src/main/java/com/im/njams/sdk/model/layout/SimpleProcessModelLayouter.java
git add njams-sdk/src/test/java/com/im/njams/sdk/model/layout/SimpleProcessModelLayouterTest.java
git commit -m "SDK-428 #comment Deprecate SimpleProcessModelLayouter in favour of CommonModelLayouter"
```

---

### Task 3: Update `Njams` default layouter

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java:305`

> **Pre-condition:** Invoke `njams-safe-modification` skill before modifying `Njams.java`. Check `NjamsTest` for existing layouter coverage — currently none. Add a test that `Njams` defaults to `CommonModelLayouter` before making the change.

- [ ] **Step 1: Write a failing test in `NjamsTest.java`**

Find `NjamsTest.java` at `njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java` and add:

```java
@Test
public void defaultLayouter_isCommonModelLayouter() {
    com.im.njams.sdk.settings.Settings settings = com.im.njams.sdk.communication.TestSender.getSettings();
    Njams njams = new Njams(Path.of("TEST"), "1.0", "TEST", settings);
    assertTrue("Default layouter must be CommonModelLayouter",
        njams.getProcessModelLayouter() instanceof com.im.njams.sdk.model.layout.CommonModelLayouter);
}
```

- [ ] **Step 2: Run the failing test**

```
mvn test -Dtest=NjamsTest#defaultLayouter_isCommonModelLayouter -pl njams-sdk
```

Expected: FAIL — `SimpleProcessModelLayouter` is not a `CommonModelLayouter`.

- [ ] **Step 3: Update the default in `Njams.java`**

In `Njams.java` around line 305:

Old:
```java
import com.im.njams.sdk.model.layout.SimpleProcessModelLayouter;
...
processModelLayouter = new SimpleProcessModelLayouter();
```

New (import changes first):
```java
import com.im.njams.sdk.model.layout.CommonModelLayouter;
```

And the constructor line:
```java
processModelLayouter = new CommonModelLayouter();
```

Remove the `import com.im.njams.sdk.model.layout.SimpleProcessModelLayouter;` import (it is no longer used here — check the file for any other references first).

- [ ] **Step 4: Run the test to verify it passes**

```
mvn test -Dtest=NjamsTest#defaultLayouter_isCommonModelLayouter -pl njams-sdk
```

Expected: PASS.

- [ ] **Step 5: Run the full test suite**

```
mvn test -pl njams-sdk
```

Expected: all tests pass.

- [ ] **Step 6: Run Checkstyle**

```
mvn checkstyle:check -pl njams-sdk
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```
git add njams-sdk/src/main/java/com/im/njams/sdk/Njams.java
git add njams-sdk/src/test/java/com/im/njams/sdk/NjamsTest.java
git commit -m "SDK-428 #comment Switch Njams default layouter from SimpleProcessModelLayouter to CommonModelLayouter"
```

---

## Self-Review

**Spec coverage:**
- [x] Copy `CamelProcessModelLayouter` as `CommonModelLayouter` into SDK layout package — Task 1
- [x] Rename class, adjust docs to generic/common framing — Task 1, Step 3
- [x] Use `CommonModelLayouter` as default in `Njams` — Task 3
- [x] Mark `SimpleProcessModelLayouter` as `@Deprecated` with reference to replacement — Task 2
- [x] Note in deprecation that old one is no longer the default — Task 2, Step 3

**Placeholder scan:** No TBDs. All steps contain actual code.

**Type consistency:** `CommonModelLayouter` is referenced consistently across Tasks 1, 2, and 3. Static constants (`ACTIVITY_SIZE`, `GROUP_PADDING`, `GROUP_HEADER_HEIGHT`) are package-accessible (`static final int`) and used in Task 1's test.
