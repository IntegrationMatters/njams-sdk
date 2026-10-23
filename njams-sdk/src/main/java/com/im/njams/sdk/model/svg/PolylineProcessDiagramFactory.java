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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * {@link NjamsProcessDiagramFactory}. Edges between two different containers fall back to the inherited
 * straight-line rendering; edges touching a group border are routed like any other.
 */
public class PolylineProcessDiagramFactory extends NjamsProcessDiagramFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PolylineProcessDiagramFactory.class);

    /** How far an edge may be pushed out of the way before its last candidate is drawn regardless. */
    private static final int MAX_ESCALATION_LEVEL = 2;

    /** Bound on the re-check loop, so a set of edges that keep displacing each other still terminates. */
    private static final int MAX_ESCALATION_ROUNDS = 4;

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

    /** Height of a group's header bar, as drawn by the inherited group rendering. */
    private static final int GROUP_HEADER_HEIGHT = 20;

    /** Clearance kept around every obstacle box, so a route that would graze one still counts as blocked. */
    private static final int CLEARANCE_MARGIN = 1;

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
            bounds.add(extentOf(route));
        }
        return bounds;
    }

    // --- routing -----------------------------------------------------------------------------------

    /**
     * The area one routed path occupies. A corridor above or below the activities, or an arrow meeting a
     * node from underneath its label, can reach past the activities themselves, and the canvas has to
     * grow with it rather than clip it.
     */
    private static double[] extentOf(Route route) {
        double left = Math.min(route.start.getX(), route.end.getX());
        double top = Math.min(route.start.getY(), route.end.getY());
        double right = Math.max(route.start.getX(), route.end.getX());
        double bottom = Math.max(route.start.getY(), route.end.getY());
        for (Point point : route.waypoints) {
            left = Math.min(left, point.getX());
            top = Math.min(top, point.getY());
            right = Math.max(right, point.getX());
            bottom = Math.max(bottom, point.getY());
        }
        return new double[] {left, top, right, bottom};
    }

    private Map<String, Route> buildPlan(ProcessModel processModel) {
        Map<String, Route> routes = new HashMap<>();
        List<TransitionModel> rootTransitions = new ArrayList<>();
        Map<GroupModel, List<TransitionModel>> groupTransitions = new LinkedHashMap<>();
        for (TransitionModel t : processModel.getTransitionModels()) {
            ActivityModel from = t.getFromActivity();
            ActivityModel to = t.getToActivity();
            if (from == null || to == null) {
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
        routeContainer(rootTransitions, rootSiblings(processModel), null, routes);
        for (Map.Entry<GroupModel, List<TransitionModel>> entry : groupTransitions.entrySet()) {
            routeContainer(entry.getValue(), entry.getKey().getChildActivities(), entry.getKey(), routes);
        }
        return routes;
    }

    /**
     * The highest y a route inside this container may use: just below a group's header, or unbounded at
     * the top level. The header height mirrors the one {@code NjamsProcessDiagramFactory.drawGroup} draws.
     */
    private static double contentTopOf(GroupModel container) {
        return container == null ? Double.NEGATIVE_INFINITY
            : container.getY() + GROUP_HEADER_HEIGHT + CLEARANCE_MARGIN;
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
        GroupModel container, Map<String, Route> routes) {
        if (transitions.isEmpty()) {
            return;
        }
        Bands bands = Bands.of(siblings, contentTopOf(container));
        Obstacles obstacles = Obstacles.of(siblings);

        Map<String, Integer> outDegree = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (TransitionModel t : transitions) {
            outDegree.merge(t.getFromActivity().getId(), 1, Integer::sum);
            inDegree.merge(t.getToActivity().getId(), 1, Integer::sum);
        }

        List<Edge> edges = new ArrayList<>();
        for (TransitionModel t : transitions) {
            Edge e = classify(t, bands, obstacles);
            e.sourceFanOut = outDegree.getOrDefault(t.getFromActivity().getId(), 0) > 1;
            e.targetFanIn = inDegree.getOrDefault(t.getToActivity().getId(), 0) > 1;
            edges.add(e);
        }
        alignFanShapes(edges);
        assignGutters(edges, obstacles, bands);
        assignLanes(edges);

        Map<Edge, Route> built = buildRoutes(edges);
        for (int round = 0; round < MAX_ESCALATION_ROUNDS; round++) {
            List<Edge> blocked = blockedEdges(edges, built, obstacles);
            if (blocked.isEmpty() || !escalateBlocked(blocked, edges, obstacles, bands)) {
                break;
            }
            assignLanes(edges);
            built = buildRoutes(edges);
        }
        for (Edge e : blockedEdges(edges, built, obstacles)) {
            LOG.debug("No clear route found for transition {}; drawing it over an activity box.",
                e.transition.getId());
        }
        for (Map.Entry<Edge, Route> entry : built.entrySet()) {
            routes.put(entry.getKey().transition.getId(), entry.getValue());
        }
    }

    private Map<Edge, Route> buildRoutes(List<Edge> edges) {
        Map<Edge, Route> built = new LinkedHashMap<>();
        for (Edge e : edges) {
            built.put(e, buildRoute(e));
        }
        return built;
    }

    /** The edges whose routed path, lanes and stagger included, still runs over a node it must clear. */
    private static List<Edge> blockedEdges(List<Edge> edges, Map<Edge, Route> built, Obstacles obstacles) {
        List<Edge> blocked = new ArrayList<>();
        for (Edge e : edges) {
            if (obstacles.blocks(built.get(e).waypoints, e.transition.getFromActivity(),
                e.transition.getToActivity())) {
                blocked.add(e);
            }
        }
        return blocked;
    }

    /**
     * Pushes every blocked edge one candidate further out, together with the rest of its fan so the fan
     * keeps one shape. Returns whether anything moved — when nothing can be raised any more, the caller
     * stops and draws what it has.
     */
    /**
     * Moves every blocked edge to its next candidate, and only those: an edge whose route is already
     * clear keeps it. The spec asks a fan to share a route <em>shape</em>, which {@link #alignFanShapes}
     * settles; raising a clear branch to a blocked sibling's level instead degrades a route that had
     * nothing wrong with it. Returns false once nothing moved, which ends the escalation loop.
     */
    private boolean escalateBlocked(List<Edge> blocked, List<Edge> edges, Obstacles obstacles,
        Bands bands) {
        boolean moved = false;
        for (Edge e : blocked) {
            moved |= escalate(e, bands);
        }
        if (moved) {
            alignFanShapes(edges);
            assignGutters(edges, obstacles, bands);
        }
        return moved;
    }

    /**
     * Moves one edge to its next candidate: a straight edge takes the routed shape, a routed one moves
     * its corridor into the next band gap. Returns false once the edge has run out of candidates.
     */
    private boolean escalate(Edge e, Bands bands) {
        if (e.level >= MAX_ESCALATION_LEVEL) {
            return false;
        }
        e.level++;
        if (e.type == Type.STRAIGHT) {
            shapeAsRouted(e, bands);
        } else if (e.type == Type.BACKWARD) {
            e.laneBase = aboveChannelBand(Math.min(e.rowS, e.rowT) - e.level, bands);
        } else if (e.type == Type.BYPASS || e.verticalApproach) {
            e.laneBase = channelBelowBand(e.rowS + e.level, bands);
        } else if (!e.rowDetour) {
            e.rowDetour = true;
            e.exitX = exitCorridorX(e.sourceBox, bands);
            e.channelBase = channelY(e.rowS, bands);
        } else {
            e.channelBase = channelBelowBand(e.rowS + e.level, bands);
        }
        return true;
    }

    /** Assigns a gutter to every elbow that still needs one, leaving the ones already placed alone. */
    private void assignGutters(List<Edge> edges, Obstacles obstacles, Bands bands) {
        for (Edge e : edges) {
            if (e.type == Type.ELBOW && !e.verticalApproach && !e.gutterAssigned) {
                assignElbowGutter(e, obstacles, bands);
                e.gutterAssigned = true;
            }
        }
    }

    private Edge classify(TransitionModel t, Bands bands, Obstacles obstacles) {

        Edge e = new Edge();
        e.transition = t;
        ActivityModel from = t.getFromActivity();
        ActivityModel to = t.getToActivity();
        e.sourceBox = NodeBox.of(from);
        e.targetBox = NodeBox.of(to);
        e.scx = e.sourceBox.centerX;
        e.scy = e.sourceBox.centerY;
        e.tcx = e.targetBox.centerX;
        e.tcy = e.targetBox.centerY;
        int colS = bands.colIndexOf(e.sourceBox);
        int colT = bands.colIndexOf(e.targetBox);
        int rowS = bands.rowIndexOf(e.sourceBox);
        int rowT = bands.rowIndexOf(e.targetBox);
        e.rowS = rowS;
        e.colS = colS;
        e.colT = colT;

        e.rowT = rowT;
        boolean obstacleBetween =
            obstacles.blocks(List.of(new Point(e.scx, e.scy), new Point(e.tcx, e.tcy)), from, to);
        if (colT < colS) {
            // A backward edge runs against the flow, so it arcs over the rows it passes instead of
            // cutting back through them (spec 6.5).
            e.type = Type.BACKWARD;
            e.laneBase = aboveChannelY(Math.min(rowS, rowT), bands);
        } else if (!obstacleBetween && (colS == colT || (rowS == rowT && Math.abs(colT - colS) < 2))) {
            e.type = Type.STRAIGHT;
        } else {
            shapeAsRouted(e, bands);
        }
        return e;
    }

    /**
     * Gives an edge the routed shape for its row relationship: a same-row bypass through the row's
     * channel, or a cross-row elbow. This is candidate level 1 — what an edge takes when the direct
     * segment between the two nodes is not clear.
     */
    private void shapeAsRouted(Edge e, Bands bands) {
        if (e.rowS == e.rowT) {
            e.type = Type.BYPASS;
            // Channel below the row, kept clear both of the label text drawn beneath the icons and of
            // any sibling in this row that is taller than a plain activity (e.g. a group box).
            e.laneBase = channelY(e.rowS, bands);
            // Leave the source on its right and re-enter the target from the left, through the
            // gutters between columns, so neither end crosses the label below an icon. The neighbour
            // columns come from the sibling bands, which cover every activity in the container; a
            // transition-derived grid would be blind to an intermediate activity (e.g. a bypassed
            // group) whose own edges never reach this container's routing.
            e.exitX = exitCorridorX(e.sourceBox, bands);
            e.approachX = approachCorridorX(e.targetBox, bands);
        } else {
            e.type = Type.ELBOW;
            // An approach that is more vertical than horizontal leaves the source downwards and comes
            // down onto the target, so it uses the row channel rather than a column gutter. Upward
            // approaches keep the side-entering shape until backward edges get their own channel.
            // Two nodes in one column are the exception: that shape returns to the source's own x, so
            // its horizontal run has no length and the route is the blocked segment again — such an
            // edge has to go round the side, through a column gutter (the mirror of a same-row bypass).
            e.verticalApproach = e.colS != e.colT
                && Math.abs(e.tcy - e.scy) > Math.abs(e.tcx - e.scx) && e.tcy > e.scy;
            if (e.verticalApproach) {
                e.laneBase = channelY(e.rowS, bands);
            }
            // Otherwise the gutter the vertical run uses depends on the fan role, decided in
            // assignElbowGutter once the fan-out / fan-in flags are known.
        }
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

    /**
     * The channel above the given band: the midpoint of the gap to the band before it, or clear above the
     * first one.
     */
    private static double aboveChannelY(int bandIndex, Bands bands) {
        double top = bands.rowTop(bandIndex);
        double mid = bandIndex > 0
            ? (bands.rowBottom(bandIndex - 1) + top) / 2.0
            : top - (DEFAULT_ROW_SPACING - DEFAULT_ACTIVITY_SIZE) / 2.0;
        return Math.max(mid, bands.ceiling());
    }

    /** The channel above the given band, or progressively further above the diagram's first band. */
    private static double aboveChannelBand(int bandIndex, Bands bands) {
        if (bandIndex >= 0) {
            return aboveChannelY(bandIndex, bands);
        }
        return Math.max(aboveChannelY(0, bands) + bandIndex * (double) DEFAULT_ROW_SPACING,
            bands.ceiling());
    }

    /** The channel below the given band, or progressively further below the diagram's last band. */
    private static double channelBelowBand(int bandIndex, Bands bands) {
        if (bandIndex < bands.rowCount()) {
            return channelY(bandIndex, bands);
        }
        return channelY(bands.rowCount() - 1, bands)
            + (bandIndex - bands.rowCount() + 1) * (double) DEFAULT_ROW_SPACING;
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
     * Makes every branch of a fan take the same route shape. The channel shape and the gutter shape nest
     * their lanes independently, so a fan split between the two has branches crossing each other; only a
     * fan that approaches vertically throughout keeps the channel shape.
     */
    private static void alignFanShapes(List<Edge> edges) {
        Map<String, List<Edge>> fans = new LinkedHashMap<>();
        for (Edge e : edges) {
            if (e.sourceFanOut) {
                fans.computeIfAbsent("out:" + e.transition.getFromActivity().getId(),
                    k -> new ArrayList<>()).add(e);
            }
            if (e.targetFanIn) {
                fans.computeIfAbsent("in:" + e.transition.getToActivity().getId(),
                    k -> new ArrayList<>()).add(e);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (List<Edge> fan : fans.values()) {
                if (fan.stream().anyMatch(e -> !e.verticalApproach)) {
                    for (Edge e : fan) {
                        changed |= e.verticalApproach;
                        e.verticalApproach = false;
                    }
                }
            }
        }
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
     * land on the same row, see {@link Obstacles} — the run is routed through the row's
     * below-row channel instead (like a {@link Type#BYPASS}) so it clears that sibling and its edges.
     */
    private void assignElbowGutter(Edge e, Obstacles obstacles, Bands bands) {
        if (e.targetFanIn && !e.sourceFanOut) {
            e.laneBase = approachCorridorX(e.targetBox, bands);
            e.gutterCol = e.colT - 1;
            e.rowDetour = obstacles.blocks(
                List.of(new Point(e.scx, e.scy), new Point(e.laneBase, e.scy)),
                e.transition.getFromActivity(), e.transition.getToActivity());
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
        // Lanes are re-assigned on every escalation round, so clear what the previous round staggered:
        // an edge that has since changed shape would otherwise keep an offset nothing recomputes.
        for (Edge e : edges) {
            e.nodeStagger = 0;
        }
        Map<Integer, List<Edge>> bypassByRow = new LinkedHashMap<>();
        Map<Integer, List<Edge>> backwardByRow = new LinkedHashMap<>();
        Map<Integer, List<Edge>> elbowByGutter = new LinkedHashMap<>();
        Map<Integer, List<Edge>> rowDetourByRow = new LinkedHashMap<>();
        Set<Integer> gutterColumnsClaimedByBypass = new HashSet<>();
        for (Edge e : edges) {
            if (e.type == Type.BACKWARD) {
                backwardByRow.computeIfAbsent(Math.min(e.rowS, e.rowT), k -> new ArrayList<>()).add(e);
            }
            if (e.type == Type.BYPASS || e.verticalApproach) {
                bypassByRow.computeIfAbsent(e.rowS, k -> new ArrayList<>()).add(e);
            }
            if (e.type == Type.BYPASS) {
                // A bypass approaches its target through the gutter just left of the target column —
                // the same reference gutter a fan-in elbow to the same target uses (see
                // assignElbowGutter). Claim it so that group's lanes start one further out below.
                gutterColumnsClaimedByBypass.add(e.colT - 1);
            } else if (e.type == Type.ELBOW && !e.verticalApproach) {
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
        for (List<Edge> group : backwardByRow.values()) {
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
        // A bypass leaves its source on the same side, and at the same y, as a straight sibling, and
        // the elbow stagger above never reaches it — so a fan mixing the two shapes draws both along one
        // run out of the node, hiding a server-coloured executed transition behind an unexecuted one.
        // Step each bypass below the runs already taken at that source, towards the channel it dives to.
        Map<String, List<Edge>> bypassBySource = new LinkedHashMap<>();
        for (Edge e : edges) {
            if (e.type == Type.BYPASS && e.sourceFanOut) {
                bypassBySource.computeIfAbsent(e.transition.getFromActivity().getId(),
                    k -> new ArrayList<>()).add(e);
            }
        }
        for (Map.Entry<String, List<Edge>> sourceGroup : bypassBySource.entrySet()) {
            double taken = 0;
            for (Edge e : edges) {
                if (sourceGroup.getKey().equals(e.transition.getFromActivity().getId())) {
                    taken = Math.max(taken, e.nodeStagger);
                }
            }
            List<Edge> group = sourceGroup.getValue();
            group.sort(bypassOrder);
            for (int i = 0; i < group.size(); i++) {
                group.get(i).nodeStagger = taken + (i + 1) * (LANE_GAP / 2.0);
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

    /**
     * The waypoints every side-entering route is built from: leave the source along its row, optionally
     * drop into a below-row channel to clear what sits in the way, cross in a vertical corridor, and
     * land on the target along a horizontal run. A {@link Type#BYPASS} crosses in the corridor before
     * its target and always takes the channel; a side-entering {@link Type#ELBOW} crosses in its gutter
     * and takes the channel only when detouring. They are otherwise the same path, so they are built
     * here rather than separately — the shapes drifted apart twice while they were.
     *
     * @param channelY the channel to detour through, or {@code null} to cross straight from the exit run
     */
    private static List<Point> sideEntryWaypoints(Edge e, double exitY, double entryY, double corridorX,
        Double channelY) {
        List<Point> wp = new ArrayList<>();
        wp.add(new Point(e.scx, exitY));
        double crossingY = exitY;
        if (channelY != null) {
            wp.add(new Point(e.exitX, exitY));
            wp.add(new Point(e.exitX, channelY));
            crossingY = channelY;
        }
        wp.add(new Point(corridorX, crossingY));
        wp.add(new Point(corridorX, entryY));
        wp.add(new Point(e.tcx, entryY));
        return wp;
    }

    /**
     * The waypoints every route that crosses in a horizontal channel is built from: leave the source
     * vertically, run across in the channel, and come back onto the target. A {@link Type#BACKWARD} edge
     * uses a channel above its row and a vertically approaching {@link Type#ELBOW} one below, which is
     * the only thing that differs between them.
     */
    private static List<Point> channelCrossingWaypoints(Edge e, double channelY) {
        return List.of(new Point(e.scx, e.scy), new Point(e.scx, channelY),
            new Point(e.tcx, channelY), new Point(e.tcx, e.tcy));
    }

    private Route buildRoute(Edge e) {
        // Each shape below sets only the attachment sides it needs, and the same Edge is rebuilt on
        // every escalation round — so clear them first. A side left over from a shape this edge no
        // longer has would attach the drawn line to an edge of the icon its route never approaches.
        e.exitSide = null;
        e.entrySide = null;
        List<Point> wp = new ArrayList<>();
        double labelX;
        double labelY;
        double labelWidth;
        String labelAnchor = "middle";
        switch (e.type) {
            case BYPASS: {
                double cy = e.laneBase + e.lane * LANE_GAP;
                wp.addAll(sideEntryWaypoints(e, e.scy + e.nodeStagger, e.tcy + TARGET_ENTRY_OFFSET,
                    e.approachX, cy));
                labelX = (e.exitX + e.approachX) / 2.0;
                labelY = cy + DEFAULT_TEXT_SIZE;
                labelWidth = Math.abs(e.approachX - e.exitX);
                break;
            }
            case BACKWARD: {
                double cy = e.laneBase - e.lane * LANE_GAP;
                e.exitSide = Side.TOP;
                e.entrySide = Side.TOP;
                wp.addAll(channelCrossingWaypoints(e, cy));
                labelX = (e.scx + e.tcx) / 2.0;
                labelY = cy - LABEL_GAP;
                labelWidth = Math.abs(e.tcx - e.scx);
                break;
            }
            case ELBOW: {
                if (e.verticalApproach) {
                    double cy = e.laneBase + e.lane * LANE_GAP;
                    e.exitSide = Side.BOTTOM;
                    e.entrySide = Side.TOP;
                    wp.addAll(channelCrossingWaypoints(e, cy));
                    labelX = (e.scx + e.tcx) / 2.0;
                    labelY = cy + DEFAULT_TEXT_SIZE;
                    labelWidth = Math.abs(e.tcx - e.scx);
                    break;
                }
                double gx = e.laneBase + e.lane * LANE_GAP;
                // Stagger exit/entry y at the node so sibling branches do not share a collinear
                // horizontal segment — which would hide a server-coloured (green) executed transition
                // behind unexecuted siblings drawn on top of it. Pure fan-in elbows stagger at the
                // target; all other elbows (fan-out or plain) stagger at the source.
                double exitY = (e.targetFanIn && !e.sourceFanOut) ? e.scy : e.scy + e.nodeStagger;
                double entryY = (e.targetFanIn && !e.sourceFanOut) ? e.tcy + e.nodeStagger : e.tcy;
                // A row detour drops into the row's below-row channel before crossing: the exit run
                // along the source row would otherwise cross a sibling sitting between the source and
                // the gutter (see assignElbowGutter), the same obstacle a BYPASS clears that way.
                Double channelY = e.rowDetour ? e.channelBase + e.channelLane * LANE_GAP : null;
                wp.addAll(sideEntryWaypoints(e, exitY, entryY, gx, channelY));
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
                if (Math.abs(e.tcy - e.scy) > Math.abs(e.tcx - e.scx)) {
                    // A straight edge whose approach is vertical leaves and arrives on the horizontal
                    // edges, so it clears the label band instead of ending inside it.
                    e.exitSide = exitSide(e.sourceBox, e.targetBox);
                    e.entrySide = entrySide(e.sourceBox, e.targetBox);
                }
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
        Point start = attachment(e.sourceBox, e.exitSide, wp.get(0), wp.get(1),
            DEFAULT_ACTIVITY_RADIUS, 0);
        Point end = attachment(e.targetBox, e.entrySide, wp.get(wp.size() - 1), wp.get(wp.size() - 2),
            DEFAULT_ACTIVITY_RADIUS + (double) DEFAULT_MARKER_SIZE, DEFAULT_MARKER_SIZE);
        return new Route(wp, start, end, labelX, labelY, labelWidth, labelAnchor);
    }

    // --- drawing -----------------------------------------------------------------------------------

    private void drawRoutedTransition(NjamsProcessDiagramContext context, TransitionModel t, Route route) {
        String markerId = createArrowMarker(context, t);

        List<Point> wp = route.waypoints;

        StringBuilder points = new StringBuilder();
        appendPoint(points, context, route.start);
        for (int i = 1; i < wp.size() - 1; i++) {
            appendPoint(points, context, wp.get(i));
        }
        appendPoint(points, context, route.end);

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

    /**
     * Where the drawn line meets a node. A route that knows which side it uses, and every route touching a
     * group, meets the node's edge there; anything else keeps to the icon's radius along its own direction.
     */
    private static Point attachment(NodeBox node, Side side, Point center, Point towards, double radius,
        double clearance) {
        Side resolved = side != null || !node.group ? side : sideTowards(center, towards);
        return resolved != null ? attachmentPoint(node, resolved, clearance)
            : radiusPoint(towards, center, radius);
    }

    /** The side a segment running from {@code from} to {@code to} leaves {@code from} by. */
    private static Side sideTowards(Point from, Point to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? Side.RIGHT : Side.LEFT;
        }
        return dy >= 0 ? Side.BOTTOM : Side.TOP;
    }

    /**
     * The side of the source an edge leaves by: the dominant direction of the approach, so a mostly
     * vertical edge leaves top or bottom rather than swinging out sideways.
     */
    private static Side exitSide(NodeBox source, NodeBox target) {
        double dx = target.centerX - source.centerX;
        double dy = target.centerY - source.centerY;
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? Side.RIGHT : Side.LEFT;
        }
        return dy >= 0 ? Side.BOTTOM : Side.TOP;
    }

    /** The side of the target an edge arrives at: the one facing {@link #exitSide}. */
    private static Side entrySide(NodeBox source, NodeBox target) {
        switch (exitSide(source, target)) {
            case RIGHT:
                return Side.LEFT;
            case LEFT:
                return Side.RIGHT;
            case BOTTOM:
                return Side.TOP;
            default:
                return Side.BOTTOM;
        }
    }

    /**
     * Where an edge meets a node on the given side, pulled back by {@code clearance} (the arrow head's
     * length at the arriving end, nothing at the leaving end). A plain activity is met on its icon
     * radius, except from below, where the label band under the icon has to stay clear; a group is met
     * on its box edge.
     */
    private static Point attachmentPoint(NodeBox node, Side side, double clearance) {
        if (node.group) {
            switch (side) {
                case LEFT:
                    return new Point(node.left - clearance, node.centerY);
                case RIGHT:
                    return new Point(node.right + clearance, node.centerY);
                case TOP:
                    return new Point(node.centerX, node.top - clearance);
                default:
                    return new Point(node.centerX, node.bottom + clearance);
            }
        }
        switch (side) {
            case LEFT:
                return new Point(node.centerX - DEFAULT_ACTIVITY_RADIUS - clearance, node.centerY);
            case RIGHT:
                return new Point(node.centerX + DEFAULT_ACTIVITY_RADIUS + clearance, node.centerY);
            case TOP:
                return new Point(node.centerX, node.centerY - DEFAULT_ACTIVITY_RADIUS - clearance);
            default:
                return new Point(node.centerX, node.bottom + LABEL_BAND_HEIGHT + clearance);
        }
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
     * The boxes a route must keep clear of, and the test for whether a candidate route hits one. Every
     * sibling in the container counts, including ones whose own edges never reach this routing; an edge's
     * own endpoints are excluded, since a route is expected to start and end at them.
     */
    private static final class Obstacles {
        private final List<ActivityModel> nodes;
        private final List<double[]> extents;

        private Obstacles(List<ActivityModel> nodes, List<double[]> extents) {
            this.nodes = nodes;
            this.extents = extents;
        }

        static Obstacles of(List<ActivityModel> siblings) {
            List<ActivityModel> nodes = new ArrayList<>(siblings);
            List<double[]> extents = new ArrayList<>(siblings.size());
            for (ActivityModel sibling : nodes) {
                double[] extent = NodeBox.of(sibling).obstacleExtent();
                extents.add(new double[] {extent[0] - CLEARANCE_MARGIN, extent[1] - CLEARANCE_MARGIN,
                    extent[2] + CLEARANCE_MARGIN, extent[3] + CLEARANCE_MARGIN});
            }
            return new Obstacles(nodes, extents);
        }

        boolean blocks(List<Point> waypoints, ActivityModel source, ActivityModel target) {
            for (int i = 0; i < nodes.size(); i++) {
                ActivityModel node = nodes.get(i);
                if (node == source || node == target) {
                    continue;
                }
                for (int w = 0; w + 1 < waypoints.size(); w++) {
                    if (segmentIntersectsRect(waypoints.get(w), waypoints.get(w + 1), extents.get(i))) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** Liang-Barsky clip: whether the segment has any point inside {left, top, right, bottom}. */
        private static boolean segmentIntersectsRect(Point from, Point to, double[] rect) {
            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            double[] edge = {-dx, dx, -dy, dy};
            double[] distance = {from.getX() - rect[0], rect[2] - from.getX(),
                from.getY() - rect[1], rect[3] - from.getY()};
            double enter = 0;
            double leave = 1;
            for (int i = 0; i < 4; i++) {
                if (edge[i] == 0) {
                    if (distance[i] < 0) {
                        return false;
                    }
                } else if (edge[i] < 0) {
                    enter = Math.max(enter, distance[i] / edge[i]);
                } else {
                    leave = Math.min(leave, distance[i] / edge[i]);
                }
            }
            return enter <= leave;
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

        private final double ceiling;

        private Bands(List<double[]> rows, List<double[]> columns, double ceiling) {
            this.rows = rows;
            this.columns = columns;
            this.ceiling = ceiling;
        }

        static Bands of(List<ActivityModel> siblings, double ceiling) {
            List<double[]> vertical = new ArrayList<>();
            List<double[]> horizontal = new ArrayList<>();
            for (ActivityModel sibling : siblings) {
                NodeBox box = NodeBox.of(sibling);
                vertical.add(new double[] {box.top, box.bottom});
                horizontal.add(new double[] {box.left, box.right});
            }
            return new Bands(merge(vertical), merge(horizontal), ceiling);
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

        /**
         * The band the given extent belongs to. A layouter that never sizes a group leaves its box with
         * no extent at all (see {@link NodeBox#of}); such a point still belongs to the band it sits in,
         * so an empty extent — on either side — is compared inclusively. An extent that overlaps no band
         * at all takes the nearest one, because every caller indexes a band list with the result.
         */
        private static int indexOf(List<double[]> bands, double start, double end) {
            int nearest = 0;
            double nearestDistance = Double.MAX_VALUE;
            for (int i = 0; i < bands.size(); i++) {
                double[] band = bands.get(i);
                if (overlaps(band, start, end)) {
                    return i;
                }
                double distance = Math.max(band[0] - end, start - band[1]);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = i;
                }
            }
            return nearest;
        }

        private static boolean overlaps(double[] band, double start, double end) {
            if (start == end) {
                return start >= band[0] && start <= band[1];
            }
            if (band[0] == band[1]) {
                return band[0] >= start && band[0] <= end;
            }
            return start < band[1] && band[0] < end;
        }

        int rowCount() {
            return rows.size();
        }

        /** The highest y a route may use, so an arc above the first row stays inside its container. */
        double ceiling() {
            return ceiling;
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
        STRAIGHT, BYPASS, ELBOW, BACKWARD
    }

    /** The side of a node a route meets it on. */
    private enum Side {
        LEFT, RIGHT, TOP, BOTTOM
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
        private boolean verticalApproach;
        private boolean gutterAssigned;
        private int rowT;
        private int level;
        private Side exitSide;
        private Side entrySide;
        private double channelBase;
        private int channelLane;
    }

    /**
     * Routed path (model coordinates) plus the chosen label anchor. The waypoints run between the two
     * node centres; {@code start} and {@code end} are where the drawn line actually begins and ends,
     * clear of the icons and their labels.
     */
    private static final class Route {
        private final List<Point> waypoints;
        private final Point start;
        private final Point end;
        private final double labelX;
        private final double labelY;
        private final double labelWidth;
        private final String labelAnchor;

        Route(List<Point> waypoints, Point start, Point end, double labelX, double labelY,
            double labelWidth, String labelAnchor) {
            this.waypoints = waypoints;
            this.start = start;
            this.end = end;
            this.labelX = labelX;
            this.labelY = labelY;
            this.labelWidth = labelWidth;
            this.labelAnchor = labelAnchor;
        }
    }
}
