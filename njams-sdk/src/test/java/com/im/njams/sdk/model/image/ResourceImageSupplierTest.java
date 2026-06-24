package com.im.njams.sdk.model.image;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Unit tests for {@link ResourceImageSupplier}: loading a classpath image with the default and an
 * explicit class loader, and the behaviour for a missing resource.
 */
public class ResourceImageSupplierTest {

    private static final String EXISTING_RESOURCE = "images/root.png";

    @Test
    public void loadsExistingResourceWithDefaultClassLoader() {
        assertNotNull(new ResourceImageSupplier("root", EXISTING_RESOURCE).getBase64Image());
    }

    @Test
    public void loadsExistingResourceWithExplicitClassLoader() {
        ResourceImageSupplier supplier =
                new ResourceImageSupplier("root", getClass().getClassLoader(), EXISTING_RESOURCE);
        assertNotNull(supplier.getBase64Image());
    }

    @Test
    public void missingResourceFailsAtConstruction() {
        // A missing resource resolves to a null URL; the supplier currently fails fast at
        // construction (NullPointerException from the null-URL method reference) rather than
        // producing a null image later. See SDK-459.
        try {
            new ResourceImageSupplier("missing", "no/such/resource.png");
            fail("expected the construction to fail for a missing resource");
        } catch (NullPointerException expected) {
            // documents current behaviour
        }
    }
}
