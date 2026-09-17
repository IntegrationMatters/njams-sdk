# CommonBfsModelLayouter — Algorithm Documentation

`CommonBfsModelLayouter` assigns absolute `x`/`y` coordinates to every `ActivityModel` and sizes every `GroupModel` to contain its children. It runs in two passes: a depth-first group-sizing pass followed by a top-down placement pass.

---

## Constants

| Constant | Value | Role |
|---|---|---|
| `HORIZONTAL_STEP` | 150 px | Horizontal distance between activity centres in adjacent columns |
| `VERTICAL_STEP` | 100 px | Vertical distance between activity centres in adjacent rows |
| `ACTIVITY_SIZE` | 50 px | Width and height of a plain activity icon |
| `GROUP_PADDING` | 20 px | Padding inside a group between the group border and its children (all sides) |
| `GROUP_HEADER_HEIGHT` | 20 px | Height of the header band drawn above the child area |
| `GROUP_MARGIN_HORIZONTAL` | 50 px | Additional horizontal margin on each side (outside the padding) |
| `GROUP_MARGIN_BOTTOM` | 10 px | Additional margin at the bottom (outside the padding) |

---

## Pass 1 — Group Sizing

Runs depth-first (innermost groups first). Each group's `width` and `height` are computed from a trial layout of its children so that parent groups can in turn be sized correctly.

For a group with a non-empty child layout:

```
group.width  = grid.totalWidth()  + 2 × GROUP_PADDING + 2 × GROUP_MARGIN_HORIZONTAL
group.height = grid.totalHeight() + GROUP_HEADER_HEIGHT + 2 × GROUP_PADDING + GROUP_MARGIN_BOTTOM
```

For an empty group, minimum dimensions apply using `HORIZONTAL_STEP` and `VERTICAL_STEP` instead of the grid totals.

