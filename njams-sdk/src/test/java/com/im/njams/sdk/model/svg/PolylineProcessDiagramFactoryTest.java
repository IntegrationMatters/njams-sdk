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
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.TransitionModel;
import com.im.njams.sdk.model.layout.CommonBfsModelLayouter;
import com.im.njams.sdk.settings.Settings;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class PolylineProcessDiagramFactoryTest {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    private ProcessModel createProcess() {
        Settings settings = TestSender.getSettings();
        Njams njams = new Njams(Path.of("TEST"), "1.0", "TEST", settings);
        return njams.model().create("proc");
    }

    private String render(ProcessModel model) {
        new CommonBfsModelLayouter().layout(model);
        Njams njams = model.getNjams();
        return new PolylineProcessDiagramFactory(njams).getProcessDiagram(model);
    }

    private static Document parse(String svg) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder()
            .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
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

    /** Label bands: the strip directly below each activity icon where its label text is drawn. */
    private static List<double[]> labelBands(Document doc) {
        List<double[]> bands = new ArrayList<>();
        for (double[] box : activityBoxes(doc)) {
            // box = [left, top, right, bottom]; label sits below the icon, ~22px tall.
            bands.add(new double[] {box[0], box[3], box[2], box[3] + 22});
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
}
