# Poly-line Process-Diagram Routing Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `PolylineProcessDiagramFactory` decide what is in the way from real box geometry instead of coordinate equality, and route the two edge families it currently refuses — edges touching a group, and backward edges.

**Architecture:** One geometry model (`NodeBox`) replaces the scattered `getX()`/`getY()` reads; a segment-versus-box obstacle test replaces the row-identity checks in `classify`/`hasSiblingBetween`; rows and columns become overlap bands used only for lane grouping. Candidate escalation (straight → natural corridor → detour corridor) picks the first route that clears every obstacle, validated **after** laning. Everything added stays `private` to the factory.

**Tech Stack:** Java 11, JUnit 4, Maven. No new dependencies. SVG DOM built with the JDK's `org.w3c.dom` as today.

**Spec:** `docs/superpowers/specs/2026-10-20-sdk-493-polyline-routing-improvements-design.md`

## Global Constraints

- `PolylineProcessDiagramFactory`'s constructors and overridden `protected` methods stay as they are; everything added is `private` (spec §4).
- `NjamsProcessDiagramFactory` — the default straight-line rendering — must not change observably (spec §3).
- The emitted SVG keeps its element shape: `<polyline>` with `modelId`, `markerId`, `name`, `points`, `marker-end`, and the existing `orient="auto"` arrow marker (spec §4).
- Cross-container transitions (`pf != pt` in `buildPlan`) stay unrouted; self-transitions stay the degenerate straight line (spec §3).
- Production and test must not share a copy of the segment/rect intersection: the test keeps its own `segmentIntersectsRect` so it verifies the result independently (spec §6.2).
- Existing invariant assertions in `PolylineProcessDiagramFactoryTest` must keep passing. Tests that pin *replaced geometry* (waypoint counts, specific corridor x values) may be rewritten to assert the invariant instead — but the behavioural invariant each one protects must still be asserted (spec §5.4). **Per `.claude/rules/testing-conventions.md` this is the one sanctioned exception; no other existing test may be modified.**
- Label band height is derived from `DEFAULT_TEXT_SIZE`, not hard-coded to 22; the test helper then follows the production value (spec §6.1).
- If no candidate is clear, take the last one and log at debug — never silently overlap (spec §6.6).
- Routing runs once per process model at project-message build time, not on the runtime monitoring path, so clarity beats micro-optimisation (spec §4).
- Copyright header on any new production file; Javadoc on public/protected members only (`.claude/rules/code-quality-general.md`, `public-api-design.md`). Keep Javadoc to intent and contract — no rationale essays.

## Review Focus

- **A container with exactly one activity, or a transition whose endpoints have identical coordinates:** band construction and dominance (`|dx|` vs `|dy|`) both degenerate; a reasonable person expects a rendered diagram, not a division by zero or an empty `points` attribute. Pinned in Task 2.
- **A backward edge inside a group:** its above-row channel must stay clamped inside the group's child area, or `assertNoTransitionCrossesAnyGroupBox` flags it against its own parent box (spec §6.2). Pinned in Task 6.
- **An edge whose source and target boxes overlap on both axes** (a group and a plain activity laid out on top of each other): every candidate intersects an obstacle, so the escalation loop must terminate and emit the last candidate rather than loop. Pinned in Task 5.
- **A fan whose branches would individually pick different shape families:** the shape family is decided for the fan as a whole, or the lane-nesting non-crossing argument stops holding (spec §6.6). Pinned in Task 5.
- **A diagram whose routed waypoints extend above y=0 or below the lowest label** (above-row channel, bottom attachment): `getAdditionalBounds` must cover the waypoints or the canvas clips them. Pinned in Task 7.

---

## Tasks

### Task 1: Node geometry and the label band

