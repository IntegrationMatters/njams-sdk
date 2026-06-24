package com.im.njams.sdk.model.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

/**
 * Unit tests for the {@link ImageSupplier} base class: naming, Base64 helper and the
 * name-based equality contract.
 */
public class ImageSupplierTest {

    @Test
    public void getNameReturnsConstructorValue() {
        assertEquals("logo", new DirectImageSupplier("logo", "data").getName());
    }

    @Test(expected = NullPointerException.class)
    public void nullNameIsRejected() {
        new DirectImageSupplier(null, "data");
    }

    @Test
    public void encodeBase64MatchesJdkEncoding() {
        assertEquals("YWJj", ImageSupplier.encodeBase64("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void equalityIsBasedOnNameOnly() {
        ImageSupplier a = new DirectImageSupplier("same", "imageA");
        ImageSupplier b = new DirectImageSupplier("same", "imageB");
        ImageSupplier c = new DirectImageSupplier("other", "imageA");

        assertEquals("same name -> equal regardless of content", a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    public void equalsRejectsNullAndOtherTypes() {
        ImageSupplier a = new DirectImageSupplier("name", "data");
        assertFalse(a.equals(null));
        assertFalse(a.equals("name"));
    }

    @Test
    public void toStringContainsNameAndType() {
        String s = new DirectImageSupplier("logo", "data").toString();
        assertTrue(s.contains("logo"));
        assertTrue(s.contains("DirectImageSupplier"));
    }
}
