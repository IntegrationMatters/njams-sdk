package com.im.njams.sdk.model.svg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Unit tests for {@link NjamsProcessDiagramContext}, the mutable holder of SVG rendering state.
 */
public class NjamsProcessDiagramContextTest {

    @Test
    public void stringAndIntPropertiesRoundtrip() {
        NjamsProcessDiagramContext context = new NjamsProcessDiagramContext();
        context.setSvgNS("http://www.w3.org/2000/svg");
        context.setCategory("SDK");
        context.setHeight(100);
        context.setWidth(200);
        context.setStartX(10);
        context.setStartY(20);

        assertEquals("http://www.w3.org/2000/svg", context.getSvgNS());
        assertEquals("SDK", context.getCategory());
        assertEquals(100, context.getHeight());
        assertEquals(200, context.getWidth());
        assertEquals(10, context.getStartX());
        assertEquals(20, context.getStartY());
    }

    @Test
    public void documentAndElementPropertiesRoundtrip() {
        NjamsProcessDiagramContext context = new NjamsProcessDiagramContext();
        Document doc = mock(Document.class);
        Element container = mock(Element.class);

        context.setDoc(doc);
        context.setContainerElement(container);

        assertSame(doc, context.getDoc());
        assertSame(container, context.getContainerElement());
    }
}
