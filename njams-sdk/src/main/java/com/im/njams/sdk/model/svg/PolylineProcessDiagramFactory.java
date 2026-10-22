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
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.model.svg;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.TransitionModel;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Process diagram factory that renders transitions as routed poly-lines instead of straight lines.
 *
 * <p>Activity placement is taken as-is from the configured layouter; only the transition edges are
 * drawn differently: each edge is routed orthogonally through the free gutters (between columns) and
 * channels (between rows) so that no transition arrow overlaps an activity node or runs collinearly
 * through one. This resolves the unreadable cases of the default straight-line rendering — most
 * notably a stepless bypass (a direct fork&#8594;convergence edge with no intermediate activity), as
 * well as choice fan-out and convergence.
 *
 * <p>This factory is an <b>opt-in</b> replacement for {@link NjamsProcessDiagramFactory}; the default
 * rendering is unchanged. Select it per client via
 * {@code njams.model().setDiagramFactory(new PolylineProcessDiagramFactory(njams))}.
 *
 * <p>Activity, group and SVG-document handling are inherited unchanged from
 * {@link NjamsProcessDiagramFactory}. Edges whose endpoints are not plain activities of the same
 * container (for example edges touching a group border) fall back to the inherited straight-line
 * rendering.
 */
public class PolylineProcessDiagramFactory extends NjamsProcessDiagramFactory {

    /** Lateral distance between two edges sharing the same gutter or channel. */
    private static final int LANE_GAP = 14;

    /** Fallback column-centre spacing, used only to size a synthetic gutter at the last column. */
    private static final int DEFAULT_COLUMN_SPACING = 150;

    /** Fallback row-centre spacing, used only to size a synthetic channel below the last row. */
    private static final int DEFAULT_ROW_SPACING = 100;

    /** Vertical clearance kept below an activity icon's label text when routing under a row. */
    private static final int LABEL_CLEARANCE = DEFAULT_TEXT_SIZE + 10;

    /** Height below the icon centre at which a bypass enters its target, keeping clear of the label. */
    private static final int TARGET_ENTRY_OFFSET = 12;

    /** Horizontal gap kept between a side-anchored label and the icon it is anchored to. */
    private static final int LABEL_GAP = 4;

    /**
     * Height of the strip below an activity icon that its label text occupies: the label's baseline sits
     * one text line below the icon, plus descender room.
     */
    static final int LABEL_BAND_HEIGHT = DEFAULT_TEXT_SIZE + DEFAULT_TEXT_SIZE / 2;

    private final boolean suppressIdLabels;

    /** Per-diagram routing result, keyed by transition id; set for the duration of one render. */
    private final ThreadLocal<Map<String, Route>> currentPlan = new ThreadLocal<>();

    /**
     * Creates the factory using the settings of the given Njams instance.
     *
     * @param njams the Njams instance whose settings configure secure processing and server
     *              compatibility
     */
    public PolylineProcessDiagramFactory(Njams njams) {
        super(njams);
        suppressIdLabels = "6.1".equals(njams.getSettings().getProperty(NjamsSettings.PROPERTY_SERVER_COMPATIBILITY));
    }

    PolylineProcessDiagramFactory(boolean disableSecureProcessing) {
        this(disableSecureProcessing, false);
    }

    PolylineProcessDiagramFactory(boolean disableSecureProcessing, boolean legacyServerCompat) {
        super(disableSecureProcessing, legacyServerCompat);
        suppressIdLabels = legacyServerCompat;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Computes the routing for all transitions of the model before delegating to the inherited
     * drawing, so that {@link #drawTransition(NjamsProcessDiagramContext, TransitionModel)} can look
     * up the routed path for each edge.
     */
    @Override
    public void createSvg(NjamsProcessDiagramContext context, ProcessModel processModel) {
        currentPlan.set(buildPlan(processModel));
        try {
            super.createSvg(context, processModel);
        } finally {
            currentPlan.remove();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Draws the transition as a routed poly-line. Edges that were not routed (no plan available,
     * or endpoints not plain activities of a single container) fall back to the inherited
     * straight-line rendering.
     */
    @Override
    protected void drawTransition(NjamsProcessDiagramContext context, TransitionModel transitionModel) {
        Map<String, Route> plan = currentPlan.get();
        Route route = plan == null ? null : plan.get(transitionModel.getId());
        if (route == null) {
            super.drawTransition(context, transitionModel);
            return;
        }
        drawRoutedTransition(context, transitionModel, route);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reports each routed transition's label extent, computed from the routing plan built by
     * {@link #createSvg}. A label can extend well beyond its activities' own bounds -- most notably a
     * purely vertical transition's fallback width, which is wider than the single column it sits in --
     * so without this the canvas could be sized too narrow and clip the label.
     */
    @Override
    protected List<double[]> getAdditionalBounds(ProcessModel processModel) {
        Map<String, Route> plan = currentPlan.get();
        if (plan == null) {
            return Collections.emptyList();
        }
        List<double[]> bounds = new ArrayList<>();
        for (Route route : plan.values()) {
            double left;
            double right;
            if ("start".equals(route.labelAnchor)) {
                left = route.labelX;
                right = route.labelX + route.labelWidth;
            } else if ("end".equals(route.labelAnchor)) {
                left = route.labelX - route.labelWidth;
                right = route.labelX;
            } else {
                left = route.labelX - route.labelWidth / 2.0;
                right = route.labelX + route.labelWidth / 2.0;
            }
            bounds.add(new double[] {left, route.labelY - DEFAULT_TEXT_SIZE * DEFAULT_MAX_LABEL_LINES,
                right, route.labelY + DEFAULT_TEXT_SIZE});
        }
        return bounds;
    }

    // --- routing -----------------------------------------------------------------------------------

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

    private void routeContainer(List<TransitionModel> transitions, List<ActivityModel> siblings,
        Map<String, Route> routes) {
        if (transitions.isEmpty()) {
            return;
        }
        Bands bands = Bands.of(siblings);

        Map<String, Integer> outDegree = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (TransitionModel t : transitions) {
            outDegree.merge(t.getFromActivity().getId(), 1, Integer::sum);
            inDegree.merge(t.getToActivity().getId(), 1, Integer::sum);
        }

        List<Edge> edges = new ArrayList<>();
        for (TransitionModel t : transitions) {
            Edge e = classify(t, bands, siblings);
            e.sourceFanOut = outDegree.getOrDefault(t.getFromActivity().getId(), 0) > 1;
            e.targetFanIn = inDegree.getOrDefault(t.getToActivity().getId(), 0) > 1;
            if (e.type == Type.ELBOW) {
                assignElbowGutter(e, siblings, bands);
            }
            edges.add(e);
        }
        assignLanes(edges);
        for (Edge e : edges) {
            routes.put(e.transition.getId(), buildRoute(e));
        }
    }

    private Edge classify(TransitionModel t, Bands bands, List<ActivityModel> siblings) {
        Edge e = new Edge();
        e.transition = t;
        ActivityModel from = t.getFromActivity();
        ActivityModel to = t.getToActivity();
        e.sourceBox = NodeBox.of(from);
        e.targetBox = NodeBox.of(to);
        e.scx = from.getX() + DEFAULT_HALF_ACTIVITY_SIZE;
        e.scy = from.getY() + DEFAULT_HALF_ACTIVITY_SIZE;
        e.tcx = to.getX() + DEFAULT_HALF_ACTIVITY_SIZE;
        e.tcy = to.getY() + DEFAULT_HALF_ACTIVITY_SIZE;
        int colS = bands.colIndexOf(e.sourceBox);
        int colT = bands.colIndexOf(e.targetBox);
        int rowS = bands.rowIndexOf(e.sourceBox);
        int rowT = bands.rowIndexOf(e.targetBox);
        e.rowS = rowS;
        e.colS = colS;
        e.colT = colT;

        boolean obstacleBetween = rowS == rowT && colT > colS
            && hasSiblingBetween(from.getX(), to.getX(), from.getY(), siblings);
        if (colS == colT || colT < colS || (rowS == rowT && !obstacleBetween && Math.abs(colT - colS) < 2)) {
            e.type = Type.STRAIGHT;
        } else if (rowS == rowT) {
            e.type = Type.BYPASS;
            // Channel below the row, kept clear both of the label text drawn beneath the icons and of
            // any sibling in this row that is taller than a plain activity (e.g. a group box).
            e.laneBase = channelY(rowS, bands);
            // Leave the source on its right and re-enter the target from the left, through the
            // gutters between columns, so neither end crosses the label below an icon. The neighbour
            // columns come from the sibling bands, which cover every activity in the container; a
            // transition-derived grid would be blind to an intermediate activity (e.g. a bypassed
            // group) whose own edges never reach this container's routing.
            e.exitX = exitCorridorX(e.sourceBox, bands);
            e.approachX = approachCorridorX(e.targetBox, bands);
        } else {
            e.type = Type.ELBOW;
            // The gutter the vertical run uses depends on the fan role, decided in assignElbowGutter
            // once the fan-out / fan-in flags are known.
        }
        return e;
    }

    private static int heightOf(ActivityModel activity) {
        return activity instanceof GroupModel ? ((GroupModel) activity).getHeight() : DEFAULT_ACTIVITY_SIZE;
    }

    private static int widthOf(ActivityModel activity) {
        return activity instanceof GroupModel ? ((GroupModel) activity).getWidth() : DEFAULT_ACTIVITY_SIZE;
    }

    /**
     * The channel y a same-row detour (a BYPASS's mid-row run, or a fan-in ELBOW's obstacle detour, see
     * {@link #assignElbowGutter}) may use below the given row band: the midpoint of the gap to the next
     * band, or clear of the label text under this band's icons, whichever is lower.
     */
    private static double channelY(int rowIndex, Bands bands) {
        double bottom = bands.rowBottom(rowIndex);
        double labelClear = bands.rowTop(rowIndex) + DEFAULT_ACTIVITY_SIZE + LABEL_CLEARANCE;
        double mid = rowIndex + 1 < bands.rowCount()
            ? (bottom + bands.rowTop(rowIndex + 1)) / 2.0
            : bottom + (DEFAULT_ROW_SPACING - DEFAULT_ACTIVITY_SIZE) / 2.0;
        return Math.max(mid, labelClear);
    }

    /** The corridor x just right of the given node's column band, before the next one. */
    private static double exitCorridorX(NodeBox node, Bands bands) {
        int col = bands.colIndexOf(node);
        double right = bands.colRight(col);
        return col + 1 < bands.colCount()
            ? (right + bands.colLeft(col + 1)) / 2.0
            : right + (DEFAULT_COLUMN_SPACING - DEFAULT_ACTIVITY_SIZE) / 2.0;
    }

    /** The corridor x just left of the given node's column band, after the previous one. */
    private static double approachCorridorX(NodeBox node, Bands bands) {
        int col = bands.colIndexOf(node);
        double left = bands.colLeft(col);
        return col > 0
            ? (bands.colRight(col - 1) + left) / 2.0
            : left - (DEFAULT_COLUMN_SPACING - DEFAULT_ACTIVITY_SIZE) / 2.0;
    }

    /**
     * Whether some sibling sits strictly between {@code fromX} and {@code toX} on {@code rowY}. Used to
     * detect an obstacle (e.g. a bypassed group) that has no transition of its own surviving into this
     * container's transition-derived coordinate grid, and so would otherwise be invisible to the
     * STRAIGHT/BYPASS classification above; and, in {@link #assignElbowGutter}, to detect a sibling
     * that a fan-in elbow's source-row exit run would otherwise cross.
     */
    private static boolean hasSiblingBetween(int fromX, int toX, int rowY, List<ActivityModel> siblings) {
        for (ActivityModel sibling : siblings) {
            if (sibling.getY() == rowY && sibling.getX() > fromX && sibling.getX() < toX) {
                return true;
            }
        }
        return false;
    }

    /**
     * Places an elbow's vertical run in the right gutter once its fan role is known. A plain elbow or
     * a fan-out exits on the source's right and runs in the gutter just right of the source column, so
     * each branch turns early. A pure fan-in instead runs along the (free) source row and bends in the
     * gutter just left of the target column, so a long join only turns towards the target at the end
     * and does not cut back across intermediate nodes on the target's row. The gutter geometry comes
     * from the sibling column bands (see {@link Bands}), so a group that is the sole occupant of its
     * column is still cleared correctly. When a fan-in's long source-row run would itself pass over another
     * sibling sitting in that row between the source and the gutter — a distant branch that happens to
     * land on the same row, see {@link #hasSiblingBetween} — the run is routed through the row's
     * below-row channel instead (like a {@link Type#BYPASS}) so it clears that sibling and its edges.
     */
    private void assignElbowGutter(Edge e, List<ActivityModel> siblings, Bands bands) {
        if (e.targetFanIn && !e.sourceFanOut) {
            e.laneBase = approachCorridorX(e.targetBox, bands);
            e.gutterCol = e.colT - 1;
            int fromX = (int) (e.scx - DEFAULT_HALF_ACTIVITY_SIZE);
            int fromY = (int) (e.scy - DEFAULT_HALF_ACTIVITY_SIZE);
            e.rowDetour = hasSiblingBetween(fromX, (int) e.laneBase, fromY, siblings);
            if (e.rowDetour) {
                e.exitX = exitCorridorX(e.sourceBox, bands);
                e.channelBase = channelY(e.rowS, bands);
            }
        } else {
            e.laneBase = exitCorridorX(e.sourceBox, bands);
            e.gutterCol = e.colS;
        }
    }

    /** Gives each edge sharing a gutter (elbow) or channel (bypass) a distinct lateral offset. */
    private void assignLanes(List<Edge> edges) {
        Map<Integer, List<Edge>> bypassByRow = new LinkedHashMap<>();
        Map<Integer, List<Edge>> elbowByGutter = new LinkedHashMap<>();
        Map<Integer, List<Edge>> rowDetourByRow = new LinkedHashMap<>();
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
                if (e.rowDetour) {
                    rowDetourByRow.computeIfAbsent(e.rowS, k -> new ArrayList<>()).add(e);
                }
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
        // A row-detouring fan-in elbow's channel run shares its row with any bypass already routed
        // through it (both use the same channelY/laneBase base for that row); it takes the next lane
        // after that row's bypasses so the two mechanisms never land on the same y.
        for (Map.Entry<Integer, List<Edge>> rowGroup : rowDetourByRow.entrySet()) {
            List<Edge> group = rowGroup.getValue();
            group.sort(bypassOrder);
            List<Edge> bypassesInRow = bypassByRow.get(rowGroup.getKey());
            int channelOffset = bypassesInRow == null ? 0 : bypassesInRow.size();
            for (int i = 0; i < group.size(); i++) {
                group.get(i).channelLane = i + channelOffset;
            }
        }
    }

    /**
     * Lane-ordering key for an elbow edge: a fan-in edge (shared target, not also a fork) nests its
     * longer runs innermost (ascending span), every other edge nests its longer runs outermost
     * (descending span). Sorting ascending by this key then assigns lane 0 to the run that must hug
     * the gutter base.
     */
    private static double elbowLaneKey(Edge e) {
        double span = Math.abs(e.tcy - e.scy);
        return e.targetFanIn && !e.sourceFanOut ? span : -span;
    }

    private Route buildRoute(Edge e) {
        List<Point> wp = new ArrayList<>();
        double labelX;
        double labelY;
        double labelWidth;
        String labelAnchor = "middle";
        switch (e.type) {
            case BYPASS: {
                double cy = e.laneBase + e.lane * LANE_GAP;
                double entryY = e.tcy + TARGET_ENTRY_OFFSET;
                wp.add(new Point(e.scx, e.scy));
                wp.add(new Point(e.exitX, e.scy));
                wp.add(new Point(e.exitX, cy));
                wp.add(new Point(e.approachX, cy));
                wp.add(new Point(e.approachX, entryY));
                wp.add(new Point(e.tcx, e.tcy));
                labelX = (e.exitX + e.approachX) / 2.0;
                labelY = cy + DEFAULT_TEXT_SIZE;
                labelWidth = Math.abs(e.approachX - e.exitX);
                break;
            }
            case ELBOW: {
                double gx = e.laneBase + e.lane * LANE_GAP;
                // Stagger exit/entry y at the node so sibling branches do not share a collinear
                // horizontal segment — which would hide a server-coloured (green) executed transition
                // behind unexecuted siblings drawn on top of it. Pure fan-in elbows stagger at the
                // target; all other elbows (fan-out or plain) stagger at the source.
                double exitY = (e.targetFanIn && !e.sourceFanOut) ? e.scy : e.scy + e.nodeStagger;
                double entryY = (e.targetFanIn && !e.sourceFanOut) ? e.tcy + e.nodeStagger : e.tcy;
                if (e.rowDetour) {
                    // The exit run along the source row would otherwise cross a sibling sitting between
                    // the source and the gutter (see assignElbowGutter) — detour through the row's
                    // below-row channel first, the same way a BYPASS clears a same-row obstacle.
                    double cy = e.channelBase + e.channelLane * LANE_GAP;
                    wp.add(new Point(e.scx, exitY));
                    wp.add(new Point(e.exitX, exitY));
                    wp.add(new Point(e.exitX, cy));
                    wp.add(new Point(gx, cy));
                    wp.add(new Point(gx, entryY));
                    wp.add(new Point(e.tcx, entryY));
                } else {
                    wp.add(new Point(e.scx, exitY));
                    wp.add(new Point(gx, exitY));
                    wp.add(new Point(gx, entryY));
                    wp.add(new Point(e.tcx, entryY));
                }
                // Anchor the label on the side that is NOT shared with sibling edges, hugging the node
                // and growing into the free run space: fan-out labels are right-aligned just left of the
                // target (so they use the long approach), fan-in labels are left-aligned just right of
                // the source. With no sharing, centre it on the longer horizontal run.
                if (e.sourceFanOut && !e.targetFanIn) {
                    labelAnchor = "end";
                    labelX = e.tcx - DEFAULT_HALF_ACTIVITY_SIZE - LABEL_GAP;
                    labelY = e.tcy + DEFAULT_TEXT_SIZE;
                    labelWidth = Math.abs(labelX - e.scx);
                } else if (e.targetFanIn && !e.sourceFanOut) {
                    labelAnchor = "start";
                    labelX = e.scx + DEFAULT_HALF_ACTIVITY_SIZE + LABEL_GAP;
                    labelY = e.scy + DEFAULT_TEXT_SIZE;
                    labelWidth = Math.abs(e.tcx - labelX);
                } else if (Math.abs(e.tcx - gx) > Math.abs(gx - e.scx)) {
                    labelX = (gx + e.tcx) / 2.0;
                    labelY = e.tcy + DEFAULT_TEXT_SIZE;
                    labelWidth = Math.abs(e.tcx - gx);
                } else {
                    labelX = (e.scx + gx) / 2.0;
                    labelY = e.scy + DEFAULT_TEXT_SIZE;
                    labelWidth = Math.abs(gx - e.scx);
                }
                break;
            }
            default: {
                wp.add(new Point(e.scx, e.scy));
                wp.add(new Point(e.tcx, e.tcy));
                labelX = (e.scx + e.tcx) / 2.0;
                // Actual horizontal span between the two endpoints. A purely vertical transition has
                // none at all, so it falls back to the same column-to-column spacing assumed for a
                // pure horizontal transition, not the far narrower activity icon size.
                double horizontalSpan = Math.abs(e.tcx - e.scx);
                double centerSpan = horizontalSpan > 0 ? horizontalSpan : DEFAULT_COLUMN_SPACING;
                // The real clear gap is between the two icons' facing edges, not their (wider) centre
                // distance -- otherwise a wrapped label happily grows into the icons on either side.
                labelWidth = centerSpan - DEFAULT_ACTIVITY_SIZE;
                if (horizontalSpan > 0) {
                    // Anchor the LAST line just above the line (hugging it, like the icon/label gap
                    // elsewhere -- see LABEL_GAP) and grow earlier lines further upward, instead of
                    // growing every line downward from just below it. The space below a same-row edge
                    // is exactly where a fan-out/fan-in sibling's staggered elbow run lives (see
                    // assignElbowGutter/assignLanes), so growing down risks the label overlapping that
                    // sibling's line.
                    int lineCount = Math.max(1, wrapLabel(e.transition.getName(), labelWidth).getLines().length);
                    labelY = (e.scy + e.tcy) / 2.0 - LABEL_GAP - (lineCount - 1) * DEFAULT_TEXT_SIZE;
                } else {
                    // Purely vertical: the label lives in the same narrow gap the two activities are
                    // spaced by, so a wrapped block must keep its last line where a single line would
                    // sit and grow the earlier lines upward, instead of growing every line downward
                    // into the activity below.
                    int lineCount = Math.max(1, wrapLabel(e.transition.getName(), labelWidth).getLines().length);
                    labelY = (e.scy + e.tcy) / 2.0 + DEFAULT_TEXT_SIZE - (lineCount - 1) * DEFAULT_TEXT_SIZE;
                }
                break;
            }
        }
        return new Route(wp, labelX, labelY, labelWidth, labelAnchor);
    }

    // --- drawing -----------------------------------------------------------------------------------

    private void drawRoutedTransition(NjamsProcessDiagramContext context, TransitionModel t, Route route) {
        String markerId = createArrowMarker(context, t);

        List<Point> wp = route.waypoints;
        Point start = radiusPoint(wp.get(1), wp.get(0), DEFAULT_ACTIVITY_RADIUS);
        Point end = radiusPoint(wp.get(wp.size() - 2), wp.get(wp.size() - 1),
            DEFAULT_ACTIVITY_RADIUS + (double) DEFAULT_MARKER_SIZE);

        StringBuilder points = new StringBuilder();
        appendPoint(points, context, start);
        for (int i = 1; i < wp.size() - 1; i++) {
            appendPoint(points, context, wp.get(i));
        }
        appendPoint(points, context, end);

        Element polyline = context.getDoc().createElementNS(context.getSvgNS(), "polyline");
        polyline.setAttributeNS(null, "markerId", markerId);
        polyline.setAttributeNS(null, "modelId", t.getId());
        polyline.setAttributeNS(null, "name", t.getName() != null ? t.getName() : "");
        polyline.setAttributeNS(null, "points", points.toString().trim());
        polyline.setAttributeNS(null, "marker-end", "url(#" + markerId + ")");
        polyline.setAttributeNS(null, "fill", "none");
        polyline.setAttributeNS(null, "style", "cursor: pointer; stroke:#000; fill:none");
        context.getContainerElement().appendChild(polyline);

        drawLabel(context, t, route, polyline);
    }

    private String createArrowMarker(NjamsProcessDiagramContext context, TransitionModel t) {
        String markerId = t.getId() + "_marker";
        Element marker = context.getDoc().createElementNS(context.getSvgNS(), "marker");
        marker.setAttributeNS(null, "id", markerId);
        marker.setAttributeNS(null, "name", markerId);
        marker.setAttributeNS(null, "viewbox", "0 0 10 10");
        marker.setAttributeNS(null, "refX", "1");
        marker.setAttributeNS(null, "refY", "5");
        marker.setAttributeNS(null, "markerUnits", "userSpaceOnUse");
        marker.setAttributeNS(null, "orient", "auto");
        marker.setAttributeNS(null, "markerWidth", "0.7em");
        marker.setAttributeNS(null, "markerHeight", "0.7em");
        marker.setAttributeNS(null, "fill", "#000");
        marker.setAttributeNS(null, "stroke", "#000");
        context.getContainerElement().appendChild(marker);
        Element head = context.getDoc().createElementNS(context.getSvgNS(), "polyline");
        head.setAttributeNS(null, "points", "0,0 10,5 0,10 1,5");
        marker.appendChild(head);
        return markerId;
    }

    private void drawLabel(NjamsProcessDiagramContext context, TransitionModel t, Route route, Element edge) {
        FittedLabel label = wrapLabel(t.getName(), route.labelWidth);
        String[] labelLines = label.getLines();
        boolean suppress = suppressIdLabels && Objects.equals(t.getName(), t.getId());
        if (labelLines.length == 0 || suppress) {
            return;
        }
        if (label.isTruncated()) {
            edge.setAttributeNS(null, TOOLTIP_ATTRIBUTE, label.getFull());
        }
        double midX = context.getStartX() + route.labelX;
        double midY = context.getStartY() + route.labelY;
        for (int i = 0; i < labelLines.length; i++) {
            Element textElem = context.getDoc().createElementNS(context.getSvgNS(), "text");
            String elemId = i == 0 ? t.getId() + "_label" : t.getId() + "_label_" + (i + 1);
            textElem.setAttributeNS(null, "id", elemId);
            textElem.setAttributeNS(null, "x", String.valueOf(midX));
            textElem.setAttributeNS(null, "y", String.valueOf(midY + i * DEFAULT_TEXT_SIZE));
            textElem.setAttributeNS(null, "text-anchor", route.labelAnchor);
            textElem.setTextContent(labelLines[i]);
            context.getContainerElement().appendChild(textElem);
        }
    }

    private static void appendPoint(StringBuilder sb, NjamsProcessDiagramContext context, Point p) {
        sb.append(context.getStartX() + p.getX()).append(',').append(context.getStartY() + p.getY()).append(' ');
    }

    /** Point at distance {@code radius} from {@code center} towards {@code towards}. */
    private static Point radiusPoint(Point towards, Point center, double radius) {
        double vx = towards.getX() - center.getX();
        double vy = towards.getY() - center.getY();
        double mag = Math.sqrt(vx * vx + vy * vy);
        if (mag == 0) {
            return new Point(center.getX(), center.getY());
        }
        return new Point(center.getX() + vx / mag * radius, center.getY() + vy / mag * radius);
    }

    /**
     * The rectangle an activity occupies, plus the area a route must keep clear of it. A plain activity is
     * a fixed-size icon with a label band below it; a group is its own, layouter-assigned box.
     */
    private static final class NodeBox {
        private final double left;
        private final double top;
        private final double right;
        private final double bottom;
        private final double centerX;
        private final double centerY;
        private final boolean group;

        private NodeBox(double left, double top, double width, double height, boolean group) {
            this.left = left;
            this.top = top;
            right = left + width;
            bottom = top + height;
            centerX = left + width / 2.0;
            centerY = top + height / 2.0;
            this.group = group;
        }

        static NodeBox of(ActivityModel activity) {
            return new NodeBox(activity.getX(), activity.getY(), widthOf(activity), heightOf(activity),
                activity instanceof GroupModel);
        }

        /**
         * The extent a route must not cross: the box itself, extended downwards over the label band for a
         * plain activity. A group draws its own label inside its box, so its extent is the box.
         */
        double[] obstacleExtent() {
            return new double[] {left, top, right, group ? bottom : bottom + LABEL_BAND_HEIGHT};
        }

        boolean overlapsVertically(NodeBox other) {
            return top < other.bottom && other.top < bottom;
        }

        boolean overlapsHorizontally(NodeBox other) {
            return left < other.right && other.left < right;
        }
    }

    /**
     * Rows and columns as overlap bands. Every node whose box overlaps another's on the perpendicular
     * axis shares its band, so a group and the plain activities beside it form one row even though their
     * boxes start at different coordinates. Bands cover every sibling in the container, including ones
     * whose own edges never reach this routing.
     */
    private static final class Bands {
        private final List<double[]> rows;
        private final List<double[]> columns;

        private Bands(List<double[]> rows, List<double[]> columns) {
            this.rows = rows;
            this.columns = columns;
        }

        static Bands of(List<ActivityModel> siblings) {
            List<double[]> vertical = new ArrayList<>();
            List<double[]> horizontal = new ArrayList<>();
            for (ActivityModel sibling : siblings) {
                NodeBox box = NodeBox.of(sibling);
                vertical.add(new double[] {box.top, box.bottom});
                horizontal.add(new double[] {box.left, box.right});
            }
            return new Bands(merge(vertical), merge(horizontal));
        }

        /** Merges overlapping spans in ascending order; spans that only touch stay separate. */
        private static List<double[]> merge(List<double[]> spans) {
            spans.sort(Comparator.comparingDouble(span -> span[0]));
            List<double[]> merged = new ArrayList<>();
            for (double[] span : spans) {
                double[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
                if (last != null && span[0] < last[1]) {
                    last[1] = Math.max(last[1], span[1]);
                } else {
                    merged.add(new double[] {span[0], span[1]});
                }
            }
            return merged;
        }

        int rowIndexOf(NodeBox node) {
            return indexOf(rows, node.top, node.bottom);
        }

        int colIndexOf(NodeBox node) {
            return indexOf(columns, node.left, node.right);
        }

        private static int indexOf(List<double[]> bands, double start, double end) {
            for (int i = 0; i < bands.size(); i++) {
                if (start < bands.get(i)[1] && bands.get(i)[0] < end) {
                    return i;
                }
            }
            return -1;
        }

        int rowCount() {
            return rows.size();
        }

        double rowTop(int index) {
            return rows.get(index)[0];
        }

        double rowBottom(int index) {
            return rows.get(index)[1];
        }

        int colCount() {
            return columns.size();
        }

        double colLeft(int index) {
            return columns.get(index)[0];
        }

        double colRight(int index) {
            return columns.get(index)[1];
        }
    }

    private enum Type {
        STRAIGHT, BYPASS, ELBOW
    }

    /** Working state for one edge while routing a container. */
    private static final class Edge {
        private TransitionModel transition;
        private Type type;
        private NodeBox sourceBox;
        private NodeBox targetBox;
        private double scx;
        private double scy;
        private double tcx;
        private double tcy;
        private int colS;
        private int rowS;
        private int colT;
        private int gutterCol;
        private double laneBase;
        private double exitX;
        private double approachX;
        private int lane;
        private double nodeStagger;
        private boolean sourceFanOut;
        private boolean targetFanIn;
        private boolean rowDetour;
        private double channelBase;
        private int channelLane;
    }

    /** Routed path (model coordinates) plus the chosen label anchor. */
    private static final class Route {
        private final List<Point> waypoints;
        private final double labelX;
        private final double labelY;
        private final double labelWidth;
        private final String labelAnchor;

        Route(List<Point> waypoints, double labelX, double labelY, double labelWidth, String labelAnchor) {
            this.waypoints = waypoints;
            this.labelX = labelX;
            this.labelY = labelY;
            this.labelWidth = labelWidth;
            this.labelAnchor = labelAnchor;
        }
    }
}
