# Poly-line process-diagram routing

`PolylineProcessDiagramFactory` is an **opt-in** alternative to the default `NjamsProcessDiagramFactory`. It renders
transitions as **routed poly-lines** instead of straight lines, so that no transition arrow overlaps an activity icon,
its label, or another arrow.

The default factory draws every transition as a straight line between activity centres. For several common
control-flow shapes that produces overlapping or node-crossing arrows that are hard to read — most visibly a *stepless
bypass* (a direct fork-to-convergence transition with no intermediate activity), but also choice fan-out and
convergence. The poly-line factory routes each edge through the free space between rows and columns to avoid this.

The default rendering is unchanged; you choose the poly-line rendering explicitly per client.

## Enabling it

Set the factory on the `Njams` instance before starting:

```java
njams.model().setDiagramFactory(new PolylineProcessDiagramFactory(njams));
```

Activity placement still comes from the configured `ProcessModelLayouter` (by default
[`CommonBfsModelLayouter`](CommonBfsModelLayouter-Algorithm)); the poly-line factory only changes how the transitions
between the placed activities are drawn. No other configuration is required.

> **Server compatibility:** the routed SVG uses `<polyline>` elements for transitions (carrying the same `modelId`,
> marker and name attributes as the default `<line>` transitions). It is rendered correctly by current nJAMS Server
> versions.

## How routing works

The factory reconstructs a column/row grid from the already-placed activities and routes each transition through the
**gutters** between columns and the **channels** between rows, keeping clear of every activity icon and of the label
text drawn beneath each icon.

![Routing model](polyline-routing/routing-model.svg)

Per-edge rules:

- **Adjacent activities on the same row** → a straight segment (unchanged).
- **Stepless bypass** (a same-row edge that skips a column) → the edge leaves the source on its right, drops into a
  channel placed **below the label band**, runs across beneath the intermediate activities, and enters the target
  from the side.
- **Same-column skip** (a vertical edge that skips an activity below it) → the mirror of the bypass: the edge leaves
  the source sideways, runs down a column gutter and comes back in. A detour through a row channel cannot help here,
  because both ends share an x.
- **Row-changing edges** (fan-out / convergence) → an orthogonal elbow through the column gutter, so each branch travels
  in its own lane. When the approach is more vertical than horizontal, the edge instead leaves the source on the side
  facing its target — downwards onto the target's top edge, or upwards onto the lower edge of the target's label band —
  and crosses in a row channel.
- **Backward edges** (an edge running right to left, against the flow) → the edge leaves the source upwards, runs back
  in a channel **above** the row, and comes down onto the target's top edge.
- An edge arriving from below ends at the **lower edge of the target's label band**, so the arrow never runs through
  the label.
- Edges that share a gutter or channel are given distinct **lanes** so parallel runs never coincide, and two edges
  leaving one activity are offset from each other so their first runs never lie on top of one another — overlapping
  segments would hide a transition the server has coloured as executed behind an unexecuted one. All branches of one
  fan take the same shape, so their lanes nest instead of crossing.
- Inside a group, every channel stays between the group's borders, with room left for the label a channel carries
  beneath it.
- Rows and columns are **overlap bands**, not exact coordinates: a group and the activities beside it count as one row
  even though their boxes start at different heights.
- A chosen route is checked against the actual activity boxes **after** lanes are applied, and moved into the next
  corridor if the displaced run would still cross something.

## Examples

### Stepless bypass

| Default (straight) | Poly-line |
|---|---|
| ![bypass before](polyline-routing/bypass-before.svg) | ![bypass after](polyline-routing/bypass-after.svg) |

With straight lines the bypass `X→Y` runs through the intermediate activity `A` and along the main-path arrows, and its
label collides with `A`. The routed version sends the bypass through a channel below the label band and into `Y` from
the side — no node, arrow or label is crossed.

### Choice fan-out

| Default (straight) | Poly-line |
|---|---|
| ![fanout before](polyline-routing/fanout-before.svg) | ![fanout after](polyline-routing/fanout-after.svg) |

Branches share vertical rails and each travels in its own lane.

## Transition labels

Transition labels are placed on a clear run of the routed edge rather than at the straight-line midpoint:

- bypass labels sit on the horizontal channel run (below the label band);
- for **fan-out**, each label is right-aligned just left of its target (growing left into the long approach), so labels
  separate by row and never spill onto the target icon;
- for **convergence (fan-in)**, labels are left-aligned just right of their source;
- otherwise the label is centred on the longer horizontal run.

Existing label wrapping/truncation and the server-compatibility label-suppression rule are reused unchanged.

## Limitations

- Self-loops and multiple independent start activities are not specially handled.
- Transitions between two different containers fall back to straight-line drawing. Edges touching a group border are
  routed like any other edge, meeting the group on its box edge where the route reaches it — so a fan into or
  out of a group keeps one entry point per branch, like a fan on a plain activity.
- In a group too tight for the channel a route would naturally take, the route is pushed against the group's border
  rather than given a gap of its own, so it can end up close to an activity.
