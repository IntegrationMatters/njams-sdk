package com.im.njams.sdk.model.image;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import org.junit.Test;

/**
 * Unit tests for {@link FileImageSupplier}: loading an image from a file and graceful handling of
 * a non-existent file.
 */
public class FileImageSupplierTest {

    @Test
    public void readsImageFromFile() throws Exception {
        File file = File.createTempFile("njams-image-test", ".png");
        file.deleteOnExit();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", file);

        assertNotNull(new FileImageSupplier("img", file).getBase64Image());
    }

    @Test
    public void nonExistentFileYieldsNull() {
        File file = new File("does-not-exist-" + System.nanoTime() + ".png");
        // unlike a missing classpath resource, a missing file is handled gracefully and yields null
        assertNull(new FileImageSupplier("img", file).getBase64Image());
    }
}
