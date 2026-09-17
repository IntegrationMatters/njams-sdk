package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.junit.Test;

public class XpathContextTest {

    @Test
    public void initialState_allFieldsNull() {
        XpathContext ctx = new XpathContext();
        assertNull(ctx.getXpf());
        assertNull(ctx.getXpath());
        assertNull(ctx.getExpr());
        assertNull(ctx.getSs());
        assertNull(ctx.getDoc());
    }

    @Test
    public void setAndGetDoc_roundtrips() {
        XpathContext ctx = new XpathContext();
        Source doc = mock(Source.class);
        ctx.setDoc(doc);
        assertSame(doc, ctx.getDoc());
    }

    @Test
    public void setAndGetXpf_roundtrips() {
        XpathContext ctx = new XpathContext();
        XPathFactory xpf = mock(XPathFactory.class);
        ctx.setXpf(xpf);
        assertSame(xpf, ctx.getXpf());
    }

    @Test
    public void setAndGetXpath_roundtrips() {
        XpathContext ctx = new XpathContext();
        XPath xpath = mock(XPath.class);
        ctx.setXpath(xpath);
        assertSame(xpath, ctx.getXpath());
    }

    @Test
    public void setAndGetExpr_roundtrips() {
        XpathContext ctx = new XpathContext();
        XPathExpression expr = mock(XPathExpression.class);
        ctx.setExpr(expr);
        assertSame(expr, ctx.getExpr());
    }

    @Test
    public void setAndGetSs_roundtrips() {
        XpathContext ctx = new XpathContext();
        SAXSource ss = new SAXSource();
        ctx.setSs(ss);
        assertSame(ss, ctx.getSs());
    }
}
