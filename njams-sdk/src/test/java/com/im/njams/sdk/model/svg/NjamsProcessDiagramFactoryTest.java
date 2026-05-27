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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;

import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.TransitionModel;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.im.njams.sdk.Path;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;

public class NjamsProcessDiagramFactoryTest {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    @Test
    public void testSecureProcessingEnabled() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);

        Assert.assertFalse(factory.disableSecureProcessing);
    }

    @Test
    public void testSecureProcessingDisabled() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(true);

        Assert.assertTrue(factory.disableSecureProcessing);
    }

    @Test
    public void testWithXsltString() throws Exception {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        String result = factory.withXslt(getMarkingXslt()).serializeDocument(createSimpleContext());
        Assert.assertTrue(result.contains("data-postprocessed=\"yes\""));
    }

    @Test
    public void testWithXsltSource() throws Exception {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        String result = factory.withXslt(new StreamSource(new java.io.StringReader(getMarkingXslt())))
            .serializeDocument(createSimpleContext());
        Assert.assertTrue(result.contains("data-postprocessed=\"yes\""));
    }

    @Test
    public void testWithXsltInputStream() throws Exception {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        ByteArrayInputStream in = new ByteArrayInputStream(getMarkingXslt().getBytes(StandardCharsets.UTF_8));
        String result = factory.withXslt(in).serializeDocument(createSimpleContext());
        Assert.assertTrue(result.contains("data-postprocessed=\"yes\""));
    }

    @Test
    public void testDrawTransitionWithoutNameProducesNoTextLabel() throws Exception {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramContext context = createSimpleContext();
        context.setContainerElement(context.getDoc().getDocumentElement());

        ActivityModel from = new ActivityModel(null, "a", "Activity A", "step");
        ActivityModel to = new ActivityModel(null, "b", "Activity B", "step");

        TransitionModel transition = new TransitionModel(null, "a_b"); // no name constructor
        transition.setFromActivity(from);
        transition.setToActivity(to);

        // non-provided name in transition
        factory.drawTransition(context, transition);

        final String svg = factory.serializeDocument(context);
        Assert.assertFalse(svg.contains("a_b_label"));
    }

    private static NjamsProcessDiagramContext createSimpleContext() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc = dbf.newDocumentBuilder().newDocument();
        doc.appendChild(doc.createElementNS("http://www.w3.org/2000/svg", "svg"));
        NjamsProcessDiagramContext context = new NjamsProcessDiagramContext();
        context.setDoc(doc);
        context.setSvgNS("http://www.w3.org/2000/svg");
        return context;
    }

    private static NjamsProcessDiagramContext createDrawableContext(String category) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc = dbf.newDocumentBuilder().newDocument();
        Element svgRoot = doc.createElementNS(SVG_NS, "svg");
        doc.appendChild(svgRoot);
        NjamsProcessDiagramContext context = new NjamsProcessDiagramContext();
        context.setDoc(doc);
        context.setSvgNS(SVG_NS);
        context.setContainerElement(svgRoot);
        context.setCategory(category);
        return context;
    }

    private static GroupModel buildGroup(String id, String name, String type, int x, int y, int width, int height) {
        ProcessModel pm = new ProcessModel(Path.of("PROCESSES"), null);
        GroupModel group = new GroupModel(pm, id, name, type);
        group.setX(x);
        group.setY(y);
        group.setWidth(width);
        group.setHeight(height);
        return group;
    }

    private static Element findChildElementWithAttribute(Element parent, String localTagName, String attrName) {
        NodeList list = parent.getElementsByTagNameNS(SVG_NS, localTagName);
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            if (e.hasAttributeNS(null, attrName)) {
                return e;
            }
        }
        return null;
    }

    @Test
    public void drawGroup_labelIsLeftBoundToIconInsideHeader() throws Exception {
        NjamsProcessDiagramContext context = createDrawableContext("cat");
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(true);
        int groupX = 100;
        int groupY = 50;
        int headerHeight = 20;
        int iconSize = 16;

        GroupModel group = buildGroup("g1", "MyGroup", "loop", groupX, groupY, 200, 150);
        factory.drawGroup(context, group);

        Element label = null;
        NodeList texts = context.getDoc().getElementsByTagNameNS(SVG_NS, "text");
        for (int i = 0; i < texts.getLength(); i++) {
            Element t = (Element) texts.item(i);
            if ("g1_label".equals(t.getAttributeNS(null, "id"))) {
                label = t;
                break;
            }
        }
        Assert.assertNotNull("Expected label text element with id 'g1_label'", label);

        double labelY = Double.parseDouble(label.getAttributeNS(null, "y"));
        Assert.assertTrue("Group label y (" + labelY + ") must be inside the header [" + groupY + ", "
            + (groupY + headerHeight) + "], but was outside.", labelY >= groupY && labelY <= groupY + headerHeight);

        double labelX = Double.parseDouble(label.getAttributeNS(null, "x"));
        Assert.assertTrue("Group label x (" + labelX + ") must sit to the right of the icon (>= "
            + (groupX + iconSize) + ") with a small gap.", labelX > groupX + iconSize);
        Assert.assertNotEquals("Group label must not be center-anchored; it must be left-bound to the icon.",
            "middle", label.getAttributeNS(null, "text-anchor"));
    }

    @Test
    public void drawGroup_iconUsesTypeBasedAttribute_andDoesNotHardcodeHref() throws Exception {
        NjamsProcessDiagramContext context = createDrawableContext("cat");
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(true);

        GroupModel group = buildGroup("g1", "MyGroup", "loop", 0, 0, 100, 100);
        factory.drawGroup(context, group);

        NodeList groups = context.getDoc().getElementsByTagNameNS(SVG_NS, "g");
        Assert.assertTrue("Expected a <g> element to be created for the group", groups.getLength() > 0);
        Element groupContainer = (Element) groups.item(0);

        Element icon = findChildElementWithAttribute(groupContainer, "image", "group-type");
        Assert.assertNotNull(
            "Expected the group icon image to carry a 'group-type' attribute analogous to activity-type", icon);
        Assert.assertEquals("cat.loop", icon.getAttributeNS(null, "group-type"));
        Assert.assertEquals("Group icon must also carry 'activity-type' with the same value as 'group-type'",
            "cat.loop", icon.getAttributeNS(null, "activity-type"));
        Assert.assertFalse("Group icon must not hardcode an xlink:href",
            icon.hasAttributeNS(XLINK_NS, "href"));
    }

    private static String getMarkingXslt() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
            + "xmlns:svg=\"http://www.w3.org/2000/svg\">"
            + "<xsl:output method=\"xml\" indent=\"yes\" encoding=\"UTF-8\"/>"
            + "<xsl:template match=\"@*|node()\">"
            + "<xsl:copy><xsl:apply-templates select=\"@*|node()\"/></xsl:copy>"
            + "</xsl:template>"
            + "<xsl:template match=\"svg:svg\">"
            + "<xsl:copy>"
            + "<xsl:apply-templates select=\"@*\"/>"
            + "<xsl:attribute name=\"data-postprocessed\">yes</xsl:attribute>"
            + "<xsl:apply-templates select=\"node()\"/>"
            + "</xsl:copy>"
            + "</xsl:template>"
            + "</xsl:stylesheet>";
    }
}