Introduces the one value type every later task reads, and removes the hard-coded 22 from the test helper.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java`

**Interfaces:**
- Consumes: `NjamsProcessDiagramFactory`'s `DEFAULT_ACTIVITY_SIZE` (50), `DEFAULT_HALF_ACTIVITY_SIZE` (25), `DEFAULT_TEXT_SIZE` (15), `DEFAULT_ACTIVITY_RADIUS` (30), `DEFAULT_MARKER_SIZE` (10).
- Produces, all `private` in `PolylineProcessDiagramFactory`:
  - `static final int LABEL_BAND_HEIGHT` — derived from `DEFAULT_TEXT_SIZE`; its value must equal 22, the height the test helper currently hard-codes.
  - `static final class NodeBox` with `double left, top, right, bottom`, `double centerX, centerY`, `boolean group`, and:
    - `static NodeBox of(ActivityModel a)` — `50x50` at `a.getX(), a.getY()` for a plain activity; `a.getWidth() x a.getHeight()` for a `GroupModel`.
    - `double[] obstacleExtent()` — `{left, top, right, bottom}` for a group; `{left, top, right, bottom + LABEL_BAND_HEIGHT}` for a plain activity.
    - `boolean overlapsVertically(NodeBox other)` / `boolean overlapsHorizontally(NodeBox other)` — band membership for Task 2.

- [ ] **Step 1: Write the failing test**

In `PolylineProcessDiagramFactoryTest`, replace the hard-coded band height in `labelBands(...)` (line ~172-173) with a constant read from the production value, and add:

```java
@Test
public void labelBandHeightIsDerivedFromTheTextSize() {
    // The band is the strip below the icon that drawActivity writes the name into: one text line plus
    // descender room. 22 is what the suite has always assumed; this pins the derivation, not the number.
    Assert.assertEquals(22, PolylineProcessDiagramFactory.labelBandHeightForTest());
}
```

Add `static int labelBandHeightForTest()` to the factory as the only test seam (package-private, not public).

- [ ] **Step 2: Run it and watch it fail**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest#labelBandHeightIsDerivedFromTheTextSize -pl njams-sdk`
Expected: FAIL — `labelBandHeightForTest` does not exist (compile error).

- [ ] **Step 3: Add `LABEL_BAND_HEIGHT`, `NodeBox` and `labelBandHeightForTest()`**

`NodeBox` is a plain private static final class; no behaviour beyond the accessors named above.

- [ ] **Step 4: Run the whole test class**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk`
Expected: PASS, all tests — `NodeBox` has no caller yet, so nothing else may change.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java
git commit -m "SDK-493 Introduce the node-box geometry and derive the label band height from the text size."
```

---

### Task 2: Overlap bands replace exact-coordinate rows and columns

Rows and columns stop being identity keys and become overlap bands, so a group and the plain activities beside it land in one band (spec §6.2). Lane grouping keeps using them; nothing yet uses them to decide obstruction.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java` — `routeContainer`, `rowBottom`, `channelY`, `widthAtColumn`, `nextColumn`, `prevColumn`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java`

