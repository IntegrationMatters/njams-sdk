package com.im.njams.sdk.model.image;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

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
    public void missingResourceThrowsDescriptiveException() {
        // A missing classpath resource is a packaging/programming error and must fail with a clear,
        // descriptive exception that names the path — not an opaque NullPointerException (SDK-459).
        try {
            new ResourceImageSupplier("missing", "no/such/resource.png");
            fail("expected NjamsSdkRuntimeException for a missing resource");
        } catch (NjamsSdkRuntimeException expected) {
            assertTrue("message must name the missing resource path",
                    expected.getMessage().contains("no/such/resource.png"));
        }
    }

    @Test
    public void missingResourceWithExplicitClassLoaderThrowsDescriptiveException() {
        try {
            new ResourceImageSupplier("missing", getClass().getClassLoader(), "no/such/resource.png");
            fail("expected NjamsSdkRuntimeException for a missing resource");
        } catch (NjamsSdkRuntimeException expected) {
            assertTrue("message must name the missing resource path",
                    expected.getMessage().contains("no/such/resource.png"));
        }
    }
}
