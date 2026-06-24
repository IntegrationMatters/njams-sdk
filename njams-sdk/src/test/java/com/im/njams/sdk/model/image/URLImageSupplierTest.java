package com.im.njams.sdk.model.image;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.URI;
import java.net.URL;

import org.junit.Test;

/**
 * Unit tests for {@link URLImageSupplier}: loading from a URL, the URI-to-URL conversion error
 * path and graceful handling of an unreachable URL.
 */
public class URLImageSupplierTest {

    @Test
    public void readsImageFromResourceUrl() {
        URL url = getClass().getClassLoader().getResource("images/root.png");
        assertNotNull("test pre-condition: images/root.png must be on the classpath", url);

        assertNotNull(new URLImageSupplier("root", url).getBase64Image());
    }

    @Test
    public void invalidUriSchemeThrowsIllegalArgument() throws Exception {
        try {
            new URLImageSupplier("img", new URI("unknownscheme://host/x"));
            fail("expected IllegalArgumentException for a URI with an unknown protocol");
        } catch (IllegalArgumentException expected) {
            // expected: MalformedURLException is wrapped into IllegalArgumentException
        }
    }

    @Test
    public void unreachableUrlYieldsNull() throws Exception {
        URL url = new File("does-not-exist-" + System.nanoTime() + ".png").toURI().toURL();
        assertNull(new URLImageSupplier("img", url).getBase64Image());
    }
}
