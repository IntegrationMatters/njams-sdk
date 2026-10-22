package com.im.njams.sdk.model.svg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.layout.CommonBfsModelLayouter;
import com.im.njams.sdk.settings.ClientSettings;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Renders one diagram per behaviour SDK-493 changed, through both factories, and writes each one to
 * {@code target/polyline-samples} so the result can be looked at. The assertions here only confirm that a
 * scenario renders at all; whether the routing <em>reads</em> well is a question for a pair of eyes, which is
 * what the files are for.
 */
public class PolylineDiagramSampleRenderingTest {

    private static final Path OUTPUT_DIRECTORY = Paths.get("target", "polyline-samples");

    @BeforeClass
    public static void createOutputDirectory() throws IOException {
        Files.createDirectories(OUTPUT_DIRECTORY);
    }

    @AfterClass
    public static void reportOutputDirectory() {
        System.out.println("Poly-line diagram samples written to " + OUTPUT_DIRECTORY.toAbsolutePath());
    }

    @Test
    public void bypassOverAnActivityOffsetVertically() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel x = model.createActivity("X", "X", null);
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel y = model.createActivity("Y", "Y", null);
        x.setStarter(true);
        model.createTransition("X", "A");
        model.createTransition("A", "Y");
        model.createTransition("X", "Y").setName("skip");

        x.setX(0);
        x.setY(0);
        a.setX(150);
        a.setY(0);
        y.setX(300);
        y.setY(7);

        write("bypass-offset", model);
    }

    @Test
    public void bypassOverAGroup() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        GroupModel g = model.createGroup("G", "G", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        ActivityModel h = g.createChildActivity("H", "H", null);
        h.setStarter(true);
        model.createTransition("A", "G");
        model.createTransition("G", "B");
        model.createTransition("A", "B").setName("skip");

        placeGroupScenario(a, g, h, b);

        write("bypass-over-group", model);
    }

    @Test
    public void edgesIntoAndOutOfAGroup() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        GroupModel g = model.createGroup("G", "G", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        ActivityModel h = g.createChildActivity("H", "H", null);
        h.setStarter(true);
        model.createTransition("A", "G").setName("into");
        model.createTransition("G", "B").setName("out of");

        placeGroupScenario(a, g, h, b);

        write("group-endpoints", model);
    }

    @Test
    public void verticalApproachDownwards() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        model.createTransition("A", "B").setName("down");

        a.setX(0);
        a.setY(0);
        b.setX(60);
        b.setY(400);

        write("vertical-approach-down", model);
    }

    @Test
    public void verticalApproachUpwards() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel a = model.createActivity("A", "A", null);
        ActivityModel b = model.createActivity("B", "B", null);
        a.setStarter(true);
        model.createTransition("A", "B").setName("up");

        a.setX(0);
        a.setY(400);
        b.setX(0);
        b.setY(0);

        write("vertical-approach-up", model);
    }

    @Test
    public void backwardEdgeLoopingBack() throws Exception {
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

        a.setX(0);
        a.setY(100);
        b.setX(150);
        b.setY(100);
        c.setX(300);
        c.setY(100);
        d.setX(450);
        d.setY(100);

        write("backward-edge", model);
    }

    @Test
    public void fanOutAndFanIn() throws Exception {
        ProcessModel model = createProcess();
        ActivityModel s = model.createActivity("S", "S", null);
        model.createActivity("P", "P", null);
        model.createActivity("Q", "Q", null);
        model.createActivity("R", "R", null);
        model.createActivity("T", "T", null);
        s.setStarter(true);
        model.createTransition("S", "P");
        model.createTransition("S", "Q");
        model.createTransition("S", "R");
        model.createTransition("P", "T");
        model.createTransition("Q", "T");
        model.createTransition("R", "T");

        new CommonBfsModelLayouter().layout(model);

        write("fan-out-fan-in", model);
    }

    /** A group wide enough to hold its child, with a plain activity on either side of it. */
    private static void placeGroupScenario(ActivityModel a, GroupModel g, ActivityModel h, ActivityModel b) {
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
    }

    private ProcessModel createProcess() {
        ClientSettings settings = TestSender.getSettings();
        Njams njams = new Njams(com.im.njams.sdk.Path.of("TEST"), "1.0", "TEST", settings);
        return njams.model().create("proc");
    }

    /** Writes the scenario through both factories, so the two renderings can be compared side by side. */
    private void write(String scenario, ProcessModel model) throws IOException {
        Njams njams = model.getNjams();
        writeFile(scenario + "-default", new NjamsProcessDiagramFactory(njams).getProcessDiagram(model));
        writeFile(scenario + "-polyline", new PolylineProcessDiagramFactory(njams).getProcessDiagram(model));
    }

    private void writeFile(String name, String svg) throws IOException {
        Assert.assertNotNull(name + " must render", svg);
        Assert.assertTrue(name + " must be an svg document", svg.contains("<svg"));
        Files.write(OUTPUT_DIRECTORY.resolve(name + ".svg"), svg.getBytes(StandardCharsets.UTF_8));
    }
}
