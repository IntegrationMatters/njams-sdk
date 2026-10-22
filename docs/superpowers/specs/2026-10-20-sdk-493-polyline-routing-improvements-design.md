# SDK-493 — Poly-line process-diagram routing improvements (Design)

**Ticket:** SDK-493 — Poly-line process-diagram routing improvements
**Fix version:** 6.1.0
**Supersedes parts of:** `2026-06-18-sdk-452-polyline-routing-spec.md` (kept as the historical record of the
original design; where the two disagree, this document wins)

## 1. Goal

`PolylineProcessDiagramFactory` routes transition edges around activities so that no arrow overlaps a node.
It does this correctly only for a narrow family of layouts. This work makes the router decide from the actual
geometry of the diagram — real boxes, real connector points — instead of from exact coordinate equality, and
extends it to the two edge families it currently refuses to route at all (edges touching a group, and edges
looping back to an earlier step).

## 2. Problem

Three reported symptoms, one root cause.

### 2.1 A bypass detour is only applied when the endpoints share an exact y

`classify` derives a logical grid from the distinct `x`/`y` values of the transitions' endpoints and compares
row *indices*:

```java
int rowS = sortedY.indexOf(from.getY());
int rowT = sortedY.indexOf(to.getY());
...
} else if (rowS == rowT) {
    e.type = Type.BYPASS;
```

If the target's `y` differs by a single pixel the edge can never become a `BYPASS`; it falls through to
`ELBOW`, whose final run is drawn horizontally at the target's own y straight into the target — passing across
any activity that happens to sit at nearly that height. The obstacle probe has the same flaw:
`hasSiblingBetween` only recognises a sibling when `sibling.getY() == rowY`.

Reported shape, which renders correctly today:

```
--+--> A ---> B
  |           ^
  +-----------+
```

The same shape with `B` a little higher or lower renders as a direct line through `A`.

### 2.2 The offset is systematic for groups

A group is taller than a plain activity and the base factory anchors a group endpoint at its vertical centre
(`NjamsProcessDiagramFactory.getTransitionCoordinates`: `group.getY() + group.getHeight() / 2`), whereas a
plain activity is anchored at `getY() + 25`. Two nodes laid out on the same visual band therefore have
different connector y values whenever one of them is a group — so 2.1 fires for essentially every diagram
containing a group.

### 2.3 Edges touching a group are not routed at all

`buildPlan` drops them before routing:

```java
if (from == null || to == null || from instanceof GroupModel || to instanceof GroupModel) {
    continue;
}
```

`drawTransition` then falls back to `super.drawTransition`, i.e. the inherited straight line, with the overlap
consequences that the poly-line factory exists to prevent.

### 2.4 Backward edges are never routed

`classify` sends every edge whose target lies in an earlier column (`colT < colS`) to `Type.STRAIGHT`, so a
loop back to an earlier step is a plain centre-to-centre line and can cross everything between the two steps.

### 2.5 Edges can only attach left and right

Every routed edge leaves its source horizontally to the right and enters its target horizontally from the left
(`buildRoute`, `BYPASS` and `ELBOW` branches). An edge arriving from well above or below is therefore bent
around into the target's left flank instead of descending into it.

## 3. Goals, non-goals, anti-goals

**Goals**

- A routed edge never crosses an activity box, a group box, or an activity's label band, whatever the vertical
  offset between its endpoints.
- Edges whose source or target is a group are routed like any other edge.
- Backward (loop-back) edges are routed.
- Edges attach on the side that matches their approach direction, including top and bottom.

**Non-goals**

- Changing `NjamsProcessDiagramFactory` (the default straight-line rendering) in any observable way.
- Changing activity placement. Layout stays entirely the layouter's business; only edges are routed.
- Changing the opt-in mechanism or any public/protected member of either factory.
- Changing label wrapping, truncation, tooltip behaviour, or the `legacyServerCompat` label suppression.
- Routing **cross-container** edges — a transition between a root-level activity and an activity inside a
  group. `buildPlan` drops these today via its `pf != pt` check, separately from the group-endpoint check this
  work fixes, and that stays. Such transitions do not occur in practice (confirmed by the ticket owner), and
  routing them would require an obstacle model spanning two containers.
- Routing **self-transitions** (source and target are the same activity). They stay the degenerate straight
  line they are today; the default layouter does not support them either. *(Builder's call, recorded here so
  it is a decision rather than an omission.)*

**Anti-goals** (ways this could technically work and still be a failure)

