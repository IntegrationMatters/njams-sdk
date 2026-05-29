# Row Assignment Specification — `CommonModelLayouter`

Specifies the row assignment pass proposed for `CommonModelLayouter.computeGrid`.  
The column assignment (BFS max-column rule) is unchanged and described briefly for context.

---

## 1  Overview

The layouter runs in two passes:

| Pass | Direction | What it does |
|------|-----------|--------------|
| Sizing | depth-first (leaves → root) | Computes `width`/`height` of each `GroupModel` from its children |
| Placement | top-down (root → leaves) | Assigns absolute `x`/`y` to every `ActivityModel` via a column/row grid |

The grid has two independent dimensions:

* **Column** — horizontal position, determined by BFS depth (unchanged).
* **Row** — vertical slot, the subject of this spec.

---

## 2  Column Assignment (unchanged)

BFS from the start activity. A node's column = its deepest predecessor's column + 1 (the "max-column rule"). This places every node to the right of all its predecessors and groups parallel branches into the same column.

---

## 3  Row Assignment (proposed)

### 3.1  Rules

**Rule 1 — Line continuity**  
A node with a single predecessor is placed on the same row as that predecessor. Sequential flows stay on one horizontal line.

**Rule 2 — Convergence inherits the topmost predecessor**  
A node with multiple predecessors (convergence node) uses the **minimum row** among its already-assigned predecessors as its desired row.

**Rule 3 — Conflict resolution: prefer above**  
If the desired row is already occupied in the target column, search outward from the desired row:  
1. Try `desired − 1`, `desired − 2`, … down to `0` (above the desired row, preferred).  
2. Then try `desired + 1`, `desired + 2`, … (below).

The first free slot wins. A row index of `0` is the topmost row — "above" means a lower index.

**Rule 4 — Isolated nodes**  
Nodes with no assigned predecessor (unreachable from the start, or secondary roots) use desired row `0` and follow Rule 3 for conflict resolution.

### 3.2  Pseudocode

```
predecessors ← invert(successors map from transitions)
occupied     ← Map<col, Set<row>>          // rows already taken per column
rowOf        ← Map<id, row>

for each node in BFS traversal order:
    col         ← columnOf[node]
    preds       ← predecessors[node] filtered to nodes already in rowOf
    desiredRow  ← min(rowOf[p] for p in preds) if preds non-empty, else 0
    assignedRow ← nearestFreeRow(desiredRow, occupied[col])
    rowOf[node] ← assignedRow
    occupied[col].add(assignedRow)

nearestFreeRow(desired, occupied):
    if desired ∉ occupied → return desired
    for delta = 1, 2, 3, …:
        above = desired − delta
        if above ≥ 0 and above ∉ occupied → return above    // prefer above
        below = desired + delta
        if below ∉ occupied             → return below
```

BFS traversal guarantees that all predecessors of a node are processed before the node itself (predecessors are always in earlier columns), so `rowOf[p]` is always available when needed.

---

## 4  Visual Examples

Colours: **blue band = row 0**, **amber band = row 1**, **green band = row 2**.  
Column/row indices are shown as `(c, r)` beneath each node.  
Highlighted arrows indicate the key transition being discussed.

---

### Example 1 — Linear flow (Rule 1)

**Topology:** `Start → A → B → End`

Every node has exactly one predecessor, so each inherits row 0.  
The entire sequence stays on a single horizontal line.

<svg xmlns="http://www.w3.org/2000/svg" width="580" height="120">
  <defs>
    <marker id="a1" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="15" width="580" height="100" fill="#eef2ff" rx="3"/>
  <text x="4" y="70" font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <line x1="106" y1="65" x2="204" y2="65" stroke="#444" stroke-width="1.5" marker-end="url(#a1)"/>
  <line x1="256" y1="65" x2="354" y2="65" stroke="#444" stroke-width="1.5" marker-end="url(#a1)"/>
  <line x1="406" y1="65" x2="504" y2="65" stroke="#444" stroke-width="1.5" marker-end="url(#a1)"/>
  <rect x="55" y="40" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="80" y="69" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">Start</text>
  <rect x="205" y="40" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="69" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">A</text>
  <rect x="355" y="40" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="380" y="69" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">B</text>
  <rect x="505" y="40" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="530" y="69" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">End</text>
  <text x="80"  y="110" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=0, r=0)</text>
  <text x="230" y="110" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=1, r=0)</text>
  <text x="380" y="110" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=2, r=0)</text>
  <text x="530" y="110" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=3, r=0)</text>