See [Group Layout](#group-layout) for a visual breakdown of the dimensions.

---

## Pass 2 — Placement

For each container (the root process or a group), a column/row grid is computed and absolute coordinates are written to every child activity. The pass then recurses into nested groups.

### 2.1  Column Assignment

BFS from the start activity assigns a column to every node. The **max-column rule** ensures correct placement at convergence points: a node's column is the maximum column among all its predecessors, plus one.

```
column[node] = max(column[predecessor] for all predecessors) + 1
```

This places every node strictly to the right of all predecessors and groups all nodes at the same dependency depth into the same column.

#### Linear chain

<svg xmlns="http://www.w3.org/2000/svg" width="580" height="125">
  <defs>
    <marker id="ca1" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="20" width="580" height="95" fill="#f5f7ff" rx="3"/>
  <text x="80"  y="13" text-anchor="middle" font-size="10" fill="#888" font-family="sans-serif">col 0</text>
  <text x="230" y="13" text-anchor="middle" font-size="10" fill="#888" font-family="sans-serif">col 1</text>
  <text x="380" y="13" text-anchor="middle" font-size="10" fill="#888" font-family="sans-serif">col 2</text>
  <text x="530" y="13" text-anchor="middle" font-size="10" fill="#888" font-family="sans-serif">col 3</text>
  <line x1="106" y1="67" x2="204" y2="67" stroke="#444" stroke-width="1.5" marker-end="url(#ca1)"/>
  <line x1="256" y1="67" x2="354" y2="67" stroke="#444" stroke-width="1.5" marker-end="url(#ca1)"/>
  <line x1="406" y1="67" x2="504" y2="67" stroke="#444" stroke-width="1.5" marker-end="url(#ca1)"/>
  <rect x="55"  y="42" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="80"  y="71" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">Start</text>
  <rect x="205" y="42" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="71" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">A</text>
  <rect x="355" y="42" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="380" y="71" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">B</text>
  <rect x="505" y="42" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="530" y="71" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">End</text>
</svg>

Each node has one predecessor, so its column is simply the predecessor's column + 1.

#### Convergence — max-column rule

When two branches merge, the join node is placed in the column after the **deepest** predecessor, not just any predecessor. This guarantees sufficient horizontal space for both incoming paths.

<svg xmlns="http://www.w3.org/2000/svg" width="580" height="235">
  <defs>
    <marker id="ca2" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <text x="80"  y="13" text-anchor="middle" font-size="10" fill="#888" font-family="sans-serif">col 0</text>
  <text x="230" y="13" text-anchor="middle" font-size="10" fill="#888" font-family="sans-serif">col 1</text>
  <text x="380" y="13" text-anchor="middle" font-size="10" fill="#888" font-family="sans-serif">col 2</text>
  <text x="530" y="13" text-anchor="middle" font-size="10" fill="#888" font-family="sans-serif">col 3</text>
  <rect x="0" y="20"  width="580" height="100" fill="#eef2ff" rx="3"/>
  <rect x="0" y="120" width="580" height="100" fill="#fff8ed" rx="3"/>
  <text x="4" y="75"  font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <text x="4" y="175" font-size="10" fill="#aaa" font-family="sans-serif">row 1</text>
  <line x1="106" y1="70"  x2="204" y2="70"  stroke="#444" stroke-width="1.5" marker-end="url(#ca2)"/>
  <line x1="256" y1="70"  x2="354" y2="70"  stroke="#444" stroke-width="1.5" marker-end="url(#ca2)"/>
  <line x1="256" y1="70"  x2="354" y2="170" stroke="#444" stroke-width="1.5" marker-end="url(#ca2)"/>
  <line x1="406" y1="70"  x2="504" y2="70"  stroke="#444" stroke-width="1.5" marker-end="url(#ca2)"/>
  <line x1="406" y1="170" x2="504" y2="70"  stroke="#444" stroke-width="1.5" marker-end="url(#ca2)"/>
  <rect x="55"  y="45"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="80"  y="74"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">Start</text>
  <rect x="205" y="45"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="74"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">A</text>
  <rect x="355" y="45"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="380" y="74"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">B</text>
  <rect x="355" y="145" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="380" y="174" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">C</text>
  <rect x="505" y="45"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="530" y="74"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">D</text>
  <text x="80"  y="115" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(c=0)</text>
  <text x="230" y="115" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(c=1)</text>
  <text x="380" y="115" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(c=2)</text>
  <text x="380" y="215" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(c=2)</text>
  <text x="530" y="115" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(c=3) ← max(2,2)+1</text>
</svg>

`D` has two predecessors, both at column 2. `max(2, 2) + 1 = 3`. Even if one branch were longer (reaching column 3 while the other is at column 2), `D` would still be placed at `max(3, 2) + 1 = 4`, guaranteeing enough room.

---

### 2.2  Row Assignment

Rows are assigned in **BFS traversal order** (all predecessors are always assigned before any successor, since predecessors are in earlier columns). For each node the algorithm:

1. Computes a **desired row** — the minimum row among its already-assigned predecessors, or `0` if none exist.
2. Resolves **conflicts** — if the desired row is occupied in the target column, it searches for the nearest free row: upward first (`desired − 1`, `desired − 2`, … down to `0`), then downward (`desired + 1`, `desired + 2`, …).

```
predecessors ← invert(successors map from transitions)
occupied     ← Map<col, Set<row>>
rowOf        ← Map<id, row>

for each node in BFS traversal order:
    col        ← columnOf[node]
    preds      ← predecessors[node] ∩ domain(rowOf)   // all assigned predecessors
    desired    ← min(rowOf[p] for p in preds), or 0 if preds is empty
    assigned   ← nearestFreeRow(desired, occupied[col])
    rowOf[node]  ← assigned
    occupied[col].add(assigned)

nearestFreeRow(desired, occupied):
    if desired ∉ occupied → return desired
    for delta = 1, 2, 3, …:
        above = desired − delta
        if above ≥ 0 and above ∉ occupied → return above   // prefer above
        if desired + delta ∉ occupied     → return desired + delta
```

#### Example 1 — Linear flow

**Topology:** `Start → A → B → End`

Every node has one predecessor and inherits its row. The full sequence stays on row 0.

<svg xmlns="http://www.w3.org/2000/svg" width="580" height="120">
  <defs>
    <marker id="r1" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="15" width="580" height="100" fill="#eef2ff" rx="3"/>
  <text x="4" y="69" font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <line x1="106" y1="65" x2="204" y2="65" stroke="#444" stroke-width="1.5" marker-end="url(#r1)"/>
  <line x1="256" y1="65" x2="354" y2="65" stroke="#444" stroke-width="1.5" marker-end="url(#r1)"/>
  <line x1="406" y1="65" x2="504" y2="65" stroke="#444" stroke-width="1.5" marker-end="url(#r1)"/>
  <rect x="55"  y="40" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="80"  y="69" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">Start</text>
  <rect x="205" y="40" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="69" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">A</text>
  <rect x="355" y="40" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="380" y="69" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">B</text>
  <rect x="505" y="40" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="530" y="69" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">End</text>
  <text x="80"  y="110" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="230" y="110" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="380" y="110" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="530" y="110" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
</svg>

#### Example 2 — Chain keeps its row after a branch

**Topology:** `Start → A` and `Start → B → C → D`

`Start` branches to `A` (first successor, row 0) and `B` (second successor; row 0 is taken → row 1). `C` inherits `B`'s row 1. `D` inherits `C`'s row 1. The chain `B → C → D` stays on row 1.

<svg xmlns="http://www.w3.org/2000/svg" width="580" height="225">
  <defs>
    <marker id="r2" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="15"  width="580" height="100" fill="#eef2ff" rx="3"/>
  <rect x="0" y="115" width="580" height="100" fill="#fff8ed" rx="3"/>
  <text x="4" y="69"  font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <text x="4" y="169" font-size="10" fill="#aaa" font-family="sans-serif">row 1</text>
  <line x1="106" y1="65"  x2="204" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#r2)"/>
  <line x1="106" y1="65"  x2="204" y2="165" stroke="#444" stroke-width="1.5" marker-end="url(#r2)"/>
  <line x1="256" y1="165" x2="354" y2="165" stroke="#444" stroke-width="1.5" marker-end="url(#r2)"/>
  <line x1="406" y1="165" x2="504" y2="165" stroke="#444" stroke-width="1.5" marker-end="url(#r2)"/>
  <rect x="55"  y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="80"  y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">Start</text>
  <rect x="205" y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">A</text>
  <rect x="205" y="140" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="169" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">B</text>
  <rect x="355" y="140" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="380" y="169" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">C</text>
  <rect x="505" y="140" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="530" y="169" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">D</text>
  <text x="80"  y="215" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="230" y="107" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="230" y="207" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=1)</text>
  <text x="380" y="207" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=1)</text>
  <text x="530" y="207" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=1)</text>
