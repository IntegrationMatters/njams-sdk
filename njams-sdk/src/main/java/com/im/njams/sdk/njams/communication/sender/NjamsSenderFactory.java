/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
 *
 */
package com.im.njams.sdk.njams.communication.sender;

import com.im.njams.sdk.communication.ConcurrentSender;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

/**
 * NjamsSenderFactory creates the sender that will be used to send messages to the server.
 *
 * It will either be a shared concurrent sender or a concurrent sender for each njams Instance based on the given
 * settings.
 */
public class NjamsSenderFactory {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsSenderFactory.class);

    public static NjamsSender getNjamsSender(Settings njamsSettings, NjamsMetadata njamsMetadata) {
        Sender sender;
        if ("true".equalsIgnoreCase(njamsSettings.getProperty(Settings.PROPERTY_SHARED_COMMUNICATIONS))) {
            LOG.debug("Using shared sender pool for {}", njamsMetadata.getClientPath());
            sender = ConcurrentSender.takeSharedSender(njamsSettings);
        } else {
            LOG.debug("Creating individual sender pool for {}", njamsMetadata.getClientPath());
            sender = new ConcurrentSender(njamsSettings);
        }
        return new NjamsSender(sender);
    }
}