**Interfaces:**
- Consumes: `NodeBox` from Task 1.
- Produces:
  - `private static final class Bands` with `static Bands of(List<ActivityModel> siblings)`, `int rowIndexOf(NodeBox n)`, `int colIndexOf(NodeBox n)`, `double rowBottom(int rowIndex)`, `double rowTop(int rowIndex)`, `int rowCount()`.
  - A band contains the nodes whose boxes overlap on the perpendicular axis; its `rowBottom` is the lowest bottom edge in it (today's `rowBottom`, generalised).

- [ ] **Step 1: Write the failing tests**

```java
@Test
public void aGroupAndAPlainActivityOnTheSameVisualBandShareARow() throws Exception {
    // A -> G (group with child H) -> B, plus the A -> B bypass. The group's connector centre sits far
    // below the plain activities', so identity-keyed rows put them in different rows and the bypass
    // loses its detour; overlap bands keep them together.
    ProcessModel model = createProcess();
    ActivityModel a = model.createActivity("A", "A", null);
    GroupModel g = model.createGroup("G", "G", null);
    model.createActivity("B", "B", null);
    a.setStarter(true);
    model.createTransition("A", "G");
    ActivityModel h = g.createChildActivity("H", "H", null);
    h.setStarter(true);
    model.createTransition("G", "B");
    model.createTransition("A", "B").setName("skip");

    Document doc = parse(render(model));

    assertNoTransitionCrossesAnyGroupBox(doc);
    assertNoTransitionCrossesAnyActivity(doc);
    assertNoTransitionCrossesAnyLabelBand(doc);
}

@Test
public void aSingleActivityWithNoTransitionsStillRenders() throws Exception {
    ProcessModel model = createProcess();
    model.createActivity("A", "A", null).setStarter(true);

    Document doc = parse(render(model));

    Assert.assertFalse("a one-activity diagram must still produce activity geometry",
        activityBoxes(doc).isEmpty());
}

@Test
public void aTransitionBetweenTwoActivitiesAtTheSamePositionStillRenders() throws Exception {
    // Degenerate geometry: |dx| and |dy| are both 0, so neither dominance nor band construction has a
    // direction to work from. It must still emit a polyline with a points attribute, not throw.
    ProcessModel model = createProcess();
    ActivityModel a = model.createActivity("A", "A", null);
    ActivityModel b = model.createActivity("B", "B", null);
    a.setStarter(true);
    model.createTransition("A", "B");
    Document doc = parse(renderWithoutLayout(model, () -> {
        a.setX(0); a.setY(0);
        b.setX(0); b.setY(0);
    }));

    Element poly = findTransitionPolyline(doc, transitionId(model, "A", "B"));
    Assert.assertNotNull("a degenerate transition must still be rendered", poly);
    Assert.assertFalse("its points attribute must not be empty", poly.getAttribute("points").isEmpty());
}
```

Add the helper `renderWithoutLayout(ProcessModel, Runnable placeCoordinates)` next to `render(...)`: it runs the placement instead of `CommonBfsModelLayouter`, then calls the factory. Task 6 reuses it.

- [ ] **Step 2: Run them and watch them fail**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk`
Expected: `aSingleActivityWithNoTransitionsStillRenders` and `aTransitionBetweenTwoActivitiesAtTheSamePositionStillRenders` fail to compile until `renderWithoutLayout` exists; `aGroupAndAPlainActivityOnTheSameVisualBandShareARow` fails on a crossing assertion.

- [ ] **Step 3: Add `Bands` and route `routeContainer` through it**

`routeContainer` builds `Bands.of(siblings)` instead of the `sortedX`/`sortedY` `TreeSet`s, and `classify`, `rowBottom`, `channelY`, `widthAtColumn`, `nextColumn` and `prevColumn` take band indices instead of raw coordinates. Keep `Edge.rowS`/`colS`/`colT` as band indices so `assignLanes` and `elbowLaneKey` are untouched.

- [ ] **Step 4: Run the whole class**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk`
Expected: PASS. If a test pinning a corridor x or waypoint count fails, rewrite it to assert its invariant per the Global Constraints, and say so in the commit.

- [ ] **Step 5: Commit**

```bash
git commit -am "SDK-493 Group nodes into overlap bands so a group and the activities beside it share a row."
```

---

### Task 3: Segment-versus-box obstacle detection

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java` — replaces `hasSiblingBetween`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java`

**Interfaces:**
- Consumes: `NodeBox.obstacleExtent()` from Task 1.
- Produces:
  - `private static final int CLEARANCE_MARGIN` — the inflation applied to every obstacle extent.
  - `private static final class Obstacles` with `static Obstacles of(List<ActivityModel> siblings)` and `boolean blocks(List<Point> waypoints, ActivityModel source, ActivityModel target)` — true when any segment between consecutive waypoints intersects any obstacle extent except the source's and the target's own.
  - `private static boolean segmentIntersectsRect(Point p, Point q, double[] rect)` — production's own implementation; the test keeps its separate copy.

- [ ] **Step 1: Write the failing test** — the regression guard for spec §2.1

```java
@Test
public void aBypassIsRoutedEvenWhenTheTargetIsOffsetVertically() throws Exception {
    // The reported shape: X -> A -> Y with X -> Y skipping A. Y is nudged a few pixels down, which is
    // enough for identity-keyed rows to drop the detour and draw a direct line straight through A.
    ProcessModel model = createProcess();
    ActivityModel x = model.createActivity("X", "X", null);
    ActivityModel a = model.createActivity("A", "A", null);
    ActivityModel y = model.createActivity("Y", "Y", null);
    x.setStarter(true);
    model.createTransition("X", "A");
    model.createTransition("A", "Y");
    model.createTransition("X", "Y").setName("skip");

    Document doc = parse(renderWithoutLayout(model, () -> {
        x.setX(0);   x.setY(0);
        a.setX(150); a.setY(0);
        y.setX(300); y.setY(7);
    }));

    assertNoTransitionCrossesAnyActivity(doc);
    assertNoTransitionCrossesAnyLabelBand(doc);
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest#aBypassIsRoutedEvenWhenTheTargetIsOffsetVertically -pl njams-sdk`
Expected: FAIL — the direct X→Y line crosses A's box.

- [ ] **Step 3: Add `Obstacles` and use it in `classify`**

`classify`'s `obstacleBetween` becomes a call to `Obstacles.blocks(...)` on the straight candidate; delete `hasSiblingBetween`. The `rowS == rowT` conditions stay for now — Task 5 removes them.

- [ ] **Step 4: Run the whole class**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -am "SDK-493 Decide obstruction by segment-versus-box intersection instead of row identity."
```

---

### Task 4: Four-side attachment

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java` — `buildRoute`, plus new attachment helpers beside `radiusPoint`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java`

**Interfaces:**
- Consumes: `NodeBox` (Task 1).
- Produces:
  - `private enum Side { LEFT, RIGHT, TOP, BOTTOM }`
  - `private static Side exitSide(NodeBox source, NodeBox target)` / `private static Side entrySide(NodeBox source, NodeBox target)` — `|dx| >= |dy|` gives RIGHT/LEFT (today's behaviour); `|dy| > |dx|` gives BOTTOM/TOP by sign. Backward edges do not use these (Task 6).
  - `private static Point attachmentPoint(NodeBox node, Side side)` — for a plain activity, the `DEFAULT_ACTIVITY_RADIUS` point in that direction for LEFT/RIGHT/TOP (`radiusPoint`, unchanged), and the **lower edge of the label band centred on the icon** for BOTTOM; for a group, the box edge on every side.
  - The arrow end stays pulled back by `DEFAULT_MARKER_SIZE`, as today.

- [ ] **Step 1: Write the failing tests**

```java
@Test
public void aVerticallyDominantEdgeEntersTheTargetFromTheTop() throws Exception {
    ProcessModel model = createProcess();
    ActivityModel a = model.createActivity("A", "A", null);
    ActivityModel b = model.createActivity("B", "B", null);
    a.setStarter(true);
    model.createTransition("A", "B");

    Document doc = parse(renderWithoutLayout(model, () -> {
        a.setX(0); a.setY(0);
        b.setX(0); b.setY(300);
    }));

    List<double[]> pts = points(findTransitionPolyline(doc, transitionId(model, "A", "B")));
    double[] last = pts.get(pts.size() - 1);
    double[] targetBox = activityBoxFor(doc, model, "B");
    Assert.assertTrue("the arrow must come down onto B's top edge, not around into its left flank",
        last[1] <= targetBox[1] + 1);
    assertNoTransitionCrossesAnyActivity(doc);
}

@Test
public void anEdgeArrivingFromBelowEndsAtTheLabelBandsLowerEdgeAndDoesNotEnterIt() throws Exception {
    ProcessModel model = createProcess();
    ActivityModel a = model.createActivity("A", "A", null);
    ActivityModel b = model.createActivity("B", "B", null);
    a.setStarter(true);
    model.createTransition("A", "B");

    Document doc = parse(renderWithoutLayout(model, () -> {
        a.setX(0); a.setY(300);
        b.setX(0); b.setY(0);
    }));

    List<double[]> pts = points(findTransitionPolyline(doc, transitionId(model, "A", "B")));
    double[] last = pts.get(pts.size() - 1);
    double[] targetBox = activityBoxFor(doc, model, "B");
    Assert.assertTrue("the arrow must stop at or below the label band's lower edge",
        last[1] >= targetBox[3] + PolylineProcessDiagramFactory.labelBandHeightForTest() - 1);
    assertNoTransitionCrossesAnyLabelBand(doc);
}
```

Add the helper `activityBoxFor(Document, ProcessModel, String activityName)` returning `{left, top, right, bottom}` of that activity's icon.

- [ ] **Step 2: Run them and watch them fail**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk`
Expected: both FAIL — the edge currently enters from the left on both.

- [ ] **Step 3: Implement `Side`, `exitSide`, `entrySide`, `attachmentPoint` and use them in `buildRoute`**

- [ ] **Step 4: Run the whole class**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -am "SDK-493 Attach edges on the side matching their approach direction, including top and bottom."
```

---

### Task 5: Candidate escalation, validated after laning

Replaces the `STRAIGHT`/`BYPASS`/`ELBOW` classification with "first candidate that clears everything", and re-validates after lanes and stagger have displaced the geometry (spec §6.6).

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java` — `routeContainer`, `classify`, `buildRoute`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java`

**Interfaces:**
- Consumes: `Obstacles` (Task 3), `Side`/`attachmentPoint` (Task 4), `Bands` (Task 2).
- Produces:
  - `private static final int MAX_ESCALATION_ROUNDS` — the bound on the re-validation loop.
  - `private List<Point> candidateWaypoints(Edge e, int level)` — level 0 the direct segment, 1 the §6.4 shape at its natural corridor, 2 the same shape displaced into the detour corridor.
  - `routeContainer` gains: assign a level per edge, run `assignLanes`, rebuild geometry, re-check with `Obstacles`, raise the level of any failing edge and repeat up to `MAX_ESCALATION_ROUNDS`; then emit. When an edge is still blocked at the last round, emit the highest-level candidate and `LOG.debug` once naming the transition id.
  - **The level is decided per fan, not per edge:** edges sharing a fan-out source or fan-in target all take the maximum level any of them needs.

- [ ] **Step 1: Write the failing tests**

```java
@Test
public void allBranchesOfAFanTakeTheSameShapeFamily() throws Exception {
    // One branch would be clear as a straight line while its siblings need an elbow. If they are allowed
    // to differ, the lane-nesting non-crossing argument no longer covers the mixed group.
    ProcessModel model = createProcess();
    ActivityModel s = model.createActivity("S", "S", null);
    model.createActivity("P", "P", null);
    model.createActivity("Q", "Q", null);
    model.createActivity("R", "R", null);
    s.setStarter(true);
    model.createTransition("S", "P");
    model.createTransition("S", "Q");
    model.createTransition("S", "R");

    Document doc = parse(render(model));

    assertNoTransitionsCrossEachOther(doc);
    assertNoTransitionCrossesAnyActivity(doc);
}

@Test
public void anEdgeThatNoCandidateCanClearStillEmitsAPolyline() throws Exception {
    // Source and target boxes overlap on both axes, so every candidate intersects an obstacle. The
    // escalation loop must terminate and emit its last candidate rather than spin.
    ProcessModel model = createProcess();
    ActivityModel a = model.createActivity("A", "A", null);
    ActivityModel b = model.createActivity("B", "B", null);
    ActivityModel c = model.createActivity("C", "C", null);
    a.setStarter(true);
    model.createTransition("A", "C");
    model.createTransition("A", "B");

    Document doc = parse(renderWithoutLayout(model, () -> {
        a.setX(0);  a.setY(0);
        b.setX(10); b.setY(10);
        c.setX(20); c.setY(20);
    }));

    Element poly = findTransitionPolyline(doc, transitionId(model, "A", "C"));
    Assert.assertNotNull("a blocked edge must still be emitted", poly);
    Assert.assertFalse("its points attribute must not be empty", poly.getAttribute("points").isEmpty());
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk`
Expected: `anEdgeThatNoCandidateCanClearStillEmitsAPolyline` FAILs or hangs before the loop is bounded.

- [ ] **Step 3: Implement candidate escalation and post-laning re-validation**

Delete `Type` and the coordinate-arithmetic branches in `classify`; the three candidate levels replace them one-for-one.

- [ ] **Step 4: Run the whole class**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk`
Expected: PASS. Expect waypoint-count assertions to need rewriting here per the Global Constraints.

- [ ] **Step 5: Commit**

```bash
git commit -am "SDK-493 Pick the first candidate route that clears every obstacle, re-checked after laning."
```

---

### Task 6: Group endpoints and backward edges

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java` — `buildPlan`'s skip condition, plus the above-row channel
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1-5.
- Produces: `buildPlan` drops only the `from == null || to == null` and `pf != pt` cases — the `instanceof GroupModel` checks go. An above-row channel family is assigned lanes exactly like the existing below-row channels, and is clamped to the group's child area when the edge belongs to a group container.

- [ ] **Step 1: Write the failing tests**

```java
@Test
public void transitionsIntoAndOutOfAGroupAreRoutedNotFallenBackTo() throws Exception {
    ProcessModel model = createProcess();
    ActivityModel a = model.createActivity("A", "A", null);
    GroupModel g = model.createGroup("G", "G", null);
    model.createActivity("B", "B", null);
    a.setStarter(true);
    ActivityModel h = g.createChildActivity("H", "H", null);
    h.setStarter(true);
    model.createTransition("A", "G");
    model.createTransition("G", "B");

    Document doc = parse(render(model));

    Assert.assertNotNull("A -> G must be routed", findTransitionPolyline(doc, transitionId(model, "A", "G")));
    Assert.assertNotNull("G -> B must be routed", findTransitionPolyline(doc, transitionId(model, "G", "B")));
    assertNoTransitionCrossesAnyGroupBox(doc);
    assertNoTransitionCrossesAnyActivity(doc);
}

@Test
public void aBackwardEdgeArcsAboveTheRowAndCrossesNothing() throws Exception {
    // CommonBfsModelLayouter does not produce loop-back shapes (it places a loop head to the right), so
    // the coordinates are set explicitly -- see spec section 6.5.
    ProcessModel model = createProcess();
    ActivityModel a = model.createActivity("A", "A", null);
    ActivityModel b = model.createActivity("B", "B", null);
    ActivityModel c = model.createActivity("C", "C", null);
    ActivityModel d = model.createActivity("D", "D", null);
    a.setStarter(true);
    model.createTransition("A", "B");
    model.createTransition("B", "C");
    model.createTransition("C", "D");
    model.createTransition("D", "A").setName("retry");

    Document doc = parse(renderWithoutLayout(model, () -> {
        a.setX(0);   a.setY(100);
        b.setX(150); b.setY(100);
        c.setX(300); c.setY(100);
        d.setX(450); d.setY(100);
    }));

    List<double[]> pts = points(findTransitionPolyline(doc, transitionId(model, "D", "A")));
    double topOfRow = activityBoxFor(doc, model, "A")[1];
    Assert.assertTrue("the loop-back run must arc above the row, not below it where the channels are",
        pts.stream().anyMatch(p -> p[1] < topOfRow));
    assertNoTransitionCrossesAnyActivity(doc);
    assertNoTransitionCrossesAnyLabelBand(doc);
}

@Test
public void aBackwardEdgeInsideAGroupStaysInsideTheGroupBox() throws Exception {
    ProcessModel model = createProcess();
    GroupModel g = model.createGroup("G", "G", null);
    ActivityModel p = g.createChildActivity("P", "P", null);
    ActivityModel q = g.createChildActivity("Q", "Q", null);
    p.setStarter(true);
    model.createTransition("P", "Q");
    model.createTransition("Q", "P").setName("back");

    Document doc = parse(render(model));

    assertNoTransitionCrossesAnyGroupBox(doc);
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk`
Expected: all three FAIL — group edges fall back to straight lines, backward edges are `STRAIGHT`.

- [ ] **Step 3: Remove the group-endpoint skip and add the above-row channel**

A backward edge exits the source's top, rises into the channel above the higher of the two bands, runs back and descends into the target's top. It is the only shape a backward edge takes; the Task 4 dominance rule does not apply to it. Clamp the channel to the group's child area for a group-container edge.

- [ ] **Step 4: Run the whole class**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -am "SDK-493 Route edges touching a group and backward edges instead of falling back to a straight line."
```

---

### Task 7: Canvas bounds cover the routed waypoints

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactory.java` — `getAdditionalBounds` (line 154)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineProcessDiagramFactoryTest.java`

**Interfaces:**
- Consumes: `Route.waypoints`.
- Produces: `getAdditionalBounds` reports the label extents it reports today **plus** the routed waypoints, so no routed geometry falls outside the canvas.

- [ ] **Step 1: Write the failing test**

```java
@Test
public void routedWaypointsStayInsideTheCanvas() throws Exception {
    // An above-row channel sits above every activity and a bottom attachment extends below the lowest
    // label band, so bounds derived from labels alone clip them.
    ProcessModel model = createProcess();
    ActivityModel a = model.createActivity("A", "A", null);
    ActivityModel b = model.createActivity("B", "B", null);
    ActivityModel c = model.createActivity("C", "C", null);
    a.setStarter(true);
    model.createTransition("A", "B");
    model.createTransition("B", "C");
    model.createTransition("C", "A").setName("retry");

    Document doc = parse(renderWithoutLayout(model, () -> {
        a.setX(0);   a.setY(100);
        b.setX(150); b.setY(100);
        c.setX(300); c.setY(100);
    }));

    double[] canvas = canvasBox(doc);
    for (Element poly : allTransitionPolylines(doc)) {
        for (double[] p : points(poly)) {
            Assert.assertTrue("waypoint " + p[0] + "," + p[1] + " falls outside the canvas",
                p[0] >= canvas[0] && p[1] >= canvas[1] && p[0] <= canvas[2] && p[1] <= canvas[3]);
        }
    }
}
```

Add the helpers `canvasBox(Document)` (from the root `<svg>`'s `width`/`height`, origin `0,0`) and `allTransitionPolylines(Document)`.

- [ ] **Step 2: Run it and watch it fail**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest#routedWaypointsStayInsideTheCanvas -pl njams-sdk`
Expected: FAIL — the loop-back arc sits above the canvas top.

- [ ] **Step 3: Include the waypoints in `getAdditionalBounds`**

- [ ] **Step 4: Run the whole class, then the whole module**

Run: `mvn test -Dtest=PolylineProcessDiagramFactoryTest -pl njams-sdk` then `mvn test -pl njams-sdk`
Expected: PASS both. `NjamsProcessDiagramFactoryTest` must be untouched — the default rendering does not change.

- [ ] **Step 5: Commit**

```bash
git commit -am "SDK-493 Size the canvas to the routed waypoints, not to the labels alone."
```

---

### Task 8: Documentation

**Files:**
- Modify: `wiki/Polyline-Process-Diagram-Routing.md` — the "enters from the side — never vertically from below" claim (~line 43) and the two limitation bullets (~lines 82-84)
- Modify: `wiki/polyline-routing/bypass-before.svg`, `bypass-after.svg`, `fanout-before.svg`, `fanout-after.svg`, `routing-model.svg`

**Interfaces:**
- Consumes: the behaviour from Tasks 4-6.
- Produces: nothing other tasks read.

- [ ] **Step 1: Update the page**

Reverse all three claims: edges attach on all four sides, edges touching a group are routed, backward edges are routed. Keep the cross-container and self-transition limitations — those stay out of scope.

- [ ] **Step 2: Redraw the five SVGs to match the new routing model**

Per `.claude/rules/docs-superpowers-lifecycle.md`, leave the duplicated copies under `docs/superpowers/specs/sdk-452-polyline-routing/` untouched — they belong to the SDK-452 spec.

- [ ] **Step 3: Commit**

```bash
git add wiki/Polyline-Process-Diagram-Routing.md wiki/polyline-routing
git commit -m "SDK-493 Update the routing wiki page and its illustrations to the new routing model."
```

---

### Task 9: A rendering test that writes the scenarios out for visual verification

The invariant assertions prove no edge crosses a box; they cannot show whether the result *reads* well. This task adds a test that renders every scenario this ticket changes and writes each one to disk, so the SVGs can be opened and looked at. It lives in `njams-sdk`'s own test sources — the sample client is a reference for people writing their own client and must not grow diagnostic tooling. **Per the user's global instruction, do not screenshot or judge the output yourself — hand the paths over and ask for a visual check.**

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineDiagramSampleRenderingTest.java`

**Interfaces:**
- Consumes: `PolylineProcessDiagramFactory` and `NjamsProcessDiagramFactory`; `CommonBfsModelLayouter`; the `createProcess()` / `renderWithoutLayout(...)` fixture idiom from `PolylineProcessDiagramFactoryTest` (duplicated here, not shared — the two classes are independent).
- Produces: nothing other tasks read. Test code, so `code-quality-general.md` and `public-api-design.md` do not apply (`.claude/rules/testing-conventions.md`).

- [ ] **Step 1: Write the test**

One `@Test` per scenario. Each renders the model through **both** factories and writes `<scenario>-default.svg` and `<scenario>-polyline.svg` into `target/polyline-samples/` (resolved from the module directory, not an absolute path), then asserts the poly-line SVG is non-empty and parses — so the test is a real smoke test of the scenario and not just a file dump. Scenarios, one per behaviour this ticket changes:

| scenario | model |
|---|---|
| `bypass-offset` | `X -> A -> Y` plus `X -> Y`, with `Y` placed 7px lower than `X` |
| `bypass-over-group` | `A -> G -> B` plus `A -> B`, `G` holding one child |
| `group-endpoints` | `A -> G -> B`, showing the routed group edges |
| `vertical-approach-down` | `A -> B` with `B` 300px directly below |
| `vertical-approach-up` | `A -> B` with `B` 300px directly above |
| `backward-edge` | `A -> B -> C -> D` plus `D -> A`, coordinates placed explicitly |
| `fan-out-fan-in` | `S -> P/Q/R -> T`, the existing lane-nesting shape, as the regression eyeball |

Place coordinates explicitly where the scenario says so; otherwise run `CommonBfsModelLayouter`. A `@BeforeClass` creates the output directory; an `@AfterClass` prints its absolute path once.

- [ ] **Step 2: Run it**

Run: `mvn test -Dtest=PolylineDiagramSampleRenderingTest -pl njams-sdk`
Expected: PASS, and fourteen `.svg` files under `njams-sdk/target/polyline-samples/`.

- [ ] **Step 3: Run the whole module once more**

Run: `mvn test -pl njams-sdk`
Expected: PASS — the new class must not disturb the suite, and writing into `target/` must not depend on test order.

- [ ] **Step 4: Hand the files to the user for visual verification**

Report the absolute output directory and the file list, and ask them to open the `-polyline.svg` files (comparing against their `-default.svg` counterparts) and confirm the routing reads correctly. **Wait for their answer — do not judge the images yourself, and do not close the ticket on a green test suite alone.**

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/model/svg/PolylineDiagramSampleRenderingTest.java
git commit -m "SDK-493 Render the routing scenarios to SVG files from a test, for visual checking."
```