</svg>

#### Example 3 — Branch from row 0 goes below

**Topology:** `Start → A → B → C` (main) and `A → D` (branch)

`B` and `C` inherit row 0 from `A`. `D` also inherits row 0 from `A`, but row 0 is already taken in that column by `B`. Searching above hits row −1 (invalid); searching below finds row 1 free.

<svg xmlns="http://www.w3.org/2000/svg" width="580" height="225">
  <defs>
    <marker id="r3" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="15"  width="580" height="100" fill="#eef2ff" rx="3"/>
  <rect x="0" y="115" width="580" height="100" fill="#fff8ed" rx="3"/>
  <text x="4" y="69"  font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <text x="4" y="169" font-size="10" fill="#aaa" font-family="sans-serif">row 1</text>
  <line x1="106" y1="65"  x2="204" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#r3)"/>
  <line x1="256" y1="65"  x2="354" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#r3)"/>
  <line x1="406" y1="65"  x2="504" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#r3)"/>
  <line x1="256" y1="65"  x2="354" y2="165" stroke="#e67e22" stroke-width="1.5" stroke-dasharray="5,3" marker-end="url(#r3)"/>
  <rect x="55"  y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="80"  y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">Start</text>
  <rect x="205" y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">A</text>
  <rect x="355" y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="380" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">B</text>
  <rect x="505" y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="530" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">C</text>
  <rect x="355" y="140" width="50" height="50" fill="white" stroke="#e67e22" stroke-width="1.5" rx="4"/>
  <text x="380" y="169" text-anchor="middle" font-size="11" font-family="monospace" fill="#a04000">D</text>
  <text x="80"  y="215" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="230" y="107" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="380" y="107" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="530" y="107" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="380" y="207" text-anchor="middle" font-size="9" fill="#a04000" font-family="sans-serif">(r=1) ↓ below</text>
