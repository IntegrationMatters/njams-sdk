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
import com.im.njams.sdk.utils.StringUtils;

public class NjamsProcessDiagramFactoryTest {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";
    private static final String TOOLTIP_ATTR = "nj-sdk-tooltip";

    private static Element findLabelById(Document doc, String id) {
        return findByTagAndAttr(doc, "text", "id", id);
    }

    private static Element findByTagAndAttr(Document doc, String tag, String attr, String value) {
        NodeList list = doc.getElementsByTagNameNS(SVG_NS, tag);
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            if (value.equals(e.getAttributeNS(null, attr))) {
                return e;
            }
        }
        return null;
    }

    private static boolean anyTextHasTooltip(Document doc) {
        NodeList texts = doc.getElementsByTagNameNS(SVG_NS, "text");
        for (int i = 0; i < texts.getLength(); i++) {
            if (((Element) texts.item(i)).hasAttributeNS(null, TOOLTIP_ATTR)) {
                return true;
            }
        }
        return false;
    }

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
    public void drawGroup_labelTextIsFullGroupName() throws Exception {
        NjamsProcessDiagramContext context = createDrawableContext("cat");
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(true);

        GroupModel group = buildGroup("g1", "MyGroup", "loop", 0, 0, 400, 100);
        factory.drawGroup(context, group);

        NodeList texts = context.getDoc().getElementsByTagNameNS(SVG_NS, "text");
        String labelText = null;
        for (int i = 0; i < texts.getLength(); i++) {
            Element t = (Element) texts.item(i);
            if ("g1_label".equals(t.getAttributeNS(null, "id"))) {
                labelText = t.getTextContent();
                break;
            }
        }
        Assert.assertNotNull("Expected label text element", labelText);
        Assert.assertEquals("Label text must equal the group name", "MyGroup", labelText);
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

    @Test
    public void drawGroup_longLabelTruncatedWhenHeaderTooNarrow() throws Exception {
        NjamsProcessDiagramContext context = createDrawableContext("cat");
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(true);
        // header width 100: available = 100 - 16 (icon) - 4 (gap) - 50 (reserved) = 30px
        // maxChars = 30 / (15 * 0.4) = 5; "A very long name" won't fit
        GroupModel group = buildGroup("g1", "A very long name", "loop", 0, 0, 100, 100);
        factory.drawGroup(context, group);

        NodeList texts = context.getDoc().getElementsByTagNameNS(SVG_NS, "text");
        String labelText = null;
        for (int i = 0; i < texts.getLength(); i++) {
            Element t = (Element) texts.item(i);
            if ("g1_label".equals(t.getAttributeNS(null, "id"))) {
                labelText = t.getTextContent();
                break;
            }
        }
        Assert.assertNotNull("Expected label text element", labelText);
        Assert.assertTrue("Truncated label must end with ellipsis",
            labelText.endsWith(String.valueOf(StringUtils.ELLIPSIS)));
        Assert.assertTrue("Truncated label must be shorter than full name",
            labelText.length() < "A very long name".length());
    }

    @Test
    public void truncateLabel_nullReturnsEmpty() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramFactory.FittedLabel result = factory.truncateLabel(null, 200);
        Assert.assertEquals("", result.singleLine());
        Assert.assertFalse(result.isTruncated());
    }

    @Test
    public void truncateLabel_blankReturnsEmpty() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramFactory.FittedLabel result = factory.truncateLabel("   ", 200);
        Assert.assertEquals("", result.singleLine());
        Assert.assertFalse(result.isTruncated());
    }

    @Test
    public void truncateLabel_shortTextReturnedUnchanged() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramFactory.FittedLabel result = factory.truncateLabel("Hello", 500);
        Assert.assertEquals("Hello", result.singleLine());
        Assert.assertFalse("Short text must not be flagged truncated", result.isTruncated());
    }

    @Test
    public void truncateLabel_longTextTruncatedWithEllipsis() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramFactory.FittedLabel result = factory.truncateLabel("A very long group name indeed", 20);
        Assert.assertTrue("Must end with ellipsis",
            result.singleLine().endsWith(String.valueOf(StringUtils.ELLIPSIS)));
        Assert.assertTrue("Must be shorter than input",
            result.singleLine().length() < "A very long group name indeed".length());
        Assert.assertTrue("Truncated text must be flagged truncated", result.isTruncated());
        Assert.assertEquals("Full text must be the normalized original",
            "A very long group name indeed", result.getFull());
    }

    @Test
    public void truncateLabel_fullTextNormalizesWhitespace() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramFactory.FittedLabel result = factory.truncateLabel("A   very \t long   name", 20);
        Assert.assertTrue(result.isTruncated());
        Assert.assertEquals("Full text must be whitespace-normalized", "A very long name", result.getFull());
    }

    @Test
    public void truncateLabel_zeroWidthFallsBackToDefault() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramFactory.FittedLabel result = factory.truncateLabel("Hello", 0);
        Assert.assertNotNull(result);
        Assert.assertFalse(result.singleLine().isEmpty());
    }

    private static double widthFor(int maxChars) {
        return maxChars * NjamsProcessDiagramFactory.DEFAULT_TEXT_SIZE
            * NjamsProcessDiagramFactory.DEFAULT_CHAR_WIDTH_FACTOR;
    }

    @Test
    public void wrapLabel_nullReturnsEmpty() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramFactory.FittedLabel result = factory.wrapLabel(null, widthFor(10));
        Assert.assertArrayEquals(new String[0], result.getLines());
        Assert.assertFalse(result.isTruncated());
    }

    @Test
    public void wrapLabel_blankReturnsEmpty() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramFactory.FittedLabel result = factory.wrapLabel("   \t\n  ", widthFor(10));
        Assert.assertArrayEquals(new String[0], result.getLines());
        Assert.assertFalse(result.isTruncated());
    }

    @Test
    public void wrapLabel_shortTextReturnsSingleLine() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramFactory.FittedLabel result = factory.wrapLabel("Hello", widthFor(10));
        Assert.assertArrayEquals(new String[]{"Hello"}, result.getLines());
        Assert.assertFalse(result.isTruncated());
    }

    @Test
    public void wrapLabel_normalizesWhitespace() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        Assert.assertArrayEquals(new String[]{"Hello World"},
            factory.wrapLabel("Hello  \t\n  World", widthFor(20)).getLines());
    }

    @Test
    public void wrapLabel_splitsOnSpace() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        // maxChars=6: "Hello " has space at index 5 → line="Hello", next="World"
        NjamsProcessDiagramFactory.FittedLabel result = factory.wrapLabel("Hello World", widthFor(6));
        Assert.assertArrayEquals(new String[]{"Hello", "World"}, result.getLines());
        Assert.assertFalse("Two complete lines must not be flagged truncated", result.isTruncated());
    }

    @Test
    public void wrapLabel_splitsOnNonAlnumKeepingChar() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        // maxChars=2: "A.B" → window "A." has '.' at index 1 → line="A." (kept), next="B"
        Assert.assertArrayEquals(new String[]{"A.", "B"}, factory.wrapLabel("A.B", widthFor(2)).getLines());
    }

    @Test
    public void wrapLabel_hardSplitWhenNoSplitChar() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        // maxChars=3: "ABCDE" → hard split at 3 → "ABC", "DE"
        Assert.assertArrayEquals(new String[]{"ABC", "DE"}, factory.wrapLabel("ABCDE", widthFor(3)).getLines());
    }

    @Test
    public void wrapLabel_longTextTruncatesOnSecondLine() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        // maxChars=5, 2-line limit: line 2 budget=4, "Worl" all alnum → hard truncate
        NjamsProcessDiagramFactory.FittedLabel result = factory.wrapLabel("Hello World Greet", widthFor(5));
        Assert.assertArrayEquals(new String[]{"Hello", "Worl" + StringUtils.ELLIPSIS}, result.getLines());
        Assert.assertTrue("Dropped content must be flagged truncated", result.isTruncated());
        Assert.assertEquals("Full text must be the normalized original", "Hello World Greet", result.getFull());
    }

    @Test
    public void wrapLabel_truncationSplitsOnSpaceInBudget() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        // maxChars=6: line 1 "AAAA" (space at 4); line 2 budget=5, "BB CC" has space at 2 → "BB" + ELLIPSIS
        Assert.assertArrayEquals(new String[]{"AAAA", "BB" + StringUtils.ELLIPSIS},
            factory.wrapLabel("AAAA BB CCCC", widthFor(6)).getLines());
    }

    @Test
    public void wrapLabel_truncatesWithNonAlnumSplitInLastLine() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        // maxChars=6, 2-line limit: line 2 budget=5, window "K!MNO" has '!' at index 1 → "K!" + ELLIPSIS
        Assert.assertArrayEquals(new String[]{"ABCDE", "K!" + StringUtils.ELLIPSIS},
            factory.wrapLabel("ABCDE K!MNOPQR", widthFor(6)).getLines());
    }

    @Test
    public void wrapLabel_noLineLongerThanMaxChars() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        int maxChars = 8;
        String[] lines = factory.wrapLabel("Hello World This Is Very Long Text Indeed", widthFor(maxChars)).getLines();
        Assert.assertTrue("Expected at least one line", lines.length > 0);
        for (String line : lines) {
            Assert.assertTrue("Line '" + line + "' exceeds maxChars=" + maxChars, line.length() <= maxChars);
        }
    }

    @Test
    public void wrapLabel_zeroWidthFallsBackToDefaultActivitySize() {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        // lineWidth=0 should not throw and should produce at least one line
        String[] lines = factory.wrapLabel("Some label", 0).getLines();
        Assert.assertTrue("Expected at least one line for zero-width fallback", lines.length > 0);
    }

    @Test
    public void drawTransition_namedTransitionProducesTextElementWithDirectContent() throws Exception {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramContext context = createSimpleContext();
        context.setContainerElement(context.getDoc().getDocumentElement());
        context.setStartX(0);
        context.setStartY(0);

        ActivityModel from = new ActivityModel(null, "a", "A", "step");
        from.setX(0);
        from.setY(0);
        ActivityModel to = new ActivityModel(null, "b", "B", "step");
        to.setX(200);
        to.setY(0);

        TransitionModel transition = new TransitionModel(null, "a_b");
        transition.setFromActivity(from);
        transition.setToActivity(to);
        transition.setName("My Label");

        factory.drawTransition(context, transition);

        NodeList texts = context.getDoc().getElementsByTagNameNS(SVG_NS, "text");
        Element labelElem = null;
        for (int i = 0; i < texts.getLength(); i++) {
            Element t = (Element) texts.item(i);
            if ("a_b_label".equals(t.getAttributeNS(null, "id"))) {
                labelElem = t;
                break;
            }
        }
        Assert.assertNotNull("Expected a text element with id 'a_b_label'", labelElem);
        // Label text must be a direct text node child — no tspan wrapper
        Assert.assertEquals(org.w3c.dom.Node.TEXT_NODE, labelElem.getFirstChild().getNodeType());
        Assert.assertEquals("My Label", labelElem.getFirstChild().getNodeValue());
    }

    @Test
    public void drawTransition_nameEqualToId_producesLabelInDefaultMode() throws Exception {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramContext context = createSimpleContext();
        context.setContainerElement(context.getDoc().getDocumentElement());
        context.setStartX(0);
        context.setStartY(0);

        ActivityModel from = new ActivityModel(null, "a", "A", "step");
        from.setX(0);
        from.setY(0);
        ActivityModel to = new ActivityModel(null, "b", "B", "step");
        to.setX(200);
        to.setY(0);

        TransitionModel transition = new TransitionModel(null, "a_b");
        transition.setFromActivity(from);
        transition.setToActivity(to);
        transition.setName("a_b"); // name == id — no suppression without compat mode

        factory.drawTransition(context, transition);

        final String svg = factory.serializeDocument(context);
        Assert.assertTrue("Default mode: name==id must still produce a label", svg.contains("a_b_label"));
    }

    @Test
    public void drawTransition_withCompatMode_nameEqualToId_suppressesLabel() throws Exception {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false, true);
        NjamsProcessDiagramContext context = createSimpleContext();
        context.setContainerElement(context.getDoc().getDocumentElement());
        context.setStartX(0);
        context.setStartY(0);

        ActivityModel from = new ActivityModel(null, "a", "A", "step");
        from.setX(0);
        from.setY(0);
        ActivityModel to = new ActivityModel(null, "b", "B", "step");
        to.setX(200);
        to.setY(0);

        TransitionModel transition = new TransitionModel(null, "a_b");
        transition.setFromActivity(from);
        transition.setToActivity(to);
        transition.setName("a_b"); // name == id — must be suppressed in compat mode

        factory.drawTransition(context, transition);

        final String svg = factory.serializeDocument(context);
        Assert.assertFalse("Compat mode: name==id must not produce a label", svg.contains("a_b_label"));
    }

    @Test
    public void drawTransition_withCompatMode_explicitNameDifferentFromId_producesLabel() throws Exception {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false, true);
        NjamsProcessDiagramContext context = createSimpleContext();
        context.setContainerElement(context.getDoc().getDocumentElement());
        context.setStartX(0);
        context.setStartY(0);

        ActivityModel from = new ActivityModel(null, "a", "A", "step");
        from.setX(0);
        from.setY(0);
        ActivityModel to = new ActivityModel(null, "b", "B", "step");
        to.setX(200);
        to.setY(0);

        TransitionModel transition = new TransitionModel(null, "a_b");
        transition.setFromActivity(from);
        transition.setToActivity(to);
        transition.setName("My Condition"); // explicit name != id

        factory.drawTransition(context, transition);

        final String svg = factory.serializeDocument(context);
        Assert.assertTrue("Compat mode with explicit name != id must still produce a label", svg.contains("a_b_label"));
    }

    @Test
    public void drawTransition_multilineNameProducesMultipleTextElements() throws Exception {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramContext context = createSimpleContext();
        context.setContainerElement(context.getDoc().getDocumentElement());
        context.setStartX(0);
        context.setStartY(0);

        // Force wrapping: narrow line width gives maxChars=5; "Hello World" needs two lines
        ActivityModel from = new ActivityModel(null, "a", "A", "step");
        from.setX(0);
        from.setY(0);
        ActivityModel to = new ActivityModel(null, "b", "B", "step");
        // small horizontal gap so wrapLabel produces 2+ lines
        to.setX(50);
        to.setY(0);

        TransitionModel transition = new TransitionModel(null, "a_b");
        transition.setFromActivity(from);
        transition.setToActivity(to);
        transition.setName("Hello World");

        factory.drawTransition(context, transition);

        // First text element carries the _label id; at least one more text element for line 2
        NodeList texts = context.getDoc().getElementsByTagNameNS(SVG_NS, "text");
        long labelTexts = 0;
        for (int i = 0; i < texts.getLength(); i++) {
            Element t = (Element) texts.item(i);
            String id = t.getAttributeNS(null, "id");
            if (id.startsWith("a_b_label")) {
                labelTexts++;
                Assert.assertEquals(org.w3c.dom.Node.TEXT_NODE, t.getFirstChild().getNodeType());
            }
        }
        Assert.assertTrue("Expected multiple text elements for wrapped label, got " + labelTexts, labelTexts > 1);
    }

    @Test
    public void drawActivity_shortLabelRetainedUnchanged() throws Exception {
        NjamsProcessDiagramContext context = createDrawableContext("cat");
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);

        ActivityModel activity = new ActivityModel(null, "a1", "Short", "step");
        activity.setX(0);
        activity.setY(0);
        factory.drawActivity(context, activity);

        NodeList texts = context.getDoc().getElementsByTagNameNS(SVG_NS, "text");
        String labelText = null;
        for (int i = 0; i < texts.getLength(); i++) {
            Element t = (Element) texts.item(i);
            if ("a1_label".equals(t.getAttributeNS(null, "id"))) {
                labelText = t.getTextContent();
                break;
            }
        }
        Assert.assertEquals("Short label must be unchanged", "Short", labelText);
    }

    @Test
    public void drawActivity_longLabelTruncatedToTwoActivityWidths() throws Exception {
        NjamsProcessDiagramContext context = createDrawableContext("cat");
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);

        // maxWidth = ACTIVITY_LABEL_MAX_WIDTH_FACTOR(2) * DEFAULT_ACTIVITY_SIZE(50) = 100px
        // maxChars = floor(100 / (15 * 0.4)) = 16; "A very long activity name here" (30 chars) must be truncated
        ActivityModel activity = new ActivityModel(null, "a1", "A very long activity name here", "step");
        activity.setX(0);
        activity.setY(0);
        factory.drawActivity(context, activity);

        NodeList texts = context.getDoc().getElementsByTagNameNS(SVG_NS, "text");
        String labelText = null;
        for (int i = 0; i < texts.getLength(); i++) {
            Element t = (Element) texts.item(i);
            if ("a1_label".equals(t.getAttributeNS(null, "id"))) {
                labelText = t.getTextContent();
                break;
            }
        }
        Assert.assertNotNull("Expected label text element", labelText);
        Assert.assertTrue("Truncated label must end with ellipsis",
            labelText.endsWith(String.valueOf(StringUtils.ELLIPSIS)));
        Assert.assertTrue("Truncated label must be shorter than full name",
            labelText.length() < "A very long activity name here".length());
    }

    @Test
    public void drawActivity_truncatedLabelPutsFullTooltipOnImage() throws Exception {
        NjamsProcessDiagramContext context = createDrawableContext("cat");
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);

        ActivityModel activity = new ActivityModel(null, "a1", "A very long activity name here", "step");
        activity.setX(0);
        activity.setY(0);
        factory.drawActivity(context, activity);

        Element image = findByTagAndAttr(context.getDoc(), "image", "modelId", "a1");
        Assert.assertNotNull("Expected activity image element", image);
        Assert.assertEquals("Tooltip must sit on the image and hold the full label",
            "A very long activity name here", image.getAttributeNS(null, TOOLTIP_ATTR));
        Assert.assertFalse("Label text element must not carry the tooltip",
            findLabelById(context.getDoc(), "a1_label").hasAttributeNS(null, TOOLTIP_ATTR));
    }

    @Test
    public void drawActivity_shortLabelHasNoTooltip() throws Exception {
        NjamsProcessDiagramContext context = createDrawableContext("cat");
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);

        ActivityModel activity = new ActivityModel(null, "a1", "Short", "step");
        activity.setX(0);
        activity.setY(0);
        factory.drawActivity(context, activity);

        Element image = findByTagAndAttr(context.getDoc(), "image", "modelId", "a1");
        Assert.assertNotNull("Expected activity image element", image);
        Assert.assertFalse("Non-truncated activity must not carry the tooltip attribute",
            image.hasAttributeNS(null, TOOLTIP_ATTR));
    }

    @Test
    public void drawGroup_truncatedLabelPutsFullTooltipOnHeader() throws Exception {
        NjamsProcessDiagramContext context = createDrawableContext("cat");
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(true);
        // header width 100 forces truncation (see drawGroup_longLabelTruncatedWhenHeaderTooNarrow)
        GroupModel group = buildGroup("g1", "A very long name", "loop", 0, 0, 100, 100);
        factory.drawGroup(context, group);

        Element header = findByTagAndAttr(context.getDoc(), "rect", "id", "g1_group_header");
        Assert.assertNotNull("Expected group header rect", header);
        Assert.assertEquals("Tooltip must sit on the group header and hold the full label",
            "A very long name", header.getAttributeNS(null, TOOLTIP_ATTR));
        Assert.assertFalse("Label text element must not carry the tooltip",
            findLabelById(context.getDoc(), "g1_label").hasAttributeNS(null, TOOLTIP_ATTR));
    }

    @Test
    public void drawGroup_shortLabelHasNoTooltip() throws Exception {
        NjamsProcessDiagramContext context = createDrawableContext("cat");
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(true);
        GroupModel group = buildGroup("g1", "MyGroup", "loop", 0, 0, 400, 100);
        factory.drawGroup(context, group);

        Element header = findByTagAndAttr(context.getDoc(), "rect", "id", "g1_group_header");
        Assert.assertNotNull("Expected group header rect", header);
        Assert.assertFalse("Non-truncated group must not carry the tooltip attribute",
            header.hasAttributeNS(null, TOOLTIP_ATTR));
    }

    @Test
    public void drawTransition_truncatedLabelPutsFullTooltipOnLine() throws Exception {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramContext context = createSimpleContext();
        context.setContainerElement(context.getDoc().getDocumentElement());
        context.setStartX(0);
        context.setStartY(0);

        ActivityModel from = new ActivityModel(null, "a", "A", "step");
        from.setX(0);
        from.setY(0);
        ActivityModel to = new ActivityModel(null, "b", "B", "step");
        to.setX(50); // narrow gap → small maxChars → forces wrap and truncation
        to.setY(0);

        TransitionModel transition = new TransitionModel(null, "a_b");
        transition.setFromActivity(from);
        transition.setToActivity(to);
        transition.setName("Alpha Beta Gamma Delta");

        factory.drawTransition(context, transition);

        Element line = findByTagAndAttr(context.getDoc(), "line", "modelId", "a_b");
        Assert.assertNotNull("Expected transition line element", line);
        Assert.assertEquals("Tooltip must sit on the line and hold the full label",
            "Alpha Beta Gamma Delta", line.getAttributeNS(null, TOOLTIP_ATTR));
        Assert.assertFalse("No label text element may carry the tooltip", anyTextHasTooltip(context.getDoc()));
    }

    @Test
    public void drawTransition_wrappedButCompleteLabelHasNoTooltip() throws Exception {
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(false);
        NjamsProcessDiagramContext context = createSimpleContext();
        context.setContainerElement(context.getDoc().getDocumentElement());
        context.setStartX(0);
        context.setStartY(0);

        ActivityModel from = new ActivityModel(null, "a", "A", "step");
        from.setX(0);
        from.setY(0);
        ActivityModel to = new ActivityModel(null, "b", "B", "step");
        to.setX(50);
        to.setY(0);

        TransitionModel transition = new TransitionModel(null, "a_b");
        transition.setFromActivity(from);
        transition.setToActivity(to);
        transition.setName("Hi Yo"); // fully fits across the wrapped lines, nothing dropped

        factory.drawTransition(context, transition);

        Element line = findByTagAndAttr(context.getDoc(), "line", "modelId", "a_b");
        Assert.assertNotNull("Expected transition line element", line);
        Assert.assertFalse("Fully-visible wrapped label must not put a tooltip on the line",
            line.hasAttributeNS(null, TOOLTIP_ATTR));
        Assert.assertFalse("No label text element may carry the tooltip", anyTextHasTooltip(context.getDoc()));
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
