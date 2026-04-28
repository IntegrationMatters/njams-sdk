/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
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
