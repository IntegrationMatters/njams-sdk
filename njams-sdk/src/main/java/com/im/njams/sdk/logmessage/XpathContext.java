/* 
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * The Software shall be used for Good, not Evil.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.logmessage;

import javax.xml.transform.sax.SAXSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import net.sf.saxon.om.NodeInfo;

/**
 *
 * @author pnientiedt
 */
public class XpathContext {

    private XPathFactory xpf = null;
    private XPath xpath = null;
    private XPathExpression expr = null;
    private SAXSource ss = null;
    private NodeInfo doc = null;

    /**
     * @return the xpf
     */
    public XPathFactory getXpf() {
        return xpf;
    }

    /**
     * @param xpf the xpf to set
     */
    public void setXpf(XPathFactory xpf) {
        this.xpf = xpf;
    }

    /**
     * @return the xpath
     */
    public XPath getXpath() {
        return xpath;
    }

    /**
     * @param xpath the xpath to set
     */
    public void setXpath(XPath xpath) {
        this.xpath = xpath;
    }

    /**
     * @return the expr
     */
    public XPathExpression getExpr() {
        return expr;
    }

    /**
     * @param expr the expr to set
     */
    public void setExpr(XPathExpression expr) {
        this.expr = expr;
    }

    /**
     * @return the ss
     */
    public SAXSource getSs() {
        return ss;
    }

    /**
     * @param ss the ss to set
     */
    public void setSs(SAXSource ss) {
        this.ss = ss;
    }

    /**
     * @return the doc
     */
    public NodeInfo getDoc() {
        return doc;
    }

    /**
     * @param doc the doc to set
     */
    public void setDoc(NodeInfo doc) {
        this.doc = doc;
    }
}