</svg>

---

### Example 2 — Chain must not fall back (Rules 1 + 3)

**Topology:** `Start → A` and `Start → B → C → D`

`Start` branches to `A` (first successor, row 0) and `B` (second successor, row 1).  
`C` and `D` each have a single predecessor (`B` and `C` respectively) and must inherit row 1 — they must **not** jump to row 0 just because their column is otherwise empty.

**Before (current behaviour):** `B→C` falls diagonally to row 0; the chain `B→C→D` is broken.

<svg xmlns="http://www.w3.org/2000/svg" width="580" height="225">
  <defs>
    <marker id="a2b" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="15"  width="580" height="100" fill="#eef2ff" rx="3"/>
  <rect x="0" y="115" width="580" height="100" fill="#fff8ed" rx="3"/>
  <text x="4" y="70"  font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <text x="4" y="170" font-size="10" fill="#aaa" font-family="sans-serif">row 1</text>
  <line x1="106" y1="65"  x2="204" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#a2b)"/>
  <line x1="106" y1="65"  x2="204" y2="165" stroke="#444" stroke-width="1.5" marker-end="url(#a2b)"/>
  <line x1="256" y1="165" x2="354" y2="65"  stroke="#e74c3c" stroke-width="2" stroke-dasharray="5,3" marker-end="url(#a2b)"/>
  <line x1="406" y1="65"  x2="504" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#a2b)"/>
  <rect x="55"  y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="80"  y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">Start</text>
  <rect x="205" y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">A</text>
  <rect x="205" y="140" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="169" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">B</text>
  <rect x="355" y="40"  width="50" height="50" fill="#ffeaea" stroke="#e74c3c" stroke-width="1.5" rx="4"/>
  <text x="380" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#c0392b">C</text>
  <rect x="505" y="40"  width="50" height="50" fill="#ffeaea" stroke="#e74c3c" stroke-width="1.5" rx="4"/>
  <text x="530" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#c0392b">D</text>
  <text x="80"  y="215" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=0, r=0)</text>
  <text x="230" y="107" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=1, r=0)</text>
  <text x="230" y="207" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=1, r=1)</text>
  <text x="380" y="107" text-anchor="middle" font-size="9" fill="#c0392b" font-family="sans-serif">(c=2, r=0) ✗</text>
  <text x="530" y="107" text-anchor="middle" font-size="9" fill="#c0392b" font-family="sans-serif">(c=3, r=0) ✗</text>
</svg>

**After (proposed behaviour):** `B→C` and `C→D` are horizontal; the chain stays on row 1.

