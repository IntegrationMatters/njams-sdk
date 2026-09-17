package com.im.njams.sdk.model.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Unit tests for {@link DirectImageSupplier}, which returns its given Base64 string verbatim.
 */
public class DirectImageSupplierTest {

    @Test
    public void returnsGivenBase64Verbatim() {
        DirectImageSupplier supplier = new DirectImageSupplier("img", "QUJD");
        assertEquals("QUJD", supplier.getBase64Image());
    }

    @Test
    public void nullImageIsReturnedAsNull() {
        assertNull(new DirectImageSupplier("img", null).getBase64Image());
    }
}