</svg>

When the main flow occupies row 0 and there is no room above, branches extend downward.

#### Example 4 — Branch from row 1 prefers above

**Topology:** `Start → A` (row 0), `Start → B → C` (row 1), `B → D` (branch)

`D` desires row 1 (same predecessor `B`), but row 1 is taken in that column by `C`. Searching above finds row 0 free — `D` lands **above** `C`.

<svg xmlns="http://www.w3.org/2000/svg" width="430" height="225">
  <defs>
    <marker id="r4" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="15"  width="430" height="100" fill="#eef2ff" rx="3"/>
  <rect x="0" y="115" width="430" height="100" fill="#fff8ed" rx="3"/>
  <text x="4" y="69"  font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <text x="4" y="169" font-size="10" fill="#aaa" font-family="sans-serif">row 1</text>
  <line x1="106" y1="65"  x2="204" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#r4)"/>
  <line x1="106" y1="65"  x2="204" y2="165" stroke="#444" stroke-width="1.5" marker-end="url(#r4)"/>
  <line x1="256" y1="165" x2="354" y2="165" stroke="#444" stroke-width="1.5" marker-end="url(#r4)"/>
  <line x1="256" y1="165" x2="354" y2="65"  stroke="#8e44ad" stroke-width="1.5" stroke-dasharray="5,3" marker-end="url(#r4)"/>
  <rect x="55"  y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="80"  y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">Start</text>
  <rect x="205" y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">A</text>
  <rect x="205" y="140" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="169" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">B</text>
  <rect x="355" y="140" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="380" y="169" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">C</text>
  <rect x="355" y="40"  width="50" height="50" fill="#f5eaff" stroke="#8e44ad" stroke-width="1.5" rx="4"/>
  <text x="380" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#6c2f9e">D</text>
  <text x="80"  y="215" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="230" y="107" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="230" y="207" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=1)</text>
  <text x="380" y="207" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=1)</text>
  <text x="380" y="107" text-anchor="middle" font-size="9" fill="#6c2f9e" font-family="sans-serif">(r=0) ↑ above</text>
</svg>

The "prefer above" rule keeps branches visually close to their origin and avoids unnecessary downward growth when space above is available.

#### Example 5 — Convergence inherits topmost predecessor

**Topology:** `Start → A → C → End` and `Start → B → C`

`C` has two predecessors: `A` (row 0) and `B` (row 1). The minimum is 0, so `C` desires row 0 and lands there. The main flow `A → C → End` is unbroken on row 0.