<svg xmlns="http://www.w3.org/2000/svg" width="580" height="225">
  <defs>
    <marker id="a2a" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="15"  width="580" height="100" fill="#eef2ff" rx="3"/>
  <rect x="0" y="115" width="580" height="100" fill="#fff8ed" rx="3"/>
  <text x="4" y="70"  font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <text x="4" y="170" font-size="10" fill="#aaa" font-family="sans-serif">row 1</text>
  <line x1="106" y1="65"  x2="204" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#a2a)"/>
  <line x1="106" y1="65"  x2="204" y2="165" stroke="#444" stroke-width="1.5" marker-end="url(#a2a)"/>
  <line x1="256" y1="165" x2="354" y2="165" stroke="#27ae60" stroke-width="2" marker-end="url(#a2a)"/>
  <line x1="406" y1="165" x2="504" y2="165" stroke="#27ae60" stroke-width="2" marker-end="url(#a2a)"/>
  <rect x="55"  y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="80"  y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">Start</text>
  <rect x="205" y="40"  width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="69"  text-anchor="middle" font-size="11" font-family="monospace" fill="#222">A</text>
  <rect x="205" y="140" width="50" height="50" fill="white" stroke="#4a7adb" stroke-width="1.5" rx="4"/>
  <text x="230" y="169" text-anchor="middle" font-size="11" font-family="monospace" fill="#222">B</text>
  <rect x="355" y="140" width="50" height="50" fill="#eafaee" stroke="#27ae60" stroke-width="1.5" rx="4"/>
  <text x="380" y="169" text-anchor="middle" font-size="11" font-family="monospace" fill="#1a7a3a">C</text>
  <rect x="505" y="140" width="50" height="50" fill="#eafaee" stroke="#27ae60" stroke-width="1.5" rx="4"/>
  <text x="530" y="169" text-anchor="middle" font-size="11" font-family="monospace" fill="#1a7a3a">D</text>
  <text x="80"  y="215" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=0, r=0)</text>
  <text x="230" y="107" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=1, r=0)</text>
  <text x="230" y="207" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=1, r=1)</text>
  <text x="380" y="207" text-anchor="middle" font-size="9" fill="#1a7a3a" font-family="sans-serif">(c=2, r=1) ✓</text>
  <text x="530" y="207" text-anchor="middle" font-size="9" fill="#1a7a3a" font-family="sans-serif">(c=3, r=1) ✓</text>
</svg>

---

### Example 3 — Branch from row 0 goes below (Rules 1 + 3)

**Topology:** `Start → A → B → C` (main) and `A → D` (branch)

`B` and `C` inherit `A`'s row 0 (Rule 1).  
`D` desires row 0 (same predecessor `A`), but row 0 is already taken in col 2 by `B`.  
Searching above: row −1 is invalid. Searching below: row 1 is free → `D` lands at row 1.

<svg xmlns="http://www.w3.org/2000/svg" width="580" height="225">
  <defs>
    <marker id="a3" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="15"  width="580" height="100" fill="#eef2ff" rx="3"/>
  <rect x="0" y="115" width="580" height="100" fill="#fff8ed" rx="3"/>
  <text x="4" y="70"  font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <text x="4" y="170" font-size="10" fill="#aaa" font-family="sans-serif">row 1</text>
  <line x1="106" y1="65"  x2="204" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#a3)"/>
  <line x1="256" y1="65"  x2="354" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#a3)"/>
  <line x1="406" y1="65"  x2="504" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#a3)"/>
  <line x1="256" y1="65"  x2="354" y2="165" stroke="#e67e22" stroke-width="1.5" stroke-dasharray="5,3" marker-end="url(#a3)"/>
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
  <text x="80"  y="215" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=0, r=0)</text>
  <text x="230" y="107" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=1, r=0)</text>
  <text x="380" y="107" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=2, r=0)</text>
  <text x="530" y="107" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=3, r=0)</text>
  <text x="380" y="207" text-anchor="middle" font-size="9" fill="#a04000" font-family="sans-serif">(c=2, r=1)</text>
</svg>

Branch `D` is placed **below** the main flow because the main flow occupies row 0 and there is no room above row 0.

---

### Example 4 — Branch from row 1 prefers above (Rule 3)

**Topology:** `Start → A` (row 0), `Start → B → C` (row 1), `B → D` (branch from row 1)

`B` is on row 1. It branches to `C` (first successor, inherits row 1) and to `D`.  
`D` desires row 1 (same predecessor `B`), but row 1 is taken in col 2 by `C`.  
Searching above: row 0 is free → `D` lands at **row 0** (above `C`).

