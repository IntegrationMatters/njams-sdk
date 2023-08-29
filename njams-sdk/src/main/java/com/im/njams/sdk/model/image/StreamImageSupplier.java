package com.im.njams.sdk.model.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link ImageSupplier} implementation that uses a stream supplier for reading the images source.
 */
public class StreamImageSupplier extends ImageSupplier {

    /**
     * Provides (opens-) an input stream for reading an image's source.
     */
    @FunctionalInterface
    public interface StreamSupplier {
        public InputStream openStream() throws Exception;
    }

    private static final Logger LOG = LoggerFactory.getLogger(StreamImageSupplier.class);

    private final StreamSupplier streamSupplier;

    /**
     * Sole constructor.
     * @param name The name for the image being loaded.
     * @param streamSupplier The factory that opens an {@link InputStream} for reading the image source.
     */
    public StreamImageSupplier(final String name, final StreamSupplier streamSupplier) {
        super(name);
        this.streamSupplier = streamSupplier;
    }

    @Override
    public String getBase64Image() {
        LOG.debug("Get image {} from input stream", getName());
        byte[] bin = getResourceBinary();
        if (bin != null && bin.length > 0) {
            return encodeBase64(bin);
        }
        return null;
    }

    private byte[] getResourceBinary() {
        try {
            try (InputStream is = streamSupplier.openStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                if (is == null) {
                    LOG.warn("Could not create input stream for resource: {}", getName());
                    return null;
                }
                BufferedImage image = ImageIO.read(is);
                ImageIO.write(image, "png", bos);
                bos.flush();
                return bos.toByteArray();
            }
        } catch (Exception e) {
            LOG.warn("Could not read from input stream for resource: {}", getName(), e);
        }
        return null;
    }

}
