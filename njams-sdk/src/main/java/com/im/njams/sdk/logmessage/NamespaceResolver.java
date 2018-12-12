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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author pnientiedt
 */
public class NamespaceResolver implements NamespaceContext {

    private static final Logger LOG = LoggerFactory.getLogger(NamespaceResolver.class);

    private static final String DEFAULT_NS = "DEFAULT";
    private final Map<String, String> prefix2Uri = new HashMap<String, String>();
    private final Map<String, String> uri2Prefix = new HashMap<String, String>();
    private String strXML = null;
    private Boolean toplevelOnly = false;

    /**
     * This constructor parses the document and stores all namespaces it can
     * find. If toplevelOnly is true, only namespaces in the root are used.
     * it also adds namespaces to grant access to Base64Decoder, java.lang.String and java.util.UUID
     *
     * @param strXML xml as string
     * @param toplevelOnly restriction of the search to enhance performance
     */
    public NamespaceResolver(String strXML, boolean toplevelOnly) {
        this.strXML = strXML;
        this.toplevelOnly = toplevelOnly;
        putInCache("base64", "java:java.util.Base64");
        putInCache("base64Decoder", "java:java.util.Base64$Decoder");
        putInCache("saxon", "http://saxon.sf.net/");
        putInCache("string", "java:java.lang.String");
        putInCache("uuid", "java:java.util.UUID");
        putInCache("xs", "http://www.w3.org/2001/XMLSchema");
    }

    private void loadNamespaces() {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document document = dBuilder.parse(new ByteArrayInputStream(strXML.getBytes()));
            examineNode(document.getFirstChild(), toplevelOnly);
        } catch (ParserConfigurationException e) {
            LOG.error("", e);
        } catch (SAXException e) {
            LOG.error("", e);
        } catch (IOException e) {
            LOG.error("", e);
        }
    }

    /**
     * A single node is read, the namespace attributes are extracted and stored.
     *
     * @param node to examine
     * @param attributesOnly , if true no recursion happens
     */
    private void examineNode(Node node, boolean attributesOnly) {
        synchronized (this) {
            NamedNodeMap attributes = node.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                storeAttribute((Attr) attribute);
            }

            if (!attributesOnly) {
                NodeList chields = node.getChildNodes();
                for (int i = 0; i < chields.getLength(); i++) {
                    Node chield = chields.item(i);
                    if (chield.getNodeType() == Node.ELEMENT_NODE) {
                        examineNode(chield, false);
                    }
                }
            }
        }
    }

    /**
     * This method looks at an attribute and stores it, if it is a namespace
     * attribute.
     *
     * @param attribute to examine
     */
    private void storeAttribute(Attr attribute) {
        // examine the attributes in namespace xmlns
        if (attribute.getName() != null && attribute.getName().startsWith("xmlns:")) {
            // Default namespace xmlns="uri goes here"
            String pre = attribute.getName().substring(6);
            if (attribute.getNodeName().equals(XMLConstants.XMLNS_ATTRIBUTE)) {
                putInCache(DEFAULT_NS, attribute.getValue());
            } else {
                // The defined prefixes are stored here
                putInCache(pre, attribute.getValue());
            }
        }
    }

    private void putInCache(String prefix, String uri) {
        prefix2Uri.put(prefix, uri);
        uri2Prefix.put(uri, prefix);
    }

    /**
     * This method is called by XPath. It returns the default namespace, if the
     * prefix is null or "".
     *
     * @param prefix to search for
     * @return uri
     */
    @Override
    public String getNamespaceURI(String prefix) {
        if (prefix == null || prefix.equals(XMLConstants.DEFAULT_NS_PREFIX)) {
            String pre = prefix2Uri.get(DEFAULT_NS);
            if (pre == null) {
                loadNamespaces();
            }
            return prefix2Uri.get(DEFAULT_NS);
        } else {
            String pre = prefix2Uri.get(prefix);
            if (pre == null) {
                loadNamespaces();
            }
            return prefix2Uri.get(prefix);
        }
    }

    /**
     * This method is not needed in this context, but can be implemented in a
     * similar way.
     */
    @Override
    public String getPrefix(String namespaceURI) {
        return uri2Prefix.get(namespaceURI);
    }

    @Override
    public Iterator<?> getPrefixes(String namespaceURI) {
        // Not implemented
        return null;
    }
}