<svg xmlns="http://www.w3.org/2000/svg" width="580" height="225">
  <defs>
    <marker id="r5" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="15"  width="580" height="100" fill="#eef2ff" rx="3"/>
  <rect x="0" y="115" width="580" height="100" fill="#fff8ed" rx="3"/>
  <text x="4" y="69"  font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <text x="4" y="169" font-size="10" fill="#aaa" font-family="sans-serif">row 1</text>
  <line x1="106" y1="65"  x2="204" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#r5)"/>
  <line x1="106" y1="65"  x2="204" y2="165" stroke="#444" stroke-width="1.5" marker-end="url(#r5)"/>
  <line x1="256" y1="65"  x2="354" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#r5)"/>
  <line x1="256" y1="165" x2="354" y2="65"  stroke="#16a085" stroke-width="1.5" stroke-dasharray="5,3" marker-end="url(#r5)"/>
  <line x1="406" y1="65"  x2="504" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#r5)"/>
  <rect x="55"  y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="80"  y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">Start</text>
  <rect x="205" y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">A</text>
  <rect x="205" y="140" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="169" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">B</text>
  <rect x="355" y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="380" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">C</text>
  <rect x="505" y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="530" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">End</text>
  <text x="80"  y="215" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="230" y="107" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
  <text x="230" y="207" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=1)</text>
  <text x="380" y="107" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0) ← min(0,1)</text>
  <text x="530" y="107" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(r=0)</text>
</svg>

#### Example 6 — Three parallel branches from one node

**Topology:** `Start → A`, `Start → B`, `Start → C` (no further edges)

All three successors desire row 0 (inherited from `Start`). `A` (first in BFS) takes row 0. `B` desires 0, finds it taken; above is row −1 (invalid); below is row 1 free → row 1. `C` desires 0, finds 0 and 1 taken; above exhausted; row 2 free → row 2.

<svg xmlns="http://www.w3.org/2000/svg" width="285" height="325">
  <defs>
    <marker id="r6" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="15"  width="285" height="100" fill="#eef2ff" rx="3"/>
  <rect x="0" y="115" width="285" height="100" fill="#fff8ed" rx="3"/>
  <rect x="0" y="215" width="285" height="100" fill="#eef5ee" rx="3"/>
  <text x="4" y="69"  font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <text x="4" y="169" font-size="10" fill="#aaa" font-family="sans-serif">row 1</text>
  <text x="4" y="269" font-size="10" fill="#aaa" font-family="sans-serif">row 2</text>
  <line x1="106" y1="65"  x2="204" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#r6)"/>
  <line x1="106" y1="65"  x2="204" y2="165" stroke="#444" stroke-width="1.5" marker-end="url(#r6)"/>
  <line x1="106" y1="65"  x2="204" y2="265" stroke="#444" stroke-width="1.5" marker-end="url(#r6)"/>
  <rect x="55"  y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="80"  y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">Start</text>
  <rect x="205" y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">A</text>
  <rect x="205" y="140" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="169" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">B</text>
  <rect x="205" y="240" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="269" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">C</text>
  <text x="80"  y="315" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(c=0, r=0)</text>
  <text x="230" y="107" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(c=1, r=0)</text>
  <text x="230" y="207" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(c=1, r=1)</text>
  <text x="230" y="307" text-anchor="middle" font-size="9" fill="#666" font-family="sans-serif">(c=1, r=2)</text>
</svg>

Parallel branches from row 0 extend downward when above is unavailable. The "prefer above" rule only activates when the desired row is greater than 0.

---

### 2.3  Grid Coordinates

After row and column assignment, `x`/`y` coordinates are computed from per-column widths and per-row heights:

```
colWidth[c]  = max width of any activity in column c (ACTIVITY_SIZE for plain activities,
                group.width for GroupModel)
rowHeight[r] = max height of any activity in row r

colX[0] = 0
colX[i] = colX[i-1] + colWidth[i-1] + (HORIZONTAL_STEP − ACTIVITY_SIZE)   // 100 px gap

rowY[0] = 0
rowY[i] = rowY[i-1] + rowHeight[i-1] + (VERTICAL_STEP − ACTIVITY_SIZE)    //  50 px gap

activity.x = originX + colX[column]
activity.y = originY + rowY[row]
```

`(x, y)` is the **top-left corner** of the activity icon. The visual centre is at `(x + ACTIVITY_SIZE/2, y + ACTIVITY_SIZE/2)`.

For root-level activities, `originX = originY = 0`. For children of a group, the origin is offset by the group's position plus its internal margins (see below).

