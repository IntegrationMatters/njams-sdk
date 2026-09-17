package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import javax.xml.namespace.NamespaceContext;

import org.junit.After;
import org.junit.Test;

/**
 * Unit tests for {@link NamespaceResolver}: lazy namespace extraction from an XML string, the
 * predefined prefixes, the top-level-only restriction and the {@link NamespaceContext} contract.
 */
public class NamespaceResolverTest {

    @After
    public void resetFactory() {
        // restore the shared default factory in case a test replaced it
        NamespaceResolver.setDocumentBuilderFactory(null);
    }

    @Test
    public void predefinedPrefixesAreAlwaysAvailable() {
        NamespaceResolver resolver = new NamespaceResolver("<root/>", true);
        assertEquals("http://www.w3.org/2001/XMLSchema", resolver.getNamespaceURI("xs"));
        assertEquals("java:java.util.UUID", resolver.getNamespaceURI("uuid"));
        assertEquals("java:java.lang.String", resolver.getNamespaceURI("string"));
        assertEquals("java:java.util.Base64", resolver.getNamespaceURI("base64"));
        assertEquals("java:java.util.Base64$Decoder", resolver.getNamespaceURI("base64Decoder"));
        assertEquals("http://saxon.sf.net/", resolver.getNamespaceURI("saxon"));
    }

    @Test
    public void resolvesPrefixDeclaredInDocument() {
        NamespaceResolver resolver =
                new NamespaceResolver("<a:root xmlns:a=\"urn:a\"><a:child/></a:root>", true);
        assertEquals("urn:a", resolver.getNamespaceURI("a"));
    }

    @Test
    public void resolvesDefaultNamespaceDeclaredInDocument() {
        NamespaceResolver resolver =
                new NamespaceResolver("<root xmlns=\"urn:default\"><child/></root>", true);
        assertEquals("urn:default", resolver.getNamespaceURI(""));
        assertEquals("urn:default", resolver.getNamespaceURI(null));
    }

    @Test
    public void resolvesDefaultAndPrefixedNamespacesTogether() {
        NamespaceResolver resolver =
                new NamespaceResolver("<root xmlns=\"urn:default\" xmlns:a=\"urn:a\"/>", true);
        assertEquals("urn:default", resolver.getNamespaceURI(""));
        assertEquals("urn:a", resolver.getNamespaceURI("a"));
    }

    @Test
    public void topLevelOnlyIgnoresNestedDeclarations() {
        String xml = "<a:root xmlns:a=\"urn:a\"><a:child xmlns:b=\"urn:b\"/></a:root>";

        NamespaceResolver topLevel = new NamespaceResolver(xml, true);
        assertEquals("urn:a", topLevel.getNamespaceURI("a"));
        assertNull("nested declaration must not be loaded when toplevelOnly is true",
                topLevel.getNamespaceURI("b"));

        NamespaceResolver deep = new NamespaceResolver(xml, false);
        assertEquals("urn:a", deep.getNamespaceURI("a"));
        assertEquals("urn:b", deep.getNamespaceURI("b"));
    }

    @Test
    public void unknownPrefixReturnsNull() {
        NamespaceResolver resolver = new NamespaceResolver("<a:root xmlns:a=\"urn:a\"/>", true);
        assertNull(resolver.getNamespaceURI("doesNotExist"));
    }

    @Test
    public void getPrefixIsReverseLookup() {
        NamespaceResolver resolver = new NamespaceResolver("<a:root xmlns:a=\"urn:a\"/>", true);
        // predefined reverse lookups are available without parsing
        assertEquals("saxon", resolver.getPrefix("http://saxon.sf.net/"));
        // trigger parsing, then the declared prefix is reverse-resolvable too
        resolver.getNamespaceURI("a");
        assertEquals("a", resolver.getPrefix("urn:a"));
    }

    @Test
    public void getPrefixesIsNotImplementedAndReturnsNull() {
        NamespaceResolver resolver = new NamespaceResolver("<root/>", true);
        assertNull(resolver.getPrefixes("urn:a"));
    }

    @Test
    public void malformedXmlDoesNotThrowAndKeepsPredefinedPrefixes() {
        NamespaceResolver resolver = new NamespaceResolver("this is not xml", true);
        // parsing fails internally but is swallowed; predefined prefixes remain usable
        assertEquals("http://www.w3.org/2001/XMLSchema", resolver.getNamespaceURI("xs"));
        assertNull(resolver.getNamespaceURI("a"));
    }
}
