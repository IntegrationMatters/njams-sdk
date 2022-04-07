/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk;

import com.im.njams.sdk.common.Path;
import org.slf4j.LoggerFactory;

public class NjamsMetadataFactory {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsMetadataFactory.class);

    private final Path clientPath;
    private final String category;
    private final String clientVersion;
    private final String sdkVersion;
    private final String machine;

    public static NjamsMetadata createMetadataFor(Path clientPath, String clientVersion, String sdkVersion, String category) {
        NjamsMetadataFactory factory = new NjamsMetadataFactory(clientPath, clientVersion, sdkVersion, category);
        return factory.create();
    }

    private NjamsMetadataFactory(Path clientPath, String clientVersion, String sdkVersion, String category) {
        this.clientPath = clientPath;
        this.clientVersion = clientVersion;
        this.sdkVersion = sdkVersion;
        this.category = category == null ? null : category.toUpperCase();
        this.machine = readMachine();
    }

    private NjamsMetadata create() {
        return new NjamsMetadata(clientPath, clientVersion, sdkVersion, machine, category);
    }

    private String readMachine() {
        String machine = "unknown";
        try {
            machine = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            LOG.debug("Error getting machine name", e);
        }
        return machine;
    }
}
