/* 
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * The Software shall be used for Good, not Evil.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads a resource from a given path with current thread classloader.
 *
 * @author pnientiedt
 */
public class ResourceLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceLoader.class);

    private ResourceLoader() {
        // private constructor to avoid instances
    }

    /**
     * Get a resource from given path as base64 encoded String
     *
     * @param resourcePath the path to search the resource at.
     * @return the resource as base65 encoded String
     */
    public static String getResourceBase64(String resourcePath) {
        byte[] r;
        r = getResourceBinary(resourcePath);
        return new String(Base64.encodeBase64(r));
    }

    /**
     * Get a resource from a given path.
     *
     * @param resourcePath the path to search the resource at.
     * @return the resource as byte array
     */
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
