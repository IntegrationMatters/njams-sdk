# SDK-479 Polyline Routing Obstacle Awareness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three defects found in the process-diagram SVG rendering, tracked under the dedicated ticket SDK-479 (split out from the originating SDK-452 poly-line routing feature): (1) a bypass transition in `PolylineProcessDiagramFactory` can cross a group box taller than a plain activity, (2) a bypass's approach corridor and a fan-in elbow's gutter can land on the exact same x-coordinate near a shared target, and (3) `NjamsProcessDiagramFactory.drawGroup()` positions a group's y using the horizontal start offset instead of the vertical one.

**Architecture:** All three fixes are surgical, root-cause-targeted changes to existing private/protected methods — no new public API, no new classes. Fix 1 and 2 touch `PolylineProcessDiagramFactory`'s routing algorithm (`buildPlan`/`routeContainer`/`classify`/`assignLanes`); fix 3 is a one-line typo fix in the base `NjamsProcessDiagramFactory.drawGroup()`.

**Tech Stack:** Java 11, JUnit 4, `com.im.njams.sdk.model` (ProcessModel/ActivityModel/GroupModel/TransitionModel), `com.im.njams.sdk.model.layout.CommonBfsModelLayouter` (real layouter used by tests), W3C DOM for SVG assertions.

**Spec:** None — this is a direct bug-fix plan for SDK-479; the analysis and root causes were established via conversation with the user (see ticket SDK-452 for the originating poly-line routing feature these defects were found in). No separate spec document was requested.

## Global Constraints

- Every commit must reference `SDK-479` (format: `SDK-479 <description>`), per `commit-conventions.md`. Do not add `#comment` to intermediate commits — only the commit that finalizes this work (decided by the user at close-out via `njams-ticket-finish`) may carry it.
- Existing test cases must never be modified. If any existing test fails after a change, the fix is wrong — stop and reconsider (per `njams-bug-fix` / `testing-conventions.md`).
- No public or protected member signatures change in this plan — all edits are to `private` methods, plus one one-line body fix inside an existing `protected` method (`drawGroup`) whose signature and Javadoc are unaffected. No Javadoc updates are required.
- Run `mvn test -pl njams-sdk` for a full-suite baseline before Task 1, and again after the final task, to confirm nothing outside the touched files regressed.

---

## File Structure

- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/svg/NjamsProcessDiagramFactory.java` — fix the `getStartX()`/`getStartY()` typo in `drawGroup()`.
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/NjamsProcessDiagramFactoryTest.java` — reproducer test for the typo fix.
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java` — sibling-geometry-aware bypass channel placement (fix 1), and shared bypass/elbow gutter-lane reservation (fix 2).
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java` — reproducer tests for fix 1 and fix 2.

---

## Baseline

- [ ] **Step 0: Establish the full-suite baseline**

