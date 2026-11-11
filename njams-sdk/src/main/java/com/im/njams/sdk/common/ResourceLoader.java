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
package com.im.njams.sdk.common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.model.image.DirectImageSupplier;
import com.im.njams.sdk.model.image.FileImageSupplier;
import com.im.njams.sdk.model.image.ImageSupplier;
import com.im.njams.sdk.model.image.ResourceImageSupplier;
import com.im.njams.sdk.model.image.StreamImageSupplier;
import com.im.njams.sdk.model.image.URLImageSupplier;

/**
 * Loads a resource from a given path with current thread classloader.
 *
 * @author pnientiedt
 * @deprecated Load resources directly instead, e.g. with {@link ClassLoader#getResourceAsStream(String)}. For
 *             providing images to the SDK, use an {@link ImageSupplier} implementation, such as
 *             {@link ResourceImageSupplier}, {@link FileImageSupplier}, {@link URLImageSupplier},
 *             {@link StreamImageSupplier} or {@link DirectImageSupplier}. Note that this class does not return a
 *             resource's original bytes, see {@link #getResourceBinary(String)}.
 */
@Deprecated(since = "6.1.0", forRemoval = true)
public class ResourceLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceLoader.class);

    private ResourceLoader() {
        // private constructor to avoid instances
    }

    /**
     * Get a resource from given path as base64 encoded String.
     * <p>
     * <b>Warning:</b> this encodes the result of {@link #getResourceBinary(String)}, i.e. the resource re-encoded
     * as PNG image, not its original bytes.
     *
     * @param resourcePath the path to search the resource at.
     * @return the resource re-encoded as PNG, as base64 encoded String
     * @deprecated See {@link ResourceLoader}.
     */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static String getResourceBase64(String resourcePath) {
        byte[] r;
        r = getResourceBinary(resourcePath);
        return Base64.getEncoder().encodeToString(r);
    }

    /**
     * Get a resource from a given path.
     * <p>
     * <b>Warning:</b> despite its name, this does not return the resource's original bytes. It decodes the
     * resource as an image and returns that image re-encoded as PNG. A resource that is not a readable image
     * yields <code>null</code>, and an image in another format yields different bytes than stored.
     *
     * @param resourcePath the path to search the resource at.
     * @return the resource re-encoded as PNG image, or <code>null</code> if it is not found or not a readable
     *         image
     * @deprecated See {@link ResourceLoader}.
     */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static byte[] getResourceBinary(String resourcePath) {
        byte[] ret = null;
        InputStream is = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                is = ResourceLoader.class.getResourceAsStream(resourcePath);
            }
            if (is == null) {
                LOG.warn("nJAMS: Could not find any resources under " + resourcePath);
                return ret;
            }
            BufferedImage image = ImageIO.read(is);
            ImageIO.write(image, "png", bos);
            bos.flush();
            ret = bos.toByteArray();
            bos.close();
            return ret;
        } catch (Exception e) {
            LOG.error("", e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    LOG.trace("Error", e);
                }
            }
        }
        return ret;
    }
}
