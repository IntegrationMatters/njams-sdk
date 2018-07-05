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
package com.im.njams.sdk.model.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Image supplier implementation that uses an URL for loading the image.
 * @author cwinkler
 *
 */
public class URLImageSupplier extends ImageSupplier {

    private static final Logger LOG = LoggerFactory.getLogger(URLImageSupplier.class);

    private final URL url;

    /**
     * @param name The name that is used to access the image.
     * @param url The URL that is used to load the image.
     */
    public URLImageSupplier(String name, URL url) {
        super(name);
        this.url = Objects.requireNonNull(url);
    }

    @Override
    public String getBase64Image() {
        LOG.debug("Get image {} from {}", getName(), url);
        byte[] bin = getResourceBinary(url);
        if (bin != null && bin.length > 0) {
            return encodeBase64(bin);
        }
        return null;
    }

    private static byte[] getResourceBinary(URL url) {
        byte[] ret = null;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = url.openStream()) {
            if (is == null) {
                LOG.warn("nJAMS: Could not find any resources under " + url);
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
        }
        return ret;
    }

}