<svg xmlns="http://www.w3.org/2000/svg" width="430" height="225">
  <defs>
    <marker id="a4" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="15"  width="430" height="100" fill="#eef2ff" rx="3"/>
  <rect x="0" y="115" width="430" height="100" fill="#fff8ed" rx="3"/>
  <text x="4" y="70"  font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <text x="4" y="170" font-size="10" fill="#aaa" font-family="sans-serif">row 1</text>
  <line x1="106" y1="65"  x2="204" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#a4)"/>
  <line x1="106" y1="65"  x2="204" y2="165" stroke="#444" stroke-width="1.5" marker-end="url(#a4)"/>
  <line x1="256" y1="165" x2="354" y2="165" stroke="#444" stroke-width="1.5" marker-end="url(#a4)"/>
  <line x1="256" y1="165" x2="354" y2="65"  stroke="#8e44ad" stroke-width="2" stroke-dasharray="5,3" marker-end="url(#a4)"/>
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
  <text x="80"  y="215" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=0, r=0)</text>
  <text x="230" y="107" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=1, r=0)</text>
  <text x="230" y="207" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=1, r=1)</text>
  <text x="380" y="207" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=2, r=1)</text>
  <text x="380" y="107" text-anchor="middle" font-size="9" fill="#6c2f9e" font-family="sans-serif">(c=2, r=0) ↑ above</text>
</svg>

`D` is placed **above** `C` because `B` (its predecessor) is at row 1, leaving row 0 free above it. This is the "prefer above" preference of Rule 3 in action.

---

### Example 5 — Convergence inherits topmost predecessor (Rule 2)

**Topology:** `Start → A → C → End` and `Start → B → C`

`A` is on row 0, `B` on row 1. Both flow into `C`.  
`C`'s predecessors have rows `{0, 1}` → minimum = 0 → `C` desires row 0 → row 0 is free → `C` lands at row 0.  
The main flow `A → C → End` remains unbroken on row 0.

<svg xmlns="http://www.w3.org/2000/svg" width="580" height="225">
  <defs>
    <marker id="a5" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0,8 3,0 6" fill="#444"/>
    </marker>
  </defs>
  <rect x="0" y="15"  width="580" height="100" fill="#eef2ff" rx="3"/>
  <rect x="0" y="115" width="580" height="100" fill="#fff8ed" rx="3"/>
  <text x="4" y="70"  font-size="10" fill="#aaa" font-family="sans-serif">row 0</text>
  <text x="4" y="170" font-size="10" fill="#aaa" font-family="sans-serif">row 1</text>
  <line x1="106" y1="65"  x2="204" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#a5)"/>
  <line x1="106" y1="65"  x2="204" y2="165" stroke="#444" stroke-width="1.5" marker-end="url(#a5)"/>
  <line x1="256" y1="65"  x2="354" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#a5)"/>
  <line x1="256" y1="165" x2="354" y2="65"  stroke="#16a085" stroke-width="2" stroke-dasharray="5,3" marker-end="url(#a5)"/>
  <line x1="406" y1="65"  x2="504" y2="65"  stroke="#444" stroke-width="1.5" marker-end="url(#a5)"/>
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
  <text x="80"  y="215" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=0, r=0)</text>
  <text x="230" y="107" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=1, r=0)</text>
  <text x="230" y="207" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=1, r=1)</text>
  <text x="380" y="107" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=2, r=0)</text>
  <text x="530" y="107" text-anchor="middle" font-size="9" fill="#888" font-family="sans-serif">(c=3, r=0)</text>
  <text x="318" y="120" text-anchor="middle" font-size="9" fill="#16a085" font-family="sans-serif">min(r0,r1)=0</text>
</svg>

The dashed arrow from `B` (row 1) to `C` (row 0) shows the convergence. `C` takes row 0 because that is the minimum row among its predecessors.

---

## 5  Scope of change

Only `computeGrid` in `CommonModelLayouter` changes — specifically the block that fills `rowOf`.  
The `bfs()` method, the `Grid` data structure, `placeContainer`, and `sizeGroup` are all unchanged.  
A new private helper `nearestFreeRow(int desired, Set<Integer> occupied)` is introduced inside `computeGrid`.  
A `predecessors` map (inverse of the existing `successors` map) must be built at the top of `computeGrid`.
