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

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;

public class NjamsProcessDiagramFactoryTest {

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

    private static NjamsProcessDiagramContext createSimpleContext() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc = dbf.newDocumentBuilder().newDocument();
        doc.appendChild(doc.createElementNS("http://www.w3.org/2000/svg", "svg"));
        NjamsProcessDiagramContext context = new NjamsProcessDiagramContext();
        context.setDoc(doc);
        context.setSvgNS("http://www.w3.org/2000/svg");
        return context;
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