---

## Group Layout

### Sizing formula

Group dimensions are computed bottom-up. The inner grid is computed from the group's children first, then the group box is sized to wrap that grid:

```
group.width  = grid.totalWidth()  + 2 × GROUP_PADDING + 2 × GROUP_MARGIN_HORIZONTAL
group.height = grid.totalHeight() + GROUP_HEADER_HEIGHT + 2 × GROUP_PADDING + GROUP_MARGIN_BOTTOM
```

### Structure

<svg xmlns="http://www.w3.org/2000/svg" width="310" height="200">
  <!-- group outer border -->
  <rect x="20" y="20" width="240" height="155" fill="none" stroke="#888" stroke-width="1.5" rx="4" stroke-dasharray="5,3"/>
  <!-- header band -->
  <rect x="20" y="20" width="240" height="20" fill="#d8d8f0" stroke="#888" stroke-width="1" rx="4"/>
  <text x="140" y="34" text-anchor="middle" font-size="10" fill="#444" font-family="sans-serif">header (GROUP_HEADER_HEIGHT = 20)</text>
  <!-- horizontal margin zones (left and right) -->
  <rect x="20"  y="40" width="60" height="135" fill="#ffe4e4" opacity="0.6"/>
  <rect x="200" y="40" width="60" height="135" fill="#ffe4e4" opacity="0.6"/>
  <!-- padding zones (left and right inner) -->
  <rect x="80"  y="40" width="20" height="105" fill="#d4f4d4" opacity="0.7"/>
  <rect x="180" y="40" width="20" height="105" fill="#d4f4d4" opacity="0.7"/>
  <!-- top padding -->
  <rect x="100" y="40" width="80" height="20" fill="#d4f4d4" opacity="0.7"/>
  <!-- activity -->
  <rect x="100" y="60" width="80" height="65" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="140" y="97" text-anchor="middle" font-size="12" font-family="monospace" fill="#222">child</text>
  <!-- bottom padding -->
  <rect x="100" y="125" width="80" height="20" fill="#d4f4d4" opacity="0.7"/>
  <!-- bottom margin -->
  <rect x="20"  y="145" width="240" height="10" fill="#fff0cc" opacity="0.8"/>
  <!-- dimension annotations on the right -->
  <text x="268" y="30"  font-size="9" fill="#888" font-family="sans-serif">↕ 20</text>
  <text x="44"  y="116" text-anchor="middle" font-size="9" fill="#c00" font-family="sans-serif">←50→</text>
  <text x="90"  y="95"  text-anchor="middle" font-size="9" fill="#060" font-family="sans-serif">←20→</text>
  <text x="190" y="95"  text-anchor="middle" font-size="9" fill="#060" font-family="sans-serif">←20→</text>
  <text x="220" y="116" text-anchor="middle" font-size="9" fill="#c00" font-family="sans-serif">←50→</text>
  <text x="140" y="53"  text-anchor="middle" font-size="9" fill="#060" font-family="sans-serif">↕ 20</text>
  <text x="140" y="138" text-anchor="middle" font-size="9" fill="#060" font-family="sans-serif">↕ 20</text>
  <text x="140" y="153" text-anchor="middle" font-size="9" fill="#b80" font-family="sans-serif">↕ 10 (bottom margin)</text>
  <!-- legend -->
  <rect x="20"  y="172" width="10" height="8" fill="#ffe4e4" stroke="#ccc" stroke-width="0.5"/>
  <text x="34"  y="180" font-size="9" fill="#555" font-family="sans-serif">GROUP_MARGIN_HORIZONTAL</text>
  <rect x="160" y="172" width="10" height="8" fill="#d4f4d4" stroke="#ccc" stroke-width="0.5"/>
  <text x="174" y="180" font-size="9" fill="#555" font-family="sans-serif">GROUP_PADDING</text>
  <rect x="240" y="172" width="10" height="8" fill="#fff0cc" stroke="#ccc" stroke-width="0.5"/>
  <text x="254" y="180" font-size="9" fill="#555" font-family="sans-serif">BOTTOM_MARGIN</text>