- A second, parallel routing mechanism living next to the current one. The existing lane/nesting/label
  machinery is correct and tested; this work generalises it rather than growing a sibling implementation.
- Trading a fixed overlap for a new one — e.g. gaining top/bottom attachment at the cost of arrows running
  through label text.
- Geometry that is right for the test fixtures and fragile for real layouts, i.e. re-introducing
  coordinate-equality reasoning anywhere.

## 4. Constraints

- `PolylineProcessDiagramFactory` is public, opt-in API; its constructors and overridden `protected` methods
  stay as they are. Everything this work adds is `private`.
- The SVG consumed by nJAMS Server keeps its current element shape: `<polyline>` with `modelId`, `markerId`,
  `name`, `points`, `marker-end`, and the existing `orient="auto"` arrow marker.
- Routing runs once per process model when the project message is built, not on the runtime monitoring path,
  so clarity is preferred over micro-optimisation — within reason, since a diagram can hold many activities.
- Existing behaviour that the current test suite protects (fan-out/fan-in lane nesting, stagger against colour
  occlusion, label placement away from nodes and sibling rails, canvas sizing for labels) must survive.

## 5. Decisions taken by the ticket owner

1. **Four-side attachment applies to any edge whose approach is more vertical than horizontal**, not only to
   edges that sit exactly above or below their target.
2. **A bottom attachment ends at the lower edge of the target's label band**, arrow pointing up at the label,
   rather than running through the label to the icon:

   ```
         #icon#
       Icon's Label
           ^
       ----+
   ```
3. **Backward edges are in scope** and arc **above** their row, not below — below is where the bypass channels
   and the activity labels already are.
4. **Existing tests may be adjusted** where they pin geometry this change deliberately replaces (for example
   "a routed BYPASS has exactly 6 waypoints"), provided the behavioural invariant each test protects is kept
   and still asserted.
5. **A new spec is written rather than amending the SDK-452 spec**, which stays untouched as the record of the
   original design.
6. **The strip below an activity's label band is treated as free.** `drawActivity` carries a second,
   currently commented-out text element (`*_stats_text`) one text-line below the label, and nJAMS Server may
   overlay additional information in that area. An arrow terminating at the label band's lower edge is
   accepted as crossing that area for now.
7. **Cross-container transitions are out of scope** because they do not occur in practice — see §3.
8. **The wiki's routing illustrations are redrawn** as part of this work, not left as the SDK-452-era
   drawings — see §8.

## 6. Design

### 6.1 Everything becomes a box

Routing works from a node geometry value per endpoint and per obstacle, replacing the raw `getX()`/`getY()`
reads scattered through `classify`, `rowBottom`, `widthAtColumn`, `nextColumn`, `prevColumn` and
`hasSiblingBetween`:

| | plain activity | group |
|---|---|---|
| box | `x, y, 50, 50` | `x, y, getWidth(), getHeight()` |
| connector centre | `x + 25, y + 25` | `x + width/2, y + height/2` |
| obstacle extent | box plus the label band below it | the box (a group's name is drawn inside its header, so there is no band below it) |

The label band is the strip directly below an activity icon where `drawActivity` writes the activity name:
`DEFAULT_ACTIVITY_SIZE + DEFAULT_TEXT_SIZE` below the icon top, plus descender room. The existing test helper
treats it as 22px; the production constant should be derived from `DEFAULT_TEXT_SIZE` rather than hard-coded
to 22, and the test helper then follows the production value.

The connector centre for a group reproduces what the base factory already does, so a straight edge into a
group is unchanged in appearance.

### 6.2 Obstacle detection is segment-versus-box intersection

A candidate route is acceptable when none of its segments intersects any obstacle extent in the same
container, excluding the edge's own two endpoints, inflated by a small clearance margin. This replaces both
the row-identity test in `classify` and `hasSiblingBetween`. The test suite already contains a
`segmentIntersectsRect` implementation that the production code can mirror; production and test must not share
a copy — the test keeps its own, so it independently verifies the result.

Rows and columns are not abandoned: they remain the grouping key for lanes (which edges share a gutter or a
channel). They stop being used to decide *whether* something is in the way.

Because connector centres no longer line up once groups take part (§2.2), the lane grouping key cannot stay
exact top-y identity either — two near-but-unequal rows would get channel bases a few pixels apart, i.e.
visually coincident runs with no lane separation. **Rows and columns become overlap bands:** nodes whose boxes
overlap on the perpendicular axis belong to the same band, and a band's channel is derived from its lowest
bottom edge (today's `rowBottom`, generalised). *(Builder's call; recorded here because the obvious reading —
cluster by connector centre — would instead split a group and the plain activities beside it into different
bands.)*

Inside a group container, the group's own border and header count as obstacles for its children's edges: a
route belonging to a group stays within that group's child area, with its corridors clamped to it. Without
this, an above-row channel or a bottom attachment can leave the group box, which
`assertNoTransitionCrossesAnyGroupBox` — which checks every transition against every group box, including its
own parent — would flag. *(Builder's call.)*

### 6.3 Attachment side follows the approach direction

Per edge, compare `|dx|` and `|dy|` between the two connector centres:

- **Horizontally dominant** (`|dx| >= |dy|`): source exits right, target is entered from the left — today's
  behaviour. Backward edges are the exception and always follow §6.5 instead.
- **Vertically dominant** (`|dy| > |dx|`): source exits bottom (target below) or top (target above), target is
  entered from the top (approach from above) or bottom (approach from below).

The attachment point on each side:

| side | plain activity | group |
|---|---|---|
| left / right / top | the point on the `DEFAULT_ACTIVITY_RADIUS` circle in that direction, i.e. 5px outside the icon — unchanged from today | the box edge |
| bottom | the lower edge of the label band, centred on the icon | the box edge |

As today, the endpoint carrying the arrowhead is pulled back by `DEFAULT_MARKER_SIZE` so the arrow tip lands
on the attachment point. `radiusPoint` keeps working unchanged for the circle cases; the box and label-band
cases need their own small helpers.

### 6.4 Route shapes

The orientation chosen in 6.3 fixes the shape family:

- **Horizontally dominant:** horizontal — vertical — horizontal (today's `ELBOW`), degenerating to a single
  straight segment when the two centres are aligned and nothing is in the way.
- **Vertically dominant:** vertical — horizontal — vertical, the mirror image, with the horizontal run placed
  in a channel between rows.

The vertical run of a horizontally dominant edge sits in a gutter between columns; the horizontal run of a
vertically dominant edge sits in a channel between rows. Both are the structures the current code already
builds (`assignElbowGutter`, `channelY`), used on the other axis.

### 6.5 Backward edges

An edge whose target box lies left of its source box exits the source's **top**, rises into the channel above
the higher of the two bands, runs back, and descends into the target's **top**. Above-row channels are a new
corridor family, assigned lanes exactly like the existing below-row channels. This is the only shape a
backward edge takes; §6.3's dominance rule does not apply to it.

The default `CommonBfsModelLayouter` states it does not handle feedback loops, and its max-column rule places
a loop head to the *right* of the node looping back to it, so a cyclic model laid out by it does not reliably
produce the "loop back to an earlier step" shape. The backward-edge fixture (§7.5) therefore places
coordinates explicitly rather than relying on that layouter. *(Builder's call.)*

### 6.6 Candidate escalation

For each edge, the router takes the first candidate that clears every obstacle:

1. the direct straight segment between the two connector centres;
2. the shape from 6.4/6.5 with its corridor at the natural position — the gutter beside the source for a
   fan-out or plain edge, the gutter beside the target for a pure fan-in, as `assignElbowGutter` decides
   today;
3. the same shape with its run displaced into a detour corridor — the below-row channel for a forward edge
   (today's `BYPASS` and the `rowDetour` variant), the above-row channel for a backward edge.

This is the generalisation of today's `STRAIGHT` / `BYPASS` / `ELBOW` classification: the same three outcomes,
selected by whether the geometry is actually clear rather than by coordinate arithmetic.

**The clearance check runs against the final geometry, not the nominal corridor.** Lanes and node staggering
are assigned after classification and displace a run by `lane * LANE_GAP` and the exit/entry y by
`nodeStagger`, so a candidate validated at its base position is not what gets emitted — a channel accepted at
its base y can end up tens of pixels lower, inside the next band's icons. Today this cannot bite because
clearance never drove the choice; under §6.6 it would silently defeat the headline goal, which is exactly
what the tests assert on the emitted points. The router therefore validates after laning and re-escalates any
edge that fails, repeating a bounded number of times. *(Builder's call on the loop's exact shape.)*

**All edges of one fan take the same shape family.** Non-crossing between sibling branches is not checked
geometrically today; it falls out of the lane-nesting rules (`elbowLaneKey`, fan-out longest-run-outermost,
fan-in innermost), and that argument only holds while every branch of a fan has the same shape. Per-edge
clearance could otherwise give one branch a straight line, another an H-V-H elbow and a third an above-row
arc, and the nesting guarantee would no longer cover the mixed group — breaking
`fanOut_branchesDoNotCrossEachOther`, `convergence_branchesDoNotCrossEachOther` and
`fanOut_directLabelDoesNotOverlapSiblingElbowLines`, which §7 keeps as contract. The shape family is therefore
decided for the fan as a whole; edge-vs-edge intersection does **not** become part of the §6.2 obstacle test.
*(Builder's call.)*

If no candidate is clear, the router takes the last one and logs at debug level, mirroring the existing
"log the truncation rather than silently overlap" rule from the SDK-452 design.

### 6.7 What is kept unchanged

- Lane assignment and its nesting rules: fan-out puts the longest run outermost, fan-in innermost, the stagger
  that keeps a server-coloured executed edge from being hidden behind unexecuted siblings, and the
  bypass/fan-in gutter claim that stops two mechanisms landing on the same x.
- Label placement rules: anchor on a clear axis-aligned run, hug the unique end for fan-out/fan-in, grow a
  wrapped block upward rather than down. A vertically dominant edge anchors its label on its horizontal run,
  which is the same rule the bypass channel run already uses.
- `wrapLabel`, truncation, the tooltip attribute, and label suppression under `legacyServerCompat`.

### 6.8 Canvas bounds

`getAdditionalBounds` currently reports label extents only. An above-row channel can sit above every activity,
and a bottom attachment extends below the label band, so the reported bounds must also cover the routed
waypoints themselves — otherwise the canvas clips them. *(Builder's call on the exact form; the requirement is
that no routed geometry falls outside the canvas.)*

## 7. Testing approach

`PolylineProcessDiagramFactoryTest` already asserts the right invariants — no transition crosses an activity
box, a group box, or a label band; branches do not cross each other; labels do not overlap nodes or sibling
rails. Those assertions are the contract and must keep passing. New fixtures to add:

1. The reported shape with the target offset vertically by a few pixels — the regression guard for 2.1.
2. A bypass whose target is a group (connector below the plain activities' line) — the regression guard for
   2.2.
3. A transition into and out of a group, asserting both are routed as polylines rather than falling back.
4. A vertically dominant edge, asserting it attaches at the target's top, and one arriving from below,
   asserting it ends at the label band's lower edge and does not enter the band.
5. A loop-back edge over several columns, asserting it runs above the band and crosses nothing. Its
   coordinates are placed explicitly rather than produced by `CommonBfsModelLayouter` — see §6.5.

Per decision 5.4, tests that pin replaced geometry (waypoint counts, specific corridor x values) may be
rewritten to assert the invariant instead of the exact shape. Note also that growing the canvas shifts
`startX`/`startY`, so assertions on absolute coordinates may move even where the routing is unchanged.

## 8. Documentation

`wiki/Polyline-Process-Diagram-Routing.md` currently states that a routed edge "enters the target from the
side — never vertically from below", and lists edges touching a group and loop-back edges among its
limitations. All three are reversed by this work, so the page is updated as part of it (see
`.claude/rules/wiki-drafts.md`).

The five illustrative SVGs under `wiki/polyline-routing/` draw the old routing model and are **redrawn** to
match. Per `.claude/rules/docs-superpowers-lifecycle.md`, the copies duplicated into
`docs/superpowers/specs/sdk-452-polyline-routing/` belong to the SDK-452 spec and stay as they are — that
spec remains the record of the original design.

## 9. Left to the builder

- Internal decomposition: how the node geometry, the obstacle set, and the candidate generator are factored
  into classes or methods inside `PolylineProcessDiagramFactory`. Everything stays private to the factory.
- The clearance margin's value, and whether it differs for boxes and label bands.
- Whether the three candidates are generated eagerly or lazily.
- The exact derivation of the label-band height from `DEFAULT_TEXT_SIZE`.
- Whether `getAdditionalBounds` reports waypoints individually or as one route bounding box.
- The bounded re-escalation loop's exact shape (§6.6), the band tolerance (§6.2), and how a group's inner
  corridors are clamped (§6.2).

## 10. Compatibility

No public or protected member changes. The emitted SVG keeps the same element and attribute shape, so no nJAMS
Server-side change is implied and the message format is untouched. The `breaking-change` label is expected to
stay off; `njams-ticket-finish` decides against the real diff.
