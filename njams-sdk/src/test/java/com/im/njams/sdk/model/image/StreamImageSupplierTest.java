package com.im.njams.sdk.model.image;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.Test;

/**
 * Unit tests for {@link StreamImageSupplier}: reading and re-encoding an image, and graceful
 * handling of a missing or failing stream.
 */
public class StreamImageSupplierTest {

    private static byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        return bos.toByteArray();
    }

    @Test
    public void readsAndReEncodesImageAsBase64Png() throws Exception {
        byte[] png = pngBytes(3, 4);
        StreamImageSupplier supplier =
                new StreamImageSupplier("img", () -> new ByteArrayInputStream(png));

        String base64 = supplier.getBase64Image();
        assertNotNull(base64);

        // the result must decode back into a readable PNG of the same dimensions
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        assertNotNull(decoded);
        org.junit.Assert.assertEquals(3, decoded.getWidth());
        org.junit.Assert.assertEquals(4, decoded.getHeight());
    }

    @Test
    public void nullStreamYieldsNull() {
        StreamImageSupplier supplier = new StreamImageSupplier("img", () -> null);
        assertNull(supplier.getBase64Image());
    }

    @Test
    public void failingStreamSupplierYieldsNull() {
        StreamImageSupplier supplier = new StreamImageSupplier("img", () -> {
            throw new java.io.IOException("boom");
        });
        assertNull(supplier.getBase64Image());
    }

    @Test
    public void nonImageContentYieldsNull() {
        // ImageIO.read returns null for non-image data, which the supplier turns into a null result
        StreamImageSupplier supplier = new StreamImageSupplier("img",
                () -> new ByteArrayInputStream("not an image".getBytes()));
        assertNull(supplier.getBase64Image());
    }
}