</svg>

### Child origin

After a group is placed at absolute position `(group.x, group.y)`, its children are placed within it using:

```
childOriginX = group.x + GROUP_PADDING + GROUP_MARGIN_HORIZONTAL
childOriginY = group.y + GROUP_HEADER_HEIGHT + GROUP_PADDING
```

Children then receive their own column/row grid relative to this origin, exactly as root-level activities do.

### Nested groups

The algorithm recurses naturally: a `GroupModel` that itself contains child groups triggers another `sizeGroup` pass (depth-first) and another `placeContainer` call (top-down). Inner groups are sized first so their widths and heights are available when the outer group is computed. This means group nesting of any depth is supported with no special-casing.

<svg xmlns="http://www.w3.org/2000/svg" width="460" height="230">
  <defs>
    <marker id="ng" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <!-- outer group -->
  <rect x="20" y="20" width="360" height="175" fill="none" stroke="#4a7adb" stroke-width="1.5" rx="6" stroke-dasharray="6,3"/>
  <rect x="20" y="20" width="360" height="20" fill="#ccd8f8" stroke="#4a7adb" stroke-width="1" rx="6"/>
  <text x="200" y="34" text-anchor="middle" font-size="10" fill="#333" font-family="sans-serif">Outer group</text>
  <!-- inner group -->
  <rect x="95" y="60" width="210" height="115" fill="none" stroke="#e67e22" stroke-width="1.5" rx="5" stroke-dasharray="4,2"/>
  <rect x="95" y="60" width="210" height="20" fill="#fde3c0" stroke="#e67e22" stroke-width="1" rx="5"/>
  <text x="200" y="74" text-anchor="middle" font-size="10" fill="#333" font-family="sans-serif">Inner group</text>
  <!-- leaf activity inside inner group -->
  <rect x="165" y="105" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="190" y="134" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">X</text>
  <!-- activity before outer group -->
  <rect x="390" y="80" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="415" y="109" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">End</text>
  <line x1="382" y1="107" x2="388" y2="107" stroke="#444" stroke-width="1.5" marker-end="url(#ng)"/>
  <text x="200" y="210" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">Inner group sized first → used to size outer group → outer placed → inner placed → X placed</text>
</svg>

---

## Limitations

`CommonBfsModelLayouter` is designed for **acyclic, single-entry process graphs**. The following model shapes are not correctly handled:

### Feedback loops (back-edges)

Transitions that point backward in the flow — from a later activity to an earlier one, forming a loop — are not supported. The BFS traversal visits each activity at most once. A back-edge causes the target activity's column to be updated after it has already been placed, which shifts it to a higher column than intended. The visual result does not represent the loop structure.

**Workaround:** Use `NoopLayouter` and supply coordinates from an external source, or implement a custom `ProcessModelLayouter` that understands your loop structure.

### Self-loops

A transition from an activity to itself is a degenerate cycle. BFS processes the activity and then, when iterating its successors, attempts to update the activity's own column to `column + 1`. Because the activity is already in the visited set it is not re-enqueued, but its column assignment is still incremented — placing it one column further right than intended. Activities after a self-loop may also be shifted.

### Multiple independent entry points

The algorithm roots its BFS at the **first** declared start activity. Activities not reachable from that root are assigned to column 0 as a fallback. If a process has two truly independent execution paths (e.g., two parallel event listeners with no shared start), the second path's activities pile up at column 0 and may overlap with the first path.

**Workaround:** Ensure the model has a single designated start activity that connects (directly or transitively) to every other activity, or use a custom layouter.

### Groups with multiple independent entry points

The same restriction applies inside groups: only the first declared start activity of a group seeds the BFS for that group's interior. If a group has multiple independent entry activities, the secondary entries are treated as unreachable from the BFS root and fall back to column 0 within the group.
