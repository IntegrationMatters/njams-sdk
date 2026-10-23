package com.im.njams.sdk.model.svg;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.TransitionModel;
import com.im.njams.sdk.model.layout.CommonBfsModelLayouter;
import com.im.njams.sdk.settings.ClientSettings;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class PolylineProcessDiagramFactoryTest {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    private ProcessModel createProcess() {
        ClientSettings settings = TestSender.getSettings();
        Njams njams = new Njams(Path.of("TEST"), "1.0", "TEST", settings);
        return njams.model().create("proc");
    }

    private String render(ProcessModel model) {
        new CommonBfsModelLayouter().layout(model);
        Njams njams = model.getNjams();
        return new PolylineProcessDiagramFactory(njams).getProcessDiagram(model);
    }


    /**
     * Renders with explicitly placed coordinates instead of the layouter, for geometries the
     * {@link CommonBfsModelLayouter} does not produce (loop-backs, near-misaligned rows).
     */
    private String renderWithoutLayout(ProcessModel model, Runnable placeCoordinates) {
        placeCoordinates.run();
        Njams njams = model.getNjams();
        return new PolylineProcessDiagramFactory(njams).getProcessDiagram(model);
    }

    private static Document parse(String svg) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder()
            .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }


    /** The drawable area of the document: the root svg element's own extent. */
    private static double[] canvasBox(Document doc) {
        Element svg = doc.getDocumentElement();
        return new double[] {0, 0, Double.parseDouble(svg.getAttribute("width")),
            Double.parseDouble(svg.getAttribute("height"))};
    }

    /** Bounding boxes of all plain activities (50px images), in SVG coordinates. */
    private static List<double[]> activityBoxes(Document doc) {
        List<double[]> boxes = new ArrayList<>();
        NodeList images = doc.getElementsByTagNameNS(SVG_NS, "image");
        for (int i = 0; i < images.getLength(); i++) {
            Element e = (Element) images.item(i);
            if (!"true".equals(e.getAttribute("activity"))) {
                continue;
            }
            double w = Double.parseDouble(e.getAttribute("width"));
            if (w < 40) {
                continue; // skip group header icons (16px)
            }
            double x = Double.parseDouble(e.getAttribute("x"));
            double y = Double.parseDouble(e.getAttribute("y"));
            boxes.add(new double[] {x, y, x + w, y + Double.parseDouble(e.getAttribute("height"))});
        }
        return boxes;
    }


    /** Bounding box of one activity's icon, by model id, in SVG coordinates. */
    private static double[] activityBoxFor(Document doc, ProcessModel model, String activityId) {
        String modelId = model.getActivity(activityId).getId();
        NodeList images = doc.getElementsByTagNameNS(SVG_NS, "image");
        for (int i = 0; i < images.getLength(); i++) {
            Element e = (Element) images.item(i);
            if (!modelId.equals(e.getAttribute("modelId")) || !"true".equals(e.getAttribute("activity"))) {
                continue;
            }
            double x = Double.parseDouble(e.getAttribute("x"));
            double y = Double.parseDouble(e.getAttribute("y"));
            return new double[] {x, y, x + Double.parseDouble(e.getAttribute("width")),
                y + Double.parseDouble(e.getAttribute("height"))};
        }
        throw new AssertionError("no activity image for " + activityId);
    }

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

    /**
     * A transition between two children of a group stays inside that group's content area. Such an edge
     * necessarily runs through the group's box, so the crossing assertion says nothing about it;
     * containment is the invariant that does, and it also pins that the edge keeps clear of the header.
     */
    private static void assertTransitionStaysInsideGroupContent(Document doc, String groupModelId,
        String transitionModelId) {
        double[] content = groupContentBox(doc, groupModelId);
        Element poly = findTransitionPolyline(doc, transitionModelId);
        Assert.assertNotNull("transition " + transitionModelId + " must be routed", poly);
        for (double[] point : points(poly)) {
            Assert.assertTrue(
                "Transition '" + transitionModelId + "' leaves the content area of group " + groupModelId
                    + " at " + point[0] + "," + point[1],
                point[0] >= content[0] && point[0] <= content[2]
                    && point[1] >= content[1] && point[1] <= content[3]);
        }
    }

    /** The content rect of one group (its box below the header), in SVG coordinates. */
    private static double[] groupContentBox(Document doc, String groupModelId) {
        NodeList rects = doc.getElementsByTagNameNS(SVG_NS, "rect");
        for (int r = 0; r < rects.getLength(); r++) {
            Element rect = (Element) rects.item(r);
            if (!(groupModelId + "_group_container").equals(rect.getAttribute("id"))) {
                continue;
            }
            double x = Double.parseDouble(rect.getAttribute("x"));
            double y = Double.parseDouble(rect.getAttribute("y"));
            return new double[] {x, y, x + Double.parseDouble(rect.getAttribute("width")),
                y + Double.parseDouble(rect.getAttribute("height"))};
        }
        throw new AssertionError("no content rect for group " + groupModelId);
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

    /** Transition polylines (those carrying a modelId), as point lists. */
    private static List<Element> transitionPolylines(Document doc) {
        List<Element> result = new ArrayList<>();
        NodeList lines = doc.getElementsByTagNameNS(SVG_NS, "polyline");
        for (int i = 0; i < lines.getLength(); i++) {
            Element e = (Element) lines.item(i);
            if (e.hasAttribute("modelId")) {
                result.add(e);
            }
        }
        return result;
    }

    private static List<double[]> points(Element polyline) {
        List<double[]> pts = new ArrayList<>();
        for (String token : polyline.getAttribute("points").trim().split("\\s+")) {
            String[] xy = token.split(",");
            pts.add(new double[] {Double.parseDouble(xy[0]), Double.parseDouble(xy[1])});
        }
        return pts;
    }

    /** Liang-Barsky: does segment p->q intersect axis-aligned rect (shrunk slightly to ignore touches)? */
    private static boolean segmentIntersectsRect(double[] p, double[] q, double[] rect) {
        double pad = 0.5;
        double xmin = rect[0] + pad;
        double ymin = rect[1] + pad;
        double xmax = rect[2] - pad;
        double ymax = rect[3] - pad;
        double dx = q[0] - p[0];
        double dy = q[1] - p[1];
        double[] pp = {-dx, dx, -dy, dy};
        double[] qq = {p[0] - xmin, xmax - p[0], p[1] - ymin, ymax - p[1]};
        double u1 = 0;
        double u2 = 1;
        for (int i = 0; i < 4; i++) {
            if (pp[i] == 0) {
                if (qq[i] < 0) {
                    return false;
                }
            } else {
                double t = qq[i] / pp[i];
                if (pp[i] < 0) {
                    u1 = Math.max(u1, t);
                } else {
                    u2 = Math.min(u2, t);
                }
            }
        }
        return u1 <= u2;
    }

    @Test
    public void routedWaypointsStayInsideTheCanvas() throws Exception {
        // The loop-back arc sits above every activity, and the top row is placed at the very top of the
        // model, so a canvas sized from the activities and their labels alone clips the arc away.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        ActivityModel c = model.createActivity("C", "C", null);
        a.setStarter(true);
        model.createTransition("A", "B");
        model.createTransition("B", "C");
        model.createTransition("C", "A").setName("retry");

        Document doc = parse(renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(0);
            b.setX(150);
            b.setY(0);
            c.setX(300);
            c.setY(0);
        }));

        double[] canvas = canvasBox(doc);
        for (Element poly : transitionPolylines(doc)) {
            for (double[] point : points(poly)) {
                Assert.assertTrue("waypoint " + point[0] + "," + point[1] + " falls outside the canvas",
                    point[0] >= canvas[0] && point[1] >= canvas[1]
                        && point[0] <= canvas[2] && point[1] <= canvas[3]);
            }
        }
    }

    @Test
    public void aBackwardEdgeInATightGroupStaysBelowTheGroupHeader() throws Exception {
        // The children sit close under the header, so the channel the arc would naturally take lies
        // above the group's content area -- outside the box the arc is supposed to stay in.
        ProcessModel model = createProcess();
        GroupModel g = model.createGroup("G", "G", null);
        ActivityModel p = g.createChildActivity("P", "P", null);
        ActivityModel q = g.createChildActivity("Q", "Q", null);
        p.setStarter(true);
        model.createTransition("P", "Q");
        model.createTransition("Q", "P").setName("back");

        Document doc = parse(renderWithoutLayout(model, () -> {
            g.setX(0);
            g.setY(0);
            g.setWidth(300);
            g.setHeight(200);
            p.setX(10);
            p.setY(40);
            q.setX(160);
            q.setY(40);
        }));

        assertTransitionStaysInsideGroupContent(doc, "G", transitionId(model, "Q", "P"));
        assertNoTransitionCrossesAnyActivity(doc);
    }

    @Test
    public void transitionsIntoAndOutOfAGroupAreRoutedNotFallenBackTo() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        GroupModel g = model.createGroup("G", "G", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        ActivityModel h = g.createChildActivity("H", "H", null);
        h.setStarter(true);
        model.createTransition("A", "G");
        model.createTransition("G", "B");

        Document doc = parse(renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(100);
            g.setX(150);
            g.setY(60);
            g.setWidth(120);
            g.setHeight(140);
            h.setX(180);
            h.setY(110);
            b.setX(400);
            b.setY(100);
        }));

        Assert.assertNotNull("A -> G must be routed",
            findTransitionPolyline(doc, transitionId(model, "A", "G")));
        Assert.assertNotNull("G -> B must be routed",
            findTransitionPolyline(doc, transitionId(model, "G", "B")));
        assertNoTransitionCrossesAnyGroupBox(doc);
        assertNoTransitionCrossesAnyActivity(doc);
    }

    @Test
    public void aBackwardEdgeArcsAboveTheRowAndCrossesNothing() throws Exception {
        // CommonBfsModelLayouter does not produce loop-back shapes (it places a loop head to the right),
        // so the coordinates are set explicitly -- see spec section 6.5.
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
            a.setX(0);
            a.setY(100);
            b.setX(150);
            b.setY(100);
            c.setX(300);
            c.setY(100);
            d.setX(450);
            d.setY(100);
        }));

        List<double[]> pts = points(findTransitionPolyline(doc, transitionId(model, "D", "A")));
        double topOfRow = activityBoxFor(doc, model, "A")[1];
        Assert.assertTrue("the loop-back run must arc above the row, not below it where the channels are",
            pts.stream().anyMatch(point -> point[1] < topOfRow));
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

        Document doc = parse(renderWithoutLayout(model, () -> {
            g.setX(0);
            g.setY(0);
            g.setWidth(300);
            g.setHeight(200);
            p.setX(10);
            p.setY(90);
            q.setX(160);
            q.setY(90);
        }));

        assertTransitionStaysInsideGroupContent(doc, "G", transitionId(model, "Q", "P"));
        assertNoTransitionCrossesAnyActivity(doc);
    }

    @Test
    public void allBranchesOfAFanTakeTheSameShapeFamily() throws Exception {
        // One branch would be clear as a straight line while its siblings need an elbow. If they are
        // allowed to differ, the lane-nesting non-crossing argument no longer covers the mixed group.
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
            a.setX(0);
            a.setY(0);
            b.setX(10);
            b.setY(10);
            c.setX(20);
            c.setY(20);
        }));

        Element poly = findTransitionPolyline(doc, transitionId(model, "A", "C"));
        Assert.assertNotNull("a blocked edge must still be emitted", poly);
        Assert.assertFalse("its points attribute must not be empty", poly.getAttribute("points").isEmpty());
    }

    @Test
    public void aRunDisplacedOntoALowerActivityByItsLaneIsEscalated() throws Exception {
        // Three bypasses share the row's channel, so the third runs two lane widths below the first.
        // That offset is applied after the shape is chosen: the shape is clear at the channel base and
        // only the displaced copy crosses Z, which a check made before laning cannot see.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        ActivityModel c = model.createActivity("C", "C", null);
        ActivityModel d = model.createActivity("D", "D", null);
        ActivityModel e = model.createActivity("E", "E", null);
        ActivityModel z = model.createActivity("Z", "Z", null);
        a.setStarter(true);
        model.createTransition("A", "B");
        model.createTransition("B", "C");
        model.createTransition("C", "D");
        model.createTransition("D", "E");
        model.createTransition("A", "C").setName("skip1");
        model.createTransition("B", "D").setName("skip2");
        model.createTransition("C", "E").setName("skip3");

        Document doc = parse(renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(0);
            b.setX(150);
            b.setY(0);
            c.setX(300);
            c.setY(0);
            d.setX(450);
            d.setY(0);
            e.setX(600);
            e.setY(0);
            z.setX(520);
            z.setY(100);
        }));

        assertNoTransitionCrossesAnyActivity(doc);
        assertNoTransitionCrossesAnyLabelBand(doc);
    }

    @Test
    public void aVerticallyDominantEdgeEntersTheTargetFromTheTop() throws Exception {
        // B sits far below A and barely to its side, so the approach is vertical. Routing it as a
        // side-entering elbow would swing the arrow around into B's left flank.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        model.createTransition("A", "B");

        Document doc = parse(renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(0);
            b.setX(60);
            b.setY(400);
        }));

        List<double[]> pts = points(findTransitionPolyline(doc, transitionId(model, "A", "B")));
        double[] last = pts.get(pts.size() - 1);
        double[] targetBox = activityBoxFor(doc, model, "B");
        Assert.assertTrue("the arrow must come down onto B's top edge, not around into its left flank",
            last[1] <= targetBox[1] + 1);
        assertNoTransitionCrossesAnyActivity(doc);
        assertNoTransitionCrossesAnyLabelBand(doc);
    }

    @Test
    public void anEdgeArrivingFromBelowEndsAtTheLabelBandsLowerEdgeAndDoesNotEnterIt() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        model.createTransition("A", "B");

        Document doc = parse(renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(300);
            b.setX(0);
            b.setY(0);
        }));

        List<double[]> pts = points(findTransitionPolyline(doc, transitionId(model, "A", "B")));
        double[] last = pts.get(pts.size() - 1);
        double[] targetBox = activityBoxFor(doc, model, "B");
        Assert.assertTrue("the arrow must stop at or below the label band's lower edge",
            last[1] >= targetBox[3] + PolylineProcessDiagramFactory.LABEL_BAND_HEIGHT - 1);
        assertNoTransitionCrossesAnyLabelBand(doc);
    }

    @Test
    public void aFanInRunIsDetouredAroundASiblingWhoseRowIsOnlyNearlyTheSame() throws Exception {
        // S1 and S2 join into T, so S1's branch runs the length of its own row before bending into the
        // gutter left of T. O sits in that row, seven pixels off S1's exact y: an exact-y sibling scan
        // does not see it and the run crosses it; a segment-versus-box test does, and detours.
        ProcessModel model = createProcess();
        ActivityModel s1 = model.createActivity("S1", "S1", null);
        ActivityModel s2 = model.createActivity("S2", "S2", null);
        ActivityModel o = model.createActivity("O", "O", null);
        ActivityModel t = model.createActivity("T", "T", null);
        s1.setStarter(true);
        model.createTransition("S1", "T");
        model.createTransition("S2", "T");

        Document doc = parse(renderWithoutLayout(model, () -> {
            s1.setX(0);
            s1.setY(0);
            o.setX(200);
            o.setY(7);
            s2.setX(0);
            s2.setY(150);
            t.setX(600);
            t.setY(150);
        }));

        assertNoTransitionCrossesAnyActivity(doc);
        assertNoTransitionCrossesAnyLabelBand(doc);
    }

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
            x.setX(0);
            x.setY(0);
            a.setX(150);
            a.setY(0);
            y.setX(300);
            y.setY(7);
        }));

        assertNoTransitionCrossesAnyActivity(doc);
        assertNoTransitionCrossesAnyLabelBand(doc);
    }

    @Test
    public void aGroupAndAPlainActivityOnTheSameVisualBandShareARow() throws Exception {
        // A -> G (group with child H) -> B, plus the A -> B bypass. The group's box is taller than a
        // plain icon and its top is nudged off the plain activities', so identity-keyed rows put them in
        // different rows and the bypass loses its detour; overlap bands keep them together.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        GroupModel g = model.createGroup("G", "G", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        model.createTransition("A", "G");
        ActivityModel h = g.createChildActivity("H", "H", null);
        h.setStarter(true);
        model.createTransition("G", "B");
        model.createTransition("A", "B").setName("skip");

        Document doc = parse(renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(100);
            g.setX(150);
            g.setY(108);
            g.setWidth(120);
            g.setHeight(140);
            b.setX(400);
            b.setY(100);
            h.setX(160);
            h.setY(120);
        }));

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
            a.setX(0);
            a.setY(0);
            b.setX(0);
            b.setY(0);
        }));

        Element poly = findTransitionPolyline(doc, transitionId(model, "A", "B"));
        Assert.assertNotNull("a degenerate transition must still be rendered", poly);
        Assert.assertFalse("its points attribute must not be empty", poly.getAttribute("points").isEmpty());
    }

    @Test
    public void labelBandHeightIsDerivedFromTheTextSize() {
        // The band is the strip below the icon that drawActivity writes the name into: one text line plus
        // descender room. 22 is what the suite has always assumed; this pins the derivation, not the number.
        Assert.assertEquals(22, PolylineProcessDiagramFactory.LABEL_BAND_HEIGHT);
    }

    /** Label bands: the strip directly below each activity icon where its label text is drawn. */
    private static List<double[]> labelBands(Document doc) {
        List<double[]> bands = new ArrayList<>();
        for (double[] box : activityBoxes(doc)) {
            // box = [left, top, right, bottom]; the label sits in the band directly below the icon.
            bands.add(new double[] {box[0], box[3], box[2], box[3] + PolylineProcessDiagramFactory.LABEL_BAND_HEIGHT});
        }
        return bands;
    }

    private static void assertNoTransitionCrossesAnyLabelBand(Document doc) {
        List<double[]> bands = labelBands(doc);
        for (Element poly : transitionPolylines(doc)) {
            List<double[]> pts = points(poly);
            for (int i = 0; i + 1 < pts.size(); i++) {
                for (double[] band : bands) {
                    Assert.assertFalse(
                        "Transition '" + poly.getAttribute("modelId") + "' segment " + i
                            + " runs through an activity label band",
                        segmentIntersectsRect(pts.get(i), pts.get(i + 1), band));
                }
            }
        }
    }

    private static void assertNoTransitionCrossesAnyActivity(Document doc) {
        List<double[]> boxes = activityBoxes(doc);
        for (Element poly : transitionPolylines(doc)) {
            List<double[]> pts = points(poly);
            for (int i = 0; i + 1 < pts.size(); i++) {
                for (double[] box : boxes) {
                    Assert.assertFalse(
                        "Transition '" + poly.getAttribute("modelId") + "' segment " + i
                            + " crosses an activity box",
                        segmentIntersectsRect(pts.get(i), pts.get(i + 1), box));
                }
            }
        }
    }

    private static Element findTransitionPolyline(Document doc, String transitionId) {
        for (Element e : transitionPolylines(doc)) {
            if (transitionId.equals(e.getAttribute("modelId"))) {
                return e;
            }
        }
        return null;
    }

    private static String transitionId(ProcessModel model, String fromId, String toId) {
        for (TransitionModel t : model.getTransitionModels()) {
            if (t.getFromActivity() != null && t.getToActivity() != null
                && fromId.equals(t.getFromActivity().getId()) && toId.equals(t.getToActivity().getId())) {
                return t.getId();
            }
        }
        throw new AssertionError("transition " + fromId + "->" + toId + " not found");
    }

    @Test
    public void aStraightEdgeLeavingAGroupIsCheckedWhereItIsActuallyDrawn() throws Exception {
        // A straight edge is checked between the two centres, but it is drawn from the group's box
        // edge — at the box's mid height, not on that centre line. The two differ whenever the
        // centres are not aligned, and O sits in the gap between them: clear of the checked line,
        // crossed by the drawn one. O has to overlap G's x range so the two share a column band,
        // which is what keeps G -> B a straight edge rather than a bypass.
        //
        // The gap O occupies is narrow enough that no candidate clears it either — O also stands in
        // the exit run of the bypass G -> B escalates to, which is the documented fallback. What this
        // pins is that the edge is no longer passed off as clear: it is seen as blocked and routed.
        ProcessModel model = createProcess();
        GroupModel g = model.createGroup("G", "G", null);
        ActivityModel h = g.createChildActivity("H", "H", null);
        ActivityModel b = model.createActivity("B", "B", null);
        ActivityModel o = model.createActivity("O", "O", null);
        h.setStarter(true);
        model.createTransition("G", "B");

        Document doc = parse(renderWithoutLayout(model, () -> {
            g.setX(150);
            g.setY(60);
            g.setWidth(120);
            g.setHeight(200);
            h.setX(180);
            h.setY(120);
            b.setX(400);
            b.setY(40);
            o.setX(260);
            o.setY(150);
        }));

        List<double[]> pts = points(findTransitionPolyline(doc, transitionId(model, "G", "B")));
        Assert.assertTrue("G -> B crosses O where it is drawn, so it must not be left as a straight "
            + "segment (was " + pts.size() + " points)", pts.size() > 2);
    }

    @Test
    public void aBypassAndAnElbowOutOfOneActivityDoNotShareACorridor() throws Exception {
        // S -> Y is a bypass over A and S -> T changes rows. Both leave S to the right, and the
        // corridor just right of S's column is the bypass's exit and the elbow's lane-0 gutter, so
        // their vertical runs land on one x and overlap.
        ProcessModel model = createProcess();
        ActivityModel s = model.createActivity("S", "S", null);
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel y = model.createActivity("Y", "Y", null);
        ActivityModel t = model.createActivity("T", "T", null);
        s.setStarter(true);
        model.createTransition("S", "Y");
        model.createTransition("S", "T");

        Document doc = parse(renderWithoutLayout(model, () -> {
            s.setX(0);
            s.setY(0);
            a.setX(150);
            a.setY(0);
            y.setX(300);
            y.setY(0);
            t.setX(150);
            t.setY(200);
        }));

        List<double[]> bypass = points(findTransitionPolyline(doc, transitionId(model, "S", "Y")));
        List<double[]> elbow = points(findTransitionPolyline(doc, transitionId(model, "S", "T")));
        Assert.assertTrue("the bypass and the elbow must not run down the same corridor (both at "
                + bypass.get(1)[0] + ")",
            Math.abs(bypass.get(1)[0] - elbow.get(1)[0]) > 1);
    }

    @Test
    public void aRouteNeverRepeatsAWaypoint() throws Exception {
        // C overlaps A's column, so A and B end up in neighbouring column bands with C blocking the
        // direct segment. The bypass's exit corridor and its approach corridor are then the same gap,
        // and its shape collapses onto a repeated point that must not reach the polyline.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel c = model.createActivity("C", "C", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        model.createTransition("A", "B");

        Document doc = parse(renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(0);
            c.setX(40);
            c.setY(0);
            b.setX(100);
            b.setY(0);
        }));

        for (Element poly : transitionPolylines(doc)) {
            List<double[]> pts = points(poly);
            for (int i = 1; i < pts.size(); i++) {
                Assert.assertFalse("transition '" + poly.getAttribute("modelId") + "' repeats waypoint "
                        + pts.get(i)[0] + "," + pts.get(i)[1],
                    Math.abs(pts.get(i)[0] - pts.get(i - 1)[0]) < 0.001
                        && Math.abs(pts.get(i)[1] - pts.get(i - 1)[1]) < 0.001);
            }
        }
    }

    @Test
    public void aSameColumnSkipDoesNotShareItsRunOutOfTheSourceWithANeighbour() throws Exception {
        // The skip leaves A sideways, along the same row A -> X runs on, so the two would lie on top
        // of each other without the fan's exit stagger.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel c = model.createActivity("C", "C", null);
        ActivityModel b = model.createActivity("B", "B", null);
        ActivityModel x = model.createActivity("X", "X", null);
        a.setStarter(true);
        model.createTransition("A", "C");
        model.createTransition("C", "B");
        model.createTransition("A", "B");
        model.createTransition("A", "X");

        Document doc = parse(renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(0);
            c.setX(0);
            c.setY(150);
            b.setX(0);
            b.setY(300);
            x.setX(150);
            x.setY(0);
        }));

        List<double[]> skip = points(findTransitionPolyline(doc, transitionId(model, "A", "B")));
        List<double[]> neighbour = points(findTransitionPolyline(doc, transitionId(model, "A", "X")));
        Assert.assertTrue("the skip and A -> X must not leave A along the same y (was "
                + skip.get(0)[1] + " and " + neighbour.get(0)[1] + ")",
            Math.abs(skip.get(0)[1] - neighbour.get(0)[1]) > 1);
        assertNoTransitionsCrossEachOther(doc);
        assertNoTransitionCrossesAnyActivity(doc);
    }

    @Test
    public void anUpwardEdgeCrossesBetweenTheRowsAndNeverReachesOverItsTarget() throws Exception {
        // Approaching from below means crossing in the gap between the two rows. A channel above the
        // target's row overshoots it, and the run back down then goes through the target's icon and
        // its label before stopping underneath them.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        model.createTransition("A", "B");

        Document doc = parse(renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(400);
            b.setX(60);
            b.setY(0);
        }));

        List<double[]> pts = points(findTransitionPolyline(doc, transitionId(model, "A", "B")));
        double[] targetBox = activityBoxFor(doc, model, "B");
        for (double[] point : pts) {
            Assert.assertTrue("the edge approaches B from below, so no part of it may sit above B's "
                    + "lower edge (found " + point[0] + "," + point[1] + ")",
                point[1] >= targetBox[3]);
        }
    }

    @Test
    public void aVerticallyDominantEdgeGoingUpEntersTheTargetFromBelow() throws Exception {
        // Spec 6.3: a vertically dominant edge exits the top when its target is above and is entered
        // from the bottom. Only the downward half of that was implemented.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        model.createTransition("A", "B");

        Document doc = parse(renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(300);
            b.setX(60);
            b.setY(0);
        }));

        List<double[]> pts = points(findTransitionPolyline(doc, transitionId(model, "A", "B")));
        double[] sourceBox = activityBoxFor(doc, model, "A");
        double[] targetBox = activityBoxFor(doc, model, "B");
        Assert.assertTrue("the edge must leave A upwards, above its icon (was " + pts.get(0)[1] + ")",
            pts.get(0)[1] <= sourceBox[1] + 1);
        Assert.assertTrue("the edge must enter B from below, at its label band's lower edge (was "
                + pts.get(pts.size() - 1)[1] + ")",
            pts.get(pts.size() - 1)[1] >= targetBox[3] + PolylineProcessDiagramFactory.LABEL_BAND_HEIGHT - 1);
    }

    @Test
    public void aSecondBackwardEdgeInATightGroupAlsoStaysBelowTheGroupHeader() throws Exception {
        // Two backward edges share the channel above the row, so the second runs a lane width higher.
        // That offset is applied after the ceiling clamp, which is what the clamp has to survive.
        ProcessModel model = createProcess();
        GroupModel g = model.createGroup("G", "G", null);
        ActivityModel p = g.createChildActivity("P", "P", null);
        ActivityModel q = g.createChildActivity("Q", "Q", null);
        ActivityModel r = g.createChildActivity("R", "R", null);
        p.setStarter(true);
        model.createTransition("P", "Q");
        model.createTransition("Q", "R");
        model.createTransition("R", "P").setName("back far");
        model.createTransition("R", "Q").setName("back near");

        Document doc = parse(renderWithoutLayout(model, () -> {
            g.setX(0);
            g.setY(0);
            g.setWidth(450);
            g.setHeight(200);
            p.setX(10);
            p.setY(40);
            q.setX(160);
            q.setY(40);
            r.setX(310);
            r.setY(40);
        }));

        assertTransitionStaysInsideGroupContent(doc, "G", transitionId(model, "R", "P"));
        assertTransitionStaysInsideGroupContent(doc, "G", transitionId(model, "R", "Q"));
    }

    @Test
    public void aChannelInATightGroupStaysAboveTheGroupsLowerBorder() throws Exception {
        // The children sit just above the group's lower border, so the channel a bypass would take
        // below their row -- and the label drawn under it -- fall outside the content area.
        ProcessModel model = createProcess();
        GroupModel g = model.createGroup("G", "G", null);
        ActivityModel p = g.createChildActivity("P", "P", null);
        ActivityModel q = g.createChildActivity("Q", "Q", null);
        ActivityModel r = g.createChildActivity("R", "R", null);
        p.setStarter(true);
        model.createTransition("P", "Q");
        model.createTransition("Q", "R");
        model.createTransition("P", "R").setName("skip");

        Document doc = parse(renderWithoutLayout(model, () -> {
            g.setX(0);
            g.setY(0);
            g.setWidth(450);
            g.setHeight(100);
            p.setX(10);
            p.setY(40);
            q.setX(160);
            q.setY(40);
            r.setX(310);
            r.setY(40);
        }));

        assertTransitionStaysInsideGroupContent(doc, "G", transitionId(model, "P", "R"));
    }

    @Test
    public void anEdgeDemotedFromAVerticalApproachDoesNotKeepItsBottomAttachment() throws Exception {
        // A -> B starts as a vertical approach, is blocked, and escalates to a lateral gutter elbow.
        // Its first run is then horizontal, so it must leave A's side — not the lower edge of A's
        // label band, which would cut back diagonally across the band it was moved away from.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel c = model.createActivity("C", "C", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        model.createTransition("A", "B");

        Document doc = parse(renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(0);
            c.setX(0);
            c.setY(150);
            b.setX(0);
            b.setY(300);
        }));

        List<double[]> pts = points(findTransitionPolyline(doc, transitionId(model, "A", "B")));
        Assert.assertEquals("the drawn start must sit on the same horizontal run as the first corner",
            pts.get(1)[1], pts.get(0)[1], 0.001);
        assertNoTransitionCrossesAnyLabelBand(doc);
    }

    @Test
    public void aSameColumnSkipIsRoutedAroundTheActivityItSkips() throws Exception {
        // The vertical mirror of the stepless bypass: A, C and B are stacked in one column and A -> B
        // skips over C, which sits there with no edges of its own. A vertical detour cannot clear it —
        // both ends share an x, so the detour's horizontal run has no length. The way round is a gutter.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel c = model.createActivity("C", "C", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        model.createTransition("A", "B");

        Document doc = parse(renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(0);
            c.setX(0);
            c.setY(150);
            b.setX(0);
            b.setY(300);
        }));

        assertNoTransitionCrossesAnyActivity(doc);
    }

    @Test
    public void aClearBranchIsNotDegradedWhenItsSiblingEscalates() throws Exception {
        // S -> N is adjacent, straight and clear. S -> T's final run crosses O, so it escalates; the
        // whole fan escalates with it, which turns the clear straight branch into a channel detour.
        ProcessModel model = createProcess();
        ActivityModel s = model.createActivity("S", "S", null);
        ActivityModel n = model.createActivity("N", "N", null);
        ActivityModel t = model.createActivity("T", "T", null);
        ActivityModel o = model.createActivity("O", "O", null);
        s.setStarter(true);
        model.createTransition("S", "N");
        model.createTransition("S", "T");

        Document doc = parse(renderWithoutLayout(model, () -> {
            s.setX(0);
            s.setY(0);
            n.setX(150);
            n.setY(0);
            t.setX(300);
            t.setY(200);
            o.setX(150);
            o.setY(200);
        }));

        List<double[]> clear = points(findTransitionPolyline(doc, transitionId(model, "S", "N")));
        Assert.assertEquals("S -> N is clear where it is, so escalating its sibling must leave it alone",
            2, clear.size());
    }

    @Test
    public void aGroupLeftUnsizedByItsLayouterStillRenders() throws Exception {
        // SimpleProcessModelLayouter, and CommonBfsModelLayouter on a model with no start activity,
        // leave a group at 0x0. Its box is then a point, which must still land in a band.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        GroupModel g = model.createGroup("G", "G", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        g.createChildActivity("H", "H", null).setStarter(true);
        model.createTransition("A", "G");
        model.createTransition("G", "B");

        String svg = renderWithoutLayout(model, () -> {
            a.setX(0);
            a.setY(100);
            g.setX(150);
            g.setY(100);
            b.setX(400);
            b.setY(100);
        });

        Assert.assertNotNull("a model whose group was never sized must still render", svg);
        Assert.assertTrue("a model whose group was never sized must still render",
            svg.contains("<svg"));
    }

    @Test
    public void aBypassLandsOnItsTargetAlongAHorizontalRun() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel y = model.createActivity("Y", "Y", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("A", "Y");
        model.createTransition("X", "Y");

        Document doc = parse(renderWithoutLayout(model, () -> {
            x.setX(0);
            x.setY(0);
            a.setX(150);
            a.setY(0);
            y.setX(300);
            y.setY(0);
        }));

        List<double[]> pts = points(findTransitionPolyline(doc, transitionId(model, "X", "Y")));
        double[] last = pts.get(pts.size() - 1);
        double[] beforeLast = pts.get(pts.size() - 2);
        Assert.assertEquals("the bypass must enter its target along a horizontal run, the way an elbow "
            + "branch does", beforeLast[1], last[1], 0.001);
    }

    @Test
    public void aBypassAndItsStraightSiblingLeaveTheSourceOnSeparateRuns() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel y = model.createActivity("Y", "Y", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("A", "Y");
        model.createTransition("X", "Y");

        Document doc = parse(renderWithoutLayout(model, () -> {
            x.setX(0);
            x.setY(0);
            a.setX(150);
            a.setY(0);
            y.setX(300);
            y.setY(0);
        }));

        List<double[]> straight = points(findTransitionPolyline(doc, transitionId(model, "X", "A")));
        List<double[]> bypass = points(findTransitionPolyline(doc, transitionId(model, "X", "Y")));
        Assert.assertTrue(
            "X -> A and the X -> Y bypass must not run along the same y out of X, or a server-coloured "
                + "executed transition is hidden behind its sibling (was " + straight.get(0)[1] + " and "
                + bypass.get(0)[1] + ")",
            Math.abs(straight.get(0)[1] - bypass.get(0)[1]) > 1);
    }

    @Test
    public void steplessBypass_isRoutedAsPolylineAvoidingIntermediateNode() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        model.createActivity("Y", "Y", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("A", "Y");
        TransitionModel bypass = model.createTransition("X", "Y");
        bypass.setName("skip");

        Document doc = parse(render(model));

        Element bypassPoly = findTransitionPolyline(doc, transitionId(model, "X", "Y"));
        Assert.assertNotNull("bypass transition must be rendered as a <polyline> with modelId", bypassPoly);
        Assert.assertTrue("bypass marker-end must be set", bypassPoly.hasAttribute("marker-end"));
        Assert.assertTrue("bypass must be routed (more than 2 points), was "
            + bypassPoly.getAttribute("points"), points(bypassPoly).size() > 2);

        assertNoTransitionCrossesAnyActivity(doc);
    }

    @Test
    public void steplessBypass_doesNotRunThroughOrEndInActivityLabel() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        model.createActivity("Y", "Y", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("A", "Y");
        model.createTransition("X", "Y").setName("skip");

        Document doc = parse(render(model));

        // No transition (in particular the bypass) may run through the label strip below any icon.
        assertNoTransitionCrossesAnyLabelBand(doc);
    }

    @Test
    public void steplessBypass_labelDoesNotSitOnIntermediateNode() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        model.createActivity("Y", "Y", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("A", "Y");
        TransitionModel bypass = model.createTransition("X", "Y");
        bypass.setName("skip");

        Document doc = parse(render(model));

        Element label = null;
        NodeList texts = doc.getElementsByTagNameNS(SVG_NS, "text");
        for (int i = 0; i < texts.getLength(); i++) {
            Element t = (Element) texts.item(i);
            if ((transitionId(model, "X", "Y") + "_label").equals(t.getAttribute("id"))) {
                label = t;
                break;
            }
        }
        Assert.assertNotNull("bypass label must be present", label);
        double lx = Double.parseDouble(label.getAttribute("x"));
        double ly = Double.parseDouble(label.getAttribute("y"));
        for (double[] box : activityBoxes(doc)) {
            Assert.assertFalse("bypass label must not sit inside an activity box",
                lx >= box[0] && lx <= box[2] && ly >= box[1] && ly <= box[3]);
        }
    }

    @Test
    public void choiceFanOut_noTransitionCrossesAnyNode() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        model.createActivity("B", "B", null);
        model.createActivity("C", "C", null);
        model.createActivity("Y", "Y", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("X", "B");
        model.createTransition("X", "C");
        model.createTransition("A", "Y");
        model.createTransition("B", "Y");
        model.createTransition("C", "Y");

        Document doc = parse(render(model));

        assertNoTransitionCrossesAnyActivity(doc);
        // every transition is emitted as a polyline with a modelId
        Assert.assertEquals(6, transitionPolylines(doc).size());
    }

    @Test
    public void transitionsAreEmittedAsPolylinesNotLines() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        x.setStarter(true);
        model.createTransition("X", "A");

        Document doc = parse(render(model));

        // no <line> carrying a modelId (those would be the old straight transitions)
        NodeList lines = doc.getElementsByTagNameNS(SVG_NS, "line");
        for (int i = 0; i < lines.getLength(); i++) {
            Assert.assertFalse("transitions must not be emitted as <line>",
                ((Element) lines.item(i)).hasAttribute("modelId"));
        }
        Assert.assertEquals(1, transitionPolylines(doc).size());
    }

    private static List<double[]> transitionLabelAnchors(Document doc, ProcessModel model) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (TransitionModel t : model.getTransitionModels()) {
            ids.add(t.getId() + "_label");
        }
        List<double[]> anchors = new ArrayList<>();
        NodeList texts = doc.getElementsByTagNameNS(SVG_NS, "text");
        for (int i = 0; i < texts.getLength(); i++) {
            Element t = (Element) texts.item(i);
            if (ids.contains(t.getAttribute("id"))) {
                anchors.add(new double[] {
                    Double.parseDouble(t.getAttribute("x")),
                    Double.parseDouble(t.getAttribute("y"))});
            }
        }
        return anchors;
    }

    private static double estLabelWidth(String text) {
        return text.length() * NjamsProcessDiagramFactory.DEFAULT_TEXT_SIZE
            * NjamsProcessDiagramFactory.DEFAULT_CHAR_WIDTH_FACTOR;
    }

    /** Approximate bounding box of a transition label element from its anchor, x/y and text. */
    private static double[] labelBox(Element label) {
        double x = Double.parseDouble(label.getAttribute("x"));
        double y = Double.parseDouble(label.getAttribute("y"));
        double w = estLabelWidth(label.getTextContent());
        String anchor = label.getAttribute("text-anchor");
        double left;
        double right;
        if ("end".equals(anchor)) {
            right = x;
            left = x - w;
        } else if ("start".equals(anchor)) {
            left = x;
            right = x + w;
        } else {
            left = x - w / 2;
            right = x + w / 2;
        }
        return new double[] {left, y - NjamsProcessDiagramFactory.DEFAULT_TEXT_SIZE + 3, right, y + 3};
    }

    private static List<Element> transitionLabels(Document doc, ProcessModel model) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (TransitionModel t : model.getTransitionModels()) {
            ids.add(t.getId() + "_label");
        }
        List<Element> result = new ArrayList<>();
        NodeList texts = doc.getElementsByTagNameNS(SVG_NS, "text");
        for (int i = 0; i < texts.getLength(); i++) {
            Element t = (Element) texts.item(i);
            if (ids.contains(t.getAttribute("id"))) {
                result.add(t);
            }
        }
        return result;
    }

    @Test
    public void fanOut_labelsDoNotOverlapTargetNodes() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        model.createActivity("B", "B", null);
        model.createActivity("C", "C", null);
        x.setStarter(true);
        model.createTransition("X", "A").setName("conditionA");
        model.createTransition("X", "B").setName("conditionB");
        model.createTransition("X", "C").setName("conditionC");

        Document doc = parse(render(model));

        List<double[]> boxes = activityBoxes(doc);
        for (Element label : transitionLabels(doc, model)) {
            for (double[] box : boxes) {
                Assert.assertFalse("label '" + label.getTextContent() + "' overlaps an activity icon",
                    segmentIntersectsRect(new double[] {labelBox(label)[0], (labelBox(label)[1] + labelBox(label)[3]) / 2},
                        new double[] {labelBox(label)[2], (labelBox(label)[1] + labelBox(label)[3]) / 2}, box));
            }
        }
    }

    @Test
    public void fanOut_transitionLabelsDoNotStackOnTopOfEachOther() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        model.createActivity("B", "B", null);
        model.createActivity("C", "C", null);
        x.setStarter(true);
        model.createTransition("X", "A").setName("toA");
        model.createTransition("X", "B").setName("toB");
        model.createTransition("X", "C").setName("toC");

        Document doc = parse(render(model));

        List<double[]> anchors = transitionLabelAnchors(doc, model);
        Assert.assertEquals("expected one label per branch", 3, anchors.size());
        // Each branch ends on its own row, so the three labels must sit at three distinct heights.
        long distinctY = anchors.stream().map(a -> Math.round(a[1])).distinct().count();
        Assert.assertEquals("fan-out labels must not stack on the same row", 3, distinctY);
    }

    /**
     * A horizontal segment of one transition properly crosses a vertical segment of another when the
     * vertical's x lies strictly inside the horizontal's x-range and the horizontal's y lies strictly
     * inside the vertical's y-range. Endpoint touches (shared trunks at a fork, merges at a join) are
     * excluded by the strict comparison.
     */
    private static boolean transitionsCross(Element a, Element b) {
        double eps = 0.01;
        List<double[]> pa = points(a);
        List<double[]> pb = points(b);
        for (int i = 0; i + 1 < pa.size(); i++) {
            for (int j = 0; j + 1 < pb.size(); j++) {
                if (properCross(pa.get(i), pa.get(i + 1), pb.get(j), pb.get(j + 1), eps)
                    || properCross(pb.get(j), pb.get(j + 1), pa.get(i), pa.get(i + 1), eps)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if axis-aligned horizontal segment h0->h1 strictly crosses axis-aligned vertical v0->v1. */
    private static boolean properCross(double[] h0, double[] h1, double[] v0, double[] v1, double eps) {
        boolean horizontal = Math.abs(h0[1] - h1[1]) < eps;
        boolean vertical = Math.abs(v0[0] - v1[0]) < eps;
        if (!horizontal || !vertical) {
            return false;
        }
        double hy = h0[1];
        double vx = v0[0];
        double hxMin = Math.min(h0[0], h1[0]);
        double hxMax = Math.max(h0[0], h1[0]);
        double vyMin = Math.min(v0[1], v1[1]);
        double vyMax = Math.max(v0[1], v1[1]);
        return vx > hxMin + eps && vx < hxMax - eps && hy > vyMin + eps && hy < vyMax - eps;
    }

    private static void assertNoTransitionsCrossEachOther(Document doc) {
        List<Element> polys = transitionPolylines(doc);
        for (int i = 0; i < polys.size(); i++) {
            for (int k = i + 1; k < polys.size(); k++) {
                Assert.assertFalse(
                    "transitions '" + polys.get(i).getAttribute("modelId") + "' and '"
                        + polys.get(k).getAttribute("modelId") + "' cross each other",
                    transitionsCross(polys.get(i), polys.get(k)));
            }
        }
    }

    @Test
    public void fanOut_branchesDoNotCrossEachOther() throws Exception {
        // X forks to three targets on three rows. The branch to the farthest target must take the
        // gutter nearest the source so the nearer branches nest inside it without being crossed.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        model.createActivity("B", "B", null);
        model.createActivity("C", "C", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("X", "B");
        model.createTransition("X", "C");

        Document doc = parse(render(model));

        assertNoTransitionsCrossEachOther(doc);
    }

    @Test
    public void convergence_branchesDoNotCrossEachOther() throws Exception {
        // Three branches reconverge on Y; the fan-in elbows must nest so no branch crosses another.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        model.createActivity("B", "B", null);
        model.createActivity("C", "C", null);
        model.createActivity("Y", "Y", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("X", "B");
        model.createTransition("X", "C");
        model.createTransition("A", "Y");
        model.createTransition("B", "Y");
        model.createTransition("C", "Y");

        Document doc = parse(render(model));

        assertNoTransitionsCrossEachOther(doc);
    }

    @Test
    public void longJoin_doesNotBendUpEarlyAcrossIntermediateNodesOrPath() throws Exception {
        // Main path A -> X -> Y -> B on one row; a lower branch A -> Z rejoins the path at B.
        // The Z -> B join spans several columns: it must run along Z's (lower) row until just before
        // B and only then bend up, so it neither crosses Y nor overlaps the X->Y / Y->B segments.
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        model.createActivity("X", "X", null);
        model.createActivity("Y", "Y", null);
        model.createActivity("B", "B", null);
        model.createActivity("Z", "Z", null);
        a.setStarter(true);
        model.createTransition("A", "X");
        model.createTransition("X", "Y");
        model.createTransition("Y", "B");
        model.createTransition("A", "Z");
        model.createTransition("Z", "B");

        Document doc = parse(render(model));

        assertNoTransitionCrossesAnyActivity(doc);
        assertNoTransitionsCrossEachOther(doc);
    }

    @Test
    public void fanOut_elbowExitYsAtSourceAreStaggeredToPreventColorOcclusion() throws Exception {
        // When nJAMS Server colours an executed transition green, sibling branches that share the same
        // exit horizontal segment occlude it. Fan-out elbows must leave the source at distinct y-coordinates
        // so each segment is individually visible regardless of draw order.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        model.createActivity("B", "B", null);
        model.createActivity("C", "C", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("X", "B");
        model.createTransition("X", "C");

        Document doc = parse(render(model));

        // Elbow transitions have more than 2 waypoints; their first waypoint y is the exit y at source.
        List<Double> exitYs = new ArrayList<>();
        for (Element poly : transitionPolylines(doc)) {
            List<double[]> pts = points(poly);
            if (pts.size() > 2) {
                exitYs.add(pts.get(0)[1]);
            }
        }
        Assert.assertTrue("fan-out must produce at least two elbow transitions", exitYs.size() >= 2);
        long distinct = exitYs.stream().mapToLong(y -> Math.round(y)).distinct().count();
        Assert.assertEquals("fan-out elbow transitions must exit the source at distinct y-coordinates",
            exitYs.size(), distinct);
    }

    @Test
    public void fanIn_elbowEntryYsAtTargetAreStaggeredToPreventColorOcclusion() throws Exception {
        // Mirror of the fan-out case: fan-in elbows must arrive at the target at distinct y-coordinates.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        model.createActivity("B", "B", null);
        model.createActivity("C", "C", null);
        model.createActivity("Y", "Y", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("X", "B");
        model.createTransition("X", "C");
        model.createTransition("A", "Y");
        model.createTransition("B", "Y");
        model.createTransition("C", "Y");

        Document doc = parse(render(model));

        // Collect last-waypoint y for all elbow transitions ending at Y (those with >2 waypoints).
        List<Double> entryYs = new ArrayList<>();
        for (TransitionModel t : model.getTransitionModels()) {
            if (t.getToActivity() == null || !"Y".equals(t.getToActivity().getId())) {
                continue;
            }
            Element poly = findTransitionPolyline(doc,
                transitionId(model, t.getFromActivity().getId(), "Y"));
            if (poly != null) {
                List<double[]> pts = points(poly);
                if (pts.size() > 2) {
                    entryYs.add(pts.get(pts.size() - 1)[1]);
                }
            }
        }
        Assert.assertTrue("fan-in must produce at least two elbow transitions", entryYs.size() >= 2);
        long distinct = entryYs.stream().mapToLong(y -> Math.round(y)).distinct().count();
        Assert.assertEquals("fan-in elbow transitions must enter the target at distinct y-coordinates",
            entryYs.size(), distinct);
    }

    @Test
    public void fanOut_directLabelDoesNotOverlapSiblingElbowLines() throws Exception {
        // X fans out to A (same row, so X::A classifies as STRAIGHT), B and C (ELBOW, staggered below
        // X's centre per assignLanes). X::A's wrapped label used to grow straight down from just below
        // its own line -- directly into the space where B's and C's staggered exit runs live.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        ActivityModel c = model.createActivity("C", "C", null);
        x.setStarter(true);
        model.createTransition("X", "A").setName("Order value exceeds the automatic approval threshold");
        model.createTransition("X", "B").setName("otherwise");
        model.createTransition("X", "C").setName("cancelled by customer");
        x.setX(0);
        x.setY(0);
        a.setX(150);
        a.setY(0);
        b.setX(150);
        b.setY(100);
        c.setX(150);
        c.setY(200);

        Njams njams = model.getNjams();
        Document doc = parse(new PolylineProcessDiagramFactory(njams).getProcessDiagram(model));

        assertLabelDoesNotOverlapAnyOtherTransition(doc, transitionId(model, "X", "A"));
    }

    @Test
    public void fanIn_directLabelDoesNotOverlapSiblingElbowLines() throws Exception {
        // A and B fan in to Y; A::Y is same row (STRAIGHT), B::Y is ELBOW (target fan-in, staggered
        // below Y's centre per assignLanes). A::Y's wrapped label used to grow straight down into the
        // space where B::Y's staggered entry run lives.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        ActivityModel y = model.createActivity("Y", "Y", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("X", "B");
        model.createTransition("A", "Y").setName("Approved after manual review by a supervisor");
        model.createTransition("B", "Y").setName("otherwise");
        x.setX(0);
        x.setY(0);
        a.setX(150);
        a.setY(0);
        b.setX(150);
        b.setY(100);
        y.setX(300);
        y.setY(0);

        Njams njams = model.getNjams();
        Document doc = parse(new PolylineProcessDiagramFactory(njams).getProcessDiagram(model));

        assertLabelDoesNotOverlapAnyOtherTransition(doc, transitionId(model, "A", "Y"));
    }

    @Test
    public void straightTransition_shortLabelSitsCloseAboveItsLine() throws Exception {
        // Single-line label on a same-row STRAIGHT edge: it must hug the line closely from above (a
        // small gap, the same closeness convention LABEL_GAP uses elsewhere for the icon-to-label gap),
        // not float a full text-line's height away from it.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        ActivityModel a = model.createActivity("A", "A", null);
        x.setStarter(true);
        model.createTransition("X", "A").setName("OK");
        x.setX(0);
        x.setY(0);
        a.setX(150);
        a.setY(0);

        Njams njams = model.getNjams();
        Document doc = parse(new PolylineProcessDiagramFactory(njams).getProcessDiagram(model));
        String tid = transitionId(model, "X", "A");

        Element poly = findTransitionPolyline(doc, tid);
        Assert.assertNotNull("transition must be rendered as a polyline", poly);
        double lineY = points(poly).get(0)[1];
        Assert.assertNull("label should fit on a single line", findText(doc, tid + "_label_2"));
        Element label = findText(doc, tid + "_label");
        Assert.assertNotNull("label must be present", label);
        double labelY = Double.parseDouble(label.getAttribute("y"));

        Assert.assertTrue("single-line label must sit above its line (labelY=" + labelY + ", lineY=" + lineY + ")",
            labelY < lineY);
        double gap = lineY - labelY;
        Assert.assertTrue("label must hug the line closely (gap=" + gap + "px), not float a full text-line "
                + "height away from it",
            gap <= NjamsProcessDiagramFactory.DEFAULT_TEXT_SIZE / 2.0);
    }

    /** Asserts neither label line (if present) of the given transition overlaps any OTHER transition's line. */
    private static void assertLabelDoesNotOverlapAnyOtherTransition(Document doc, String tid) {
        for (String suffix : new String[] {"_label", "_label_2"}) {
            Element label = findText(doc, tid + suffix);
            if (label == null) {
                continue;
            }
            double[] lb = labelBox(label);
            for (Element poly : transitionPolylines(doc)) {
                if (tid.equals(poly.getAttribute("modelId"))) {
                    continue;
                }
                List<double[]> pts = points(poly);
                for (int i = 0; i + 1 < pts.size(); i++) {
                    Assert.assertFalse(
                        "label line '" + label.getTextContent() + "' (" + tid + suffix
                            + ") overlaps sibling transition '" + poly.getAttribute("modelId") + "'",
                        segmentIntersectsRect(pts.get(i), pts.get(i + 1), lb));
                }
            }
        }
    }

    private static Element findText(Document doc, String id) {
        NodeList texts = doc.getElementsByTagNameNS(SVG_NS, "text");
        for (int i = 0; i < texts.getLength(); i++) {
            Element t = (Element) texts.item(i);
            if (id.equals(t.getAttribute("id"))) {
                return t;
            }
        }
        return null;
    }

    @Test
    public void transition_truncatedLabelCarriesFullTooltipOnPolyline() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        x.setStarter(true);
        String longName = "This Is A Very Long Transition Condition Label That Will Not Fit";
        model.createTransition("X", "A").setName(longName);

        Document doc = parse(render(model));
        String tid = transitionId(model, "X", "A");

        Element poly = findTransitionPolyline(doc, tid);
        Assert.assertNotNull("transition must be rendered as a polyline", poly);
        Assert.assertEquals("tooltip must sit on the polyline and hold the full label",
            longName, poly.getAttribute("nj-sdk-tooltip"));
        Assert.assertNotNull("truncated label must still wrap to a second line", findText(doc, tid + "_label_2"));
        Assert.assertFalse("first label line must not carry the tooltip",
            findText(doc, tid + "_label").hasAttribute("nj-sdk-tooltip"));
        Assert.assertFalse("second label line must not carry the tooltip",
            findText(doc, tid + "_label_2").hasAttribute("nj-sdk-tooltip"));
    }

    @Test
    public void diagonalStraightTransition_labelWidthUsesActualHorizontalSpan() throws Exception {
        // Positioned manually (bypassing the auto layouter) so X->A classifies as a "STRAIGHT" edge
        // whose target column lies left of its source column (colT < colS) with a different row --
        // a genuinely diagonal edge with ~500px of actual horizontal room between the two activities.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        ActivityModel a = model.createActivity("A", "A", null);
        x.setStarter(true);
        String name = "This Is A Long Transition Condition";
        model.createTransition("X", "A").setName(name);
        x.setX(500);
        x.setY(0);
        a.setX(0);
        a.setY(300);

        Njams njams = model.getNjams();
        Document doc = parse(new PolylineProcessDiagramFactory(njams).getProcessDiagram(model));
        String tid = transitionId(model, "X", "A");

        Element poly = findTransitionPolyline(doc, tid);
        Assert.assertNotNull("transition must be rendered as a polyline", poly);
        Assert.assertFalse(
            "label has ~500px of actual horizontal room and must not be truncated as if only "
                + "the fixed default activity width were available",
            poly.hasAttribute("nj-sdk-tooltip"));
        Assert.assertNull("label should fit on a single line given the actual available width",
            findText(doc, tid + "_label_2"));
        Assert.assertEquals(name, findText(doc, tid + "_label").getTextContent());
    }

    @Test
    public void verticalStraightTransition_labelFallsBackToColumnSpacingNotActivitySize() throws Exception {
        // X and A share the same column (scx == tcx), so the edge has no actual horizontal extent at
        // all: the fallback must assume the same clear gap as the default column-to-column spacing
        // (minus the icon width on either side), not the far narrower activity icon size outright.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        ActivityModel a = model.createActivity("A", "A", null);
        x.setStarter(true);
        String name = "Continue Now";
        model.createTransition("X", "A").setName(name);
        x.setX(200);
        x.setY(0);
        a.setX(200);
        a.setY(300);

        Njams njams = model.getNjams();
        Document doc = parse(new PolylineProcessDiagramFactory(njams).getProcessDiagram(model));
        String tid = transitionId(model, "X", "A");

        Element poly = findTransitionPolyline(doc, tid);
        Assert.assertNotNull("transition must be rendered as a polyline", poly);
        Assert.assertFalse(
            "a purely vertical transition must fall back to the default column spacing, not the "
                + "much narrower activity icon size, so a normal-length label must not be truncated",
            poly.hasAttribute("nj-sdk-tooltip"));
        Assert.assertNull("label should fit on a single line given the column-spacing fallback width",
            findText(doc, tid + "_label_2"));
        Assert.assertEquals(name, findText(doc, tid + "_label").getTextContent());
    }

    /** True if two axis-aligned boxes ([left, top, right, bottom]) overlap by more than a hairline. */
    private static boolean boxesOverlap(double[] a, double[] b) {
        double pad = 0.5;
        return a[0] < b[2] - pad && a[2] > b[0] + pad && a[1] < b[3] - pad && a[3] > b[1] + pad;
    }

    /** Asserts neither label line (if present) of the given transition overlaps any activity icon. */
    private static void assertLabelDoesNotOverlapAnyActivity(Document doc, String tid) {
        List<double[]> boxes = activityBoxes(doc);
        for (String suffix : new String[] {"_label", "_label_2"}) {
            Element label = findText(doc, tid + suffix);
            if (label == null) {
                continue;
            }
            double[] lb = labelBox(label);
            for (double[] box : boxes) {
                Assert.assertFalse(
                    "label line '" + label.getTextContent() + "' (" + tid + suffix + ") overlaps an activity icon",
                    boxesOverlap(lb, box));
            }
        }
    }

    @Test
    public void straightTransition_longLabelDoesNotOverlapAdjacentActivities() throws Exception {
        // Same-row STRAIGHT edge (colT - colS < 2): the actual span used for wrapping is the distance
        // between activity CENTRES, which is 50px (DEFAULT_ACTIVITY_SIZE) wider on each side than the
        // real clear gap between the two icons' facing edges.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        ActivityModel a = model.createActivity("A", "A", null);
        x.setStarter(true);
        String name = "This condition determines whether the customer qualifies for automatic approval";
        model.createTransition("X", "A").setName(name);
        x.setX(0);
        x.setY(0);
        a.setX(150);
        a.setY(0);

        Njams njams = model.getNjams();
        Document doc = parse(new PolylineProcessDiagramFactory(njams).getProcessDiagram(model));
        String tid = transitionId(model, "X", "A");

        assertLabelDoesNotOverlapAnyActivity(doc, tid);
    }

    @Test
    public void verticalStraightTransition_wrappedLabelDoesNotOverlapLowerActivity() throws Exception {
        // Purely vertical STRAIGHT edge: a wrapped (2-line) label must stay within the vertical gap
        // between the two activities rather than growing straight down from a single-line offset.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        ActivityModel a = model.createActivity("A", "A", null);
        x.setStarter(true);
        String name = "This step requires manual review before proceeding further";
        model.createTransition("X", "A").setName(name);
        x.setX(0);
        x.setY(0);
        a.setX(0);
        a.setY(100);

        Njams njams = model.getNjams();
        Document doc = parse(new PolylineProcessDiagramFactory(njams).getProcessDiagram(model));
        String tid = transitionId(model, "X", "A");

        assertLabelDoesNotOverlapAnyActivity(doc, tid);
    }

    @Test
    public void verticalStraightTransition_canvasAccommodatesFallbackLabelWidth() throws Exception {
        // The canvas is currently sized purely from activity positions (icon size + margin), with no
        // regard for how wide a transition's own label is allowed to be. For an isolated vertical chain
        // the canvas is only as wide as a single icon (~70px), while the label's own fallback width
        // (DEFAULT_COLUMN_SPACING(150) - DEFAULT_ACTIVITY_SIZE(50) = 100px) already exceeds that -- an
        // internal inconsistency that lets real (wider-than-estimated) glyph rendering get clipped by
        // the SVG viewport, independent of how accurate the estimate itself is.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        ActivityModel a = model.createActivity("A", "A", null);
        x.setStarter(true);
        model.createTransition("X", "A").setName("Continue Processing");
        x.setX(0);
        x.setY(0);
        a.setX(0);
        a.setY(100);

        Njams njams = model.getNjams();
        Document doc = parse(new PolylineProcessDiagramFactory(njams).getProcessDiagram(model));

        double canvasWidth = Double.parseDouble(doc.getDocumentElement().getAttribute("width"));
        Assert.assertTrue(
            "canvas width (" + canvasWidth + ") must be at least the label's assumed available width "
                + "(100px), otherwise the label can render wider than the canvas and get clipped",
            canvasWidth >= 100);
    }

    @Test
    public void parallelBranches_shareGutterButGetDistinctLanes() throws Exception {
        // X -> A (row 0) and X -> B (row 1): both leave column 0; their vertical runs must differ in x.
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        model.createActivity("A", "A", null);
        model.createActivity("B", "B", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("X", "B");

        Document doc = parse(render(model));
        assertNoTransitionCrossesAnyActivity(doc);
    }

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

        Element bypassPolyline = findTransitionPolyline(doc, transitionId(model, "A", "B"));
        Assert.assertNotNull("bypass A->B must be routed as a polyline, not fall back to a straight line",
            bypassPolyline);
        Assert.assertEquals("a routed BYPASS transition should have 6 waypoints", 6,
            points(bypassPolyline).size());
        assertNoTransitionCrossesAnyGroupBox(doc);
    }

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
            Math.abs(bypassApproachX - elbowBGutterX) > 7.0);
        Assert.assertTrue("bypass approach corridor must not coincide with elbow C's gutter",
            Math.abs(bypassApproachX - elbowCGutterX) > 7.0);
        Assert.assertTrue("fan-in elbows sharing a gutter must still use distinct lanes",
            Math.abs(elbowBGutterX - elbowCGutterX) > 7.0);
    }

    @Test
    public void fanInElbowExit_doesNotCrossLaterActivityInSameRow() throws Exception {
        // Real topology reported against SDK-479 (Camel "producer-order-intake" route):
        // validate-items-non-empty forks into a short branch (log-rejected-empty-items, joining far to
        // the right at set-response-content-type, a high fan-in target) and a longer branch that passes
        // through a second Choice (choose-confirm-or-reject). The extra hop bumps choose-confirm-or-reject
        // onto the SAME row as log-rejected-empty-items, landing between it and the join column. The
        // fan-in elbow's exit leg runs unstaggered along its own row (see assignElbowGutter) all the way
        // to the join's gutter, with no obstacle awareness — so it cuts straight through
        // choose-confirm-or-reject's icon and its own outgoing edge.
        ProcessModel model = createProcess();
        ActivityModel start = model.createActivity("From:platform-http_1", "From:platform-http_1", null);
        model.createActivity("unmarshal-incoming-order", "unmarshal-incoming-order", null);
        model.createActivity("producer-extract-order-id", "producer-extract-order-id", null);
        model.createActivity("extract-callback-url", "extract-callback-url", null);
        model.createActivity("save-parsed-order", "save-parsed-order", null);
        model.createActivity("set-default-duplicate-response", "set-default-duplicate-response", null);
        model.createActivity("dedupe-incoming-order", "dedupe-incoming-order", null);
        model.createActivity("restore-parsed-order", "restore-parsed-order", null);
        model.createActivity("validate-structure", "validate-structure", null);
        model.createActivity("set-rejection-missing-items", "set-rejection-missing-items", null);
        model.createActivity("log-rejected-missing-items", "log-rejected-missing-items", null);
        model.createActivity("validate-items-non-empty", "validate-items-non-empty", null);
        model.createActivity("set-rejection-empty-items", "set-rejection-empty-items", null);
        model.createActivity("log-rejected-empty-items", "log-rejected-empty-items", null);
        model.createActivity("enrich-inventory-check", "enrich-inventory-check", null);
        model.createActivity("roll-injected-error", "roll-injected-error", null);
        model.createActivity("choose-injected-error", "choose-injected-error", null);
        model.createActivity("log-injected-error", "log-injected-error", null);
        model.createActivity("throw-injected-error", "throw-injected-error", null);
        model.createActivity("choose-confirm-or-reject", "choose-confirm-or-reject", null);
        model.createActivity("set-rejection-out-of-stock", "set-rejection-out-of-stock", null);
        model.createActivity("log-rejected-out-of-stock", "log-rejected-out-of-stock", null);
        model.createActivity("log-order-confirmed", "log-order-confirmed", null);
        model.createActivity("save-confirmation-response", "save-confirmation-response", null);
        model.createActivity("set-body-for-async-handoff", "set-body-for-async-handoff", null);
        model.createActivity("handoff-to-produce-queue", "handoff-to-produce-queue", null);
        model.createActivity("restore-confirmation-response", "restore-confirmation-response", null);
        model.createActivity("set-response-content-type", "set-response-content-type", null);
        start.setStarter(true);

        model.createTransition("From:platform-http_1", "unmarshal-incoming-order");
        model.createTransition("unmarshal-incoming-order", "producer-extract-order-id");
        model.createTransition("producer-extract-order-id", "extract-callback-url");
        model.createTransition("extract-callback-url", "save-parsed-order");
        model.createTransition("save-parsed-order", "set-default-duplicate-response");
        model.createTransition("set-default-duplicate-response", "dedupe-incoming-order");
        model.createTransition("restore-parsed-order", "validate-structure");
        model.createTransition("set-rejection-missing-items", "log-rejected-missing-items");
        model.createTransition("validate-structure", "set-rejection-missing-items").setName("${body[items]} == null");
        model.createTransition("set-rejection-empty-items", "log-rejected-empty-items");
        model.createTransition("validate-items-non-empty", "set-rejection-empty-items")
            .setName("${body[items].size()} == 0");
        model.createTransition("enrich-inventory-check", "roll-injected-error");
        model.createTransition("roll-injected-error", "choose-injected-error");
        model.createTransition("log-injected-error", "throw-injected-error");
        model.createTransition("choose-injected-error", "log-injected-error")
            .setName("${header.injectedErrorRoll} < {{...}}");
        model.createTransition("set-rejection-out-of-stock", "log-rejected-out-of-stock");
        model.createTransition("choose-confirm-or-reject", "set-rejection-out-of-stock")
            .setName("${variable.inventoryCheck[inStock]} == false");
        model.createTransition("log-order-confirmed", "save-confirmation-response");
        model.createTransition("save-confirmation-response", "set-body-for-async-handoff");
        model.createTransition("set-body-for-async-handoff", "handoff-to-produce-queue");
        model.createTransition("handoff-to-produce-queue", "restore-confirmation-response");
        model.createTransition("choose-confirm-or-reject", "log-order-confirmed").setName("otherwise");
        model.createTransition("choose-injected-error", "choose-confirm-or-reject").setName("otherwise");
        model.createTransition("validate-items-non-empty", "enrich-inventory-check").setName("otherwise");
        model.createTransition("validate-structure", "validate-items-non-empty").setName("otherwise");
        model.createTransition("dedupe-incoming-order", "restore-parsed-order");
        model.createTransition("dedupe-incoming-order", "set-response-content-type");
        model.createTransition("log-rejected-missing-items", "set-response-content-type");
        model.createTransition("log-rejected-empty-items", "set-response-content-type");
        model.createTransition("throw-injected-error", "set-response-content-type");
        model.createTransition("log-rejected-out-of-stock", "set-response-content-type");
        model.createTransition("restore-confirmation-response", "set-response-content-type");

        Document doc = parse(render(model));

        assertNoTransitionCrossesAnyActivity(doc);
    }
}