Run: `mvn test -pl njams-sdk`
Expected: build succeeds (note the pass/fail counts; this is the baseline every later task's full-suite run is compared against).

---

### Task 1: Fix `drawGroup()` vertical positioning typo

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/svg/NjamsProcessDiagramFactory.java:404`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/NjamsProcessDiagramFactoryTest.java`

**Interfaces:**
- Consumes: `NjamsProcessDiagramContext.getStartY()` (already exists, already used elsewhere in the file for activities/transitions).
- Produces: nothing new — internal calculation only.

- [ ] **Step 1: Write the failing test**

Add to `NjamsProcessDiagramFactoryTest.java`, directly after the existing `drawGroup_labelIsLeftBoundToIconInsideHeader` test (after line 210):

```java
    @Test
    public void drawGroup_positionsGroupUsingVerticalStartOffsetNotHorizontal() throws Exception {
        NjamsProcessDiagramContext context = createDrawableContext("cat");
        context.setStartX(10);
        context.setStartY(4);
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(true);

        GroupModel group = buildGroup("g1", "MyGroup", "loop", 0, 6, 200, 150);
        factory.drawGroup(context, group);

        Element header = findByTagAndAttr(context.getDoc(), "rect", "id", "g1_group_header");
        Assert.assertNotNull("Expected group header rect", header);
        double headerY = Double.parseDouble(header.getAttributeNS(null, "y"));
        Assert.assertEquals(
            "Group header y must use the vertical start offset (startY=4), not the horizontal one (startX=10)",
            4 + 6, headerY, 0.01);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=NjamsProcessDiagramFactoryTest#drawGroup_positionsGroupUsingVerticalStartOffsetNotHorizontal -pl njams-sdk`
Expected: FAIL — `headerY` is `16.0` (10 + 6, using `startX`), not the expected `10.0` (4 + 6).

- [ ] **Step 3: Fix the typo**

In `NjamsProcessDiagramFactory.java`, change line 404 from:

```java
        int groupY = context.getStartX() + groupModel.getY();
```

to:

```java
        int groupY = context.getStartY() + groupModel.getY();
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=NjamsProcessDiagramFactoryTest#drawGroup_positionsGroupUsingVerticalStartOffsetNotHorizontal -pl njams-sdk`
Expected: PASS

- [ ] **Step 5: Run the full class to confirm no regression**

Run: `mvn test -Dtest=NjamsProcessDiagramFactoryTest -pl njams-sdk`
Expected: all tests PASS (same count as baseline, plus the new one).

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/model/svg/NjamsProcessDiagramFactory.java njams-sdk/src/test/java/com/im/njams/sdk/model/svg/NjamsProcessDiagramFactoryTest.java
git commit -m "SDK-479 Fix drawGroup() using horizontal start offset for group's y position"
```

---

### Task 2: Bypass channel avoids a group taller than a plain activity

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java`

**Interfaces:**
- Consumes: `GroupModel.getHeight()` (already exists, already used by `NjamsProcessDiagramFactory.drawGroup()`), `ProcessModel.getActivityModels()`, `ActivityModel.getParent()`, `GroupModel.getChildActivities()` (all already exist).
- Produces: `rootSiblings(ProcessModel)`, `rowBottom(int, List<ActivityModel>)`, `heightOf(ActivityModel)` — new private static helpers in `PolylineProcessDiagramFactory`. `routeContainer` and `classify` gain a `List<ActivityModel> siblings` parameter (private methods, no external callers to update besides the ones in this same file).

- [ ] **Step 1: Write the failing test**

Add to `PolylineProcessDiagramFactoryTest.java`. First add the missing import (the file does not currently import `GroupModel`) — add after the existing `import com.im.njams.sdk.model.ActivityModel;` (line 13):

```java
import com.im.njams.sdk.model.GroupModel;
```

Then add a `groupBoxes` helper and the reproducer test, placed directly after the existing `activityBoxes` helper (after line 65):

```java
    /** Bounding boxes of group containers (header + child area combined), in SVG coordinates. */
    private static List<double[]> groupBoxes(Document doc) {
        List<double[]> boxes = new ArrayList<>();
        NodeList groups = doc.getElementsByTagNameNS(SVG_NS, "g");
        for (int i = 0; i < groups.getLength(); i++) {
            Element g = (Element) groups.item(i);
            if (!g.hasAttribute("modelId") || !g.getAttribute("id").startsWith("group_")) {
                continue;
            }
            Double left = null;
            Double top = null;
            Double right = null;
            Double bottom = null;
            NodeList rects = g.getElementsByTagNameNS(SVG_NS, "rect");
            for (int r = 0; r < rects.getLength(); r++) {
                Element rect = (Element) rects.item(r);
                double x = Double.parseDouble(rect.getAttribute("x"));
                double y = Double.parseDouble(rect.getAttribute("y"));
                double w = Double.parseDouble(rect.getAttribute("width"));
                double h = Double.parseDouble(rect.getAttribute("height"));
                left = left == null ? x : Math.min(left, x);
                top = top == null ? y : Math.min(top, y);
                right = right == null ? x + w : Math.max(right, x + w);
                bottom = bottom == null ? y + h : Math.max(bottom, y + h);
            }
            if (left != null) {
                boxes.add(new double[] {left, top, right, bottom});
            }
        }
        return boxes;
    }

    private static void assertNoTransitionCrossesAnyGroupBox(Document doc) {
        List<double[]> boxes = groupBoxes(doc);
        Assert.assertFalse("expected at least one group box in this diagram", boxes.isEmpty());
        for (Element poly : transitionPolylines(doc)) {
            List<double[]> pts = points(poly);
            for (int i = 0; i + 1 < pts.size(); i++) {
                for (double[] box : boxes) {
                    Assert.assertFalse(
                        "Transition '" + poly.getAttribute("modelId") + "' segment " + i
                            + " crosses a group box",
                        segmentIntersectsRect(pts.get(i), pts.get(i + 1), box));
                }
            }
        }
    }
```

Then add the reproducer test, placed directly after the existing `parallelBranches_shareGutterButGetDistinctLanes` test (after line 629, before the closing `}` of the class):

```java
    @Test
    public void bypass_avoidsGroupBoxTallerThanPlainActivity() throws Exception {
        // A -> G (a group containing one child H) -> B is the main chain; A -> B bypasses G directly,
        // skipping its column. A group's minimum height (header + padding + one child row = 120) is
        // far taller than a plain activity (50), so a channel computed only from a plain activity's
        // height would cut straight through it.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        GroupModel g = model.createGroup("G", "G", null);
        model.createActivity("B", "B", null);
        a.setStarter(true);
        model.createTransition("A", "G");
        ActivityModel h = g.createChildActivity("H", "H", null);
        h.setStarter(true);
        model.createTransition("G", "B");
        TransitionModel bypass = model.createTransition("A", "B");
        bypass.setName("skip");

        Document doc = parse(render(model));

        assertNoTransitionCrossesAnyGroupBox(doc);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest#bypass_avoidsGroupBoxTallerThanPlainActivity -pl njams-sdk`
Expected: FAIL — a segment of the `A->B` bypass polyline intersects the `G` group's box.

- [ ] **Step 3: Implement sibling-aware row-bottom computation**

In `PolylineProcessDiagramFactory.java`, replace `buildPlan` (currently lines 144-170) with:

```java
    private Map<String, Route> buildPlan(ProcessModel processModel) {
        Map<String, Route> routes = new HashMap<>();
        List<TransitionModel> rootTransitions = new ArrayList<>();
        Map<GroupModel, List<TransitionModel>> groupTransitions = new LinkedHashMap<>();
        for (TransitionModel t : processModel.getTransitionModels()) {
            ActivityModel from = t.getFromActivity();
            ActivityModel to = t.getToActivity();
            if (from == null || to == null || from instanceof GroupModel || to instanceof GroupModel) {
                continue;
            }
            GroupModel pf = from.getParent();
            GroupModel pt = to.getParent();
            if (pf != pt) {
                continue;
            }
            if (pf == null) {
                rootTransitions.add(t);
            } else {
                groupTransitions.computeIfAbsent(pf, k -> new ArrayList<>()).add(t);
            }
        }
        routeContainer(rootTransitions, rootSiblings(processModel), routes);
        for (Map.Entry<GroupModel, List<TransitionModel>> entry : groupTransitions.entrySet()) {
            routeContainer(entry.getValue(), entry.getKey().getChildActivities(), routes);
        }
        return routes;
    }

    /** Top-level activities of the process model — the obstacles a root-level edge must clear. */
    private static List<ActivityModel> rootSiblings(ProcessModel processModel) {
        List<ActivityModel> siblings = new ArrayList<>();
        for (ActivityModel a : processModel.getActivityModels()) {
            if (a.getParent() == null) {
                siblings.add(a);
            }
        }
        return siblings;
    }
```

Replace the `routeContainer` method (currently lines 172-208) with:

```java
    private void routeContainer(List<TransitionModel> transitions, List<ActivityModel> siblings,
        Map<String, Route> routes) {
        if (transitions.isEmpty()) {
            return;
        }
        TreeSet<Integer> xSet = new TreeSet<>();
        TreeSet<Integer> ySet = new TreeSet<>();
        for (TransitionModel t : transitions) {
            xSet.add(t.getFromActivity().getX());
            xSet.add(t.getToActivity().getX());
            ySet.add(t.getFromActivity().getY());
            ySet.add(t.getToActivity().getY());
        }
        List<Integer> sortedX = new ArrayList<>(xSet);
        List<Integer> sortedY = new ArrayList<>(ySet);

        Map<String, Integer> outDegree = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (TransitionModel t : transitions) {
            outDegree.merge(t.getFromActivity().getId(), 1, Integer::sum);
            inDegree.merge(t.getToActivity().getId(), 1, Integer::sum);
        }

        List<Edge> edges = new ArrayList<>();
        for (TransitionModel t : transitions) {
            Edge e = classify(t, sortedX, sortedY, siblings);
            e.sourceFanOut = outDegree.getOrDefault(t.getFromActivity().getId(), 0) > 1;
            e.targetFanIn = inDegree.getOrDefault(t.getToActivity().getId(), 0) > 1;
            if (e.type == Type.ELBOW) {
                assignElbowGutter(e, sortedX);
            }
            edges.add(e);
        }
        assignLanes(edges);
        for (Edge e : edges) {
            routes.put(e.transition.getId(), buildRoute(e));
        }
    }
```

Replace the `classify` method (currently lines 210-253) with:

```java
    private Edge classify(TransitionModel t, List<Integer> sortedX, List<Integer> sortedY,
        List<ActivityModel> siblings) {
        Edge e = new Edge();
        e.transition = t;
        ActivityModel from = t.getFromActivity();
        ActivityModel to = t.getToActivity();
        e.scx = from.getX() + DEFAULT_HALF_ACTIVITY_SIZE;
        e.scy = from.getY() + DEFAULT_HALF_ACTIVITY_SIZE;
        e.tcx = to.getX() + DEFAULT_HALF_ACTIVITY_SIZE;
        e.tcy = to.getY() + DEFAULT_HALF_ACTIVITY_SIZE;
        int colS = sortedX.indexOf(from.getX());
        int colT = sortedX.indexOf(to.getX());
        int rowS = sortedY.indexOf(from.getY());
        int rowT = sortedY.indexOf(to.getY());
        e.rowS = rowS;
        e.colS = colS;
        e.colT = colT;

        if (colS == colT || colT < colS || (rowS == rowT && Math.abs(colT - colS) < 2)) {
            e.type = Type.STRAIGHT;
        } else if (rowS == rowT) {
            e.type = Type.BYPASS;
            // Channel below the row, kept clear both of the label text drawn beneath the icons and of
            // any sibling in this row that is taller than a plain activity (e.g. a group box).
            double rowBottom = rowBottom(from.getY(), siblings);
            double labelClear = from.getY() + DEFAULT_ACTIVITY_SIZE + LABEL_CLEARANCE;
            double mid = rowS + 1 < sortedY.size()
                ? (rowBottom + sortedY.get(rowS + 1)) / 2.0
                : rowBottom + (DEFAULT_ROW_SPACING - DEFAULT_ACTIVITY_SIZE) / 2.0;
            e.laneBase = Math.max(mid, labelClear);
            // Leave the source on its right and re-enter the target from the left, through the
            // gutters between columns, so neither end crosses the label below an icon.
            double srcRight = from.getX() + DEFAULT_ACTIVITY_SIZE;
            e.exitX = colS + 1 < sortedX.size()
                ? (srcRight + sortedX.get(colS + 1)) / 2.0
                : srcRight + (DEFAULT_COLUMN_SPACING - DEFAULT_ACTIVITY_SIZE) / 2.0;
            e.approachX = colT - 1 >= 0
                ? (sortedX.get(colT - 1) + DEFAULT_ACTIVITY_SIZE + to.getX()) / 2.0
                : to.getX() - (DEFAULT_COLUMN_SPACING - DEFAULT_ACTIVITY_SIZE) / 2.0;
        } else {
            e.type = Type.ELBOW;
            // The gutter the vertical run uses depends on the fan role, decided in assignElbowGutter
            // once the fan-out / fan-in flags are known.
        }
        return e;
    }

    /**
     * The lowest y a bypass channel in this row may safely start from: the bottom edge of the tallest
     * sibling activity whose top sits exactly at {@code rowY} — a plain activity's bottom by default,
     * or a group's actual (dynamic) bottom edge when a group occupies that row.
     */
    private static double rowBottom(int rowY, List<ActivityModel> siblings) {
        double bottom = rowY + DEFAULT_ACTIVITY_SIZE;
        for (ActivityModel sibling : siblings) {
            if (sibling.getY() == rowY) {
                double siblingBottom = sibling.getY() + heightOf(sibling);
                if (siblingBottom > bottom) {
                    bottom = siblingBottom;
                }
            }
        }
        return bottom;
    }

    private static int heightOf(ActivityModel activity) {
        return activity instanceof GroupModel ? ((GroupModel) activity).getHeight() : DEFAULT_ACTIVITY_SIZE;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest#bypass_avoidsGroupBoxTallerThanPlainActivity -pl njams-sdk`
Expected: PASS

- [ ] **Step 5: Run the full class to confirm no regression**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk`
Expected: all tests PASS (same count as before this task, plus the new one).

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java
git commit -m "SDK-479 Make bypass channel placement aware of taller sibling group boxes"
```

---

### Task 3: Bypass approach corridor and fan-in elbow gutter no longer collide

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java`

**Interfaces:**
- Consumes: `Edge.colT`, `Edge.gutterCol`, `Edge.type` (existing fields, unchanged).
- Produces: no new methods; `assignLanes` gains an internal `Set<Integer> gutterColumnsClaimedByBypass` and applies a lane offset to elbow groups sharing a claimed gutter column.

- [ ] **Step 1: Write the failing test**

Add to `PolylineProcessDiagramFactoryTest.java`, directly after the `bypass_avoidsGroupBoxTallerThanPlainActivity` test added in Task 2:

```java
    @Test
    public void bypassAndFanInElbow_sharingApproachGutter_useDistinctCorridors() throws Exception {
        // X -> D -> Y is the main path; X -> Y bypasses D directly (skips its column). B and C, on
        // two other rows, also feed into Y (fan-in). The bypass's final approach hop and the fan-in
        // elbows' gutter both reference "the column just left of Y" using the identical formula, so
        // without coordination they can land on the exact same x — making the bypass's last hop and
        // an elbow's long vertical run run right on top of each other near the shared target.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("D", "D", null);
        model.createActivity("B", "B", null);
        model.createActivity("C", "C", null);
        model.createActivity("Y", "Y", null);
        x.setStarter(true);
        model.createTransition("X", "D");
        model.createTransition("D", "Y");
        model.createTransition("X", "Y").setName("skip");
        model.createTransition("X", "B");
        model.createTransition("B", "Y");
        model.createTransition("X", "C");
        model.createTransition("C", "Y");

        Document doc = parse(render(model));

        Element bypass = findTransitionPolyline(doc, transitionId(model, "X", "Y"));
        Element elbowB = findTransitionPolyline(doc, transitionId(model, "B", "Y"));
        Element elbowC = findTransitionPolyline(doc, transitionId(model, "C", "Y"));
        Assert.assertNotNull("bypass X->Y must be routed", bypass);
        Assert.assertNotNull("elbow B->Y must be routed", elbowB);
        Assert.assertNotNull("elbow C->Y must be routed", elbowC);

        // The bypass's final vertical hop into the target is its second-to-last waypoint's x.
        double bypassApproachX = points(bypass).get(points(bypass).size() - 2)[0];
        // An elbow's gutter x is its second waypoint (after the exit-from-source hop).
        double elbowBGutterX = points(elbowB).get(1)[0];
        double elbowCGutterX = points(elbowC).get(1)[0];

        Assert.assertTrue("bypass approach corridor must not coincide with elbow B's gutter",
            Math.abs(bypassApproachX - elbowBGutterX) > 0.01);
        Assert.assertTrue("bypass approach corridor must not coincide with elbow C's gutter",
            Math.abs(bypassApproachX - elbowCGutterX) > 0.01);
        Assert.assertTrue("fan-in elbows sharing a gutter must still use distinct lanes",
            Math.abs(elbowBGutterX - elbowCGutterX) > 0.01);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest#bypassAndFanInElbow_sharingApproachGutter_useDistinctCorridors -pl njams-sdk`
Expected: FAIL — `bypassApproachX` equals `elbowBGutterX` (both computed from the identical "midpoint of column-left-of-target and target's left edge" formula).

- [ ] **Step 3: Implement the shared gutter-lane reservation**

In `PolylineProcessDiagramFactory.java`, add two imports: `java.util.HashSet` (after `java.util.HashMap`) and `java.util.Set` (after `java.util.Objects`, before `java.util.TreeSet`), so the alphabetically-ordered import block reads:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
```

Replace the `assignLanes` method (after Task 2's edits, this is still the same method, unchanged by Task 2) with:

```java
    /** Gives each edge sharing a gutter (elbow) or channel (bypass) a distinct lateral offset. */
    private void assignLanes(List<Edge> edges) {
        Map<Integer, List<Edge>> bypassByRow = new LinkedHashMap<>();
        Map<Integer, List<Edge>> elbowByGutter = new LinkedHashMap<>();
        Set<Integer> gutterColumnsClaimedByBypass = new HashSet<>();
        for (Edge e : edges) {
            if (e.type == Type.BYPASS) {
                bypassByRow.computeIfAbsent(e.rowS, k -> new ArrayList<>()).add(e);
                // A bypass approaches its target through the gutter just left of the target column —
                // the same reference gutter a fan-in elbow to the same target uses (see
                // assignElbowGutter). Claim it so that group's lanes start one further out below.
                gutterColumnsClaimedByBypass.add(e.colT - 1);
            } else if (e.type == Type.ELBOW) {
                elbowByGutter.computeIfAbsent(e.gutterCol, k -> new ArrayList<>()).add(e);
            }
        }
        Comparator<Edge> bypassOrder = Comparator.comparingDouble((Edge e) -> e.tcy)
            .thenComparingDouble(e -> e.tcx)
            .thenComparing(e -> e.transition.getId());
        for (List<Edge> group : bypassByRow.values()) {
            group.sort(bypassOrder);
            for (int i = 0; i < group.size(); i++) {
                group.get(i).lane = i;
            }
        }
        // Elbow lanes must nest so branches sharing a gutter never cross. Lane 0 hugs the gutter base
        // and higher lanes step away from it. An edge with a longer vertical run has to sit where
        // shorter branches can pass it without interception: for a fan-out (shared source) the
        // farthest target takes the lane nearest the source (longest run outermost); for a fan-in
        // (shared target) the nearest source takes the base lane (longest run innermost, hugging the
        // convergence). Ordering by the signed span achieves both.
        Comparator<Edge> elbowOrder = Comparator
            .comparingDouble(PolylineProcessDiagramFactory::elbowLaneKey)
            .thenComparingDouble((Edge e) -> e.tcy)
            .thenComparingDouble(e -> e.tcx)
            .thenComparing(e -> e.transition.getId());
        for (Map.Entry<Integer, List<Edge>> gutterGroup : elbowByGutter.entrySet()) {
            List<Edge> group = gutterGroup.getValue();
            group.sort(elbowOrder);
            int n = group.size();
            // A bypass sharing this gutter column already occupies its base corridor (lane 0) for the
            // final hop into a common target; start the elbow group one lane further out so a fan-in
            // elbow's gutter can never land on the exact x a bypass's approach corridor already uses.
            int laneOffset = gutterColumnsClaimedByBypass.contains(gutterGroup.getKey()) ? 1 : 0;
            for (int i = 0; i < n; i++) {
                Edge e = group.get(i);
                e.lane = i + laneOffset;
                // Stagger direction must follow the elbow's travel direction so the staggered exit/entry
                // y stays strictly between scy and tcy, keeping the vertical segment clear of any
                // straight edge from the same source (or to the same target) at y=scy (or y=tcy).
                // For fan-out, the inner lane (lane 0, smallest gx) gets the LARGEST stagger so that
                // the outer lane's horizontal — at a smaller y — never falls inside the inner lane's
                // vertical range. For fan-in the roles reverse: inner lane gets the SMALLEST stagger
                // so the inner's final horizontal stays below the outer's vertical.
                double direction = Math.signum(e.tcy - e.scy);
                if (e.targetFanIn && !e.sourceFanOut) {
                    e.nodeStagger = -direction * (i + 1) * (LANE_GAP / 2.0);
                } else {
                    e.nodeStagger = direction * (n - i) * (LANE_GAP / 2.0);
                }
            }
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest#bypassAndFanInElbow_sharingApproachGutter_useDistinctCorridors -pl njams-sdk`
Expected: PASS

- [ ] **Step 5: Run the full class to confirm no regression**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk`
Expected: all tests PASS (same count as after Task 2, plus the new one).

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java
git commit -m "SDK-479 Prevent bypass approach corridor from colliding with a fan-in elbow gutter"
```

---

## Final Verification

- [ ] **Step 1: Run the full SDK test suite**

Run: `mvn test -pl njams-sdk`
Expected: PASS, same or greater test count than the Step 0 baseline, no failures.

- [ ] **Step 2: Hand off**

This plan's code changes are complete. Do not resolve the ticket from here — closing SDK-479 (breaking-change label check, closing comment, resolution) goes through `njams-ticket-finish` once the user is ready, per `jira-workflow.md`.
