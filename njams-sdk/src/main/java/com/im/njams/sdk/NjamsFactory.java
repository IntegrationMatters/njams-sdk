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

package com.im.njams.sdk;

import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.njams.NjamsArgos;
import com.im.njams.sdk.njams.NjamsConfiguration;
import com.im.njams.sdk.njams.NjamsConfigurationFactory;
import com.im.njams.sdk.njams.NjamsFeatures;
import com.im.njams.sdk.njams.NjamsJobs;
import com.im.njams.sdk.njams.NjamsProjectMessage;
import com.im.njams.sdk.njams.NjamsReceiver;
import com.im.njams.sdk.njams.NjamsSender;
import com.im.njams.sdk.njams.NjamsSenderFactory;
import com.im.njams.sdk.njams.NjamsSettings;
import com.im.njams.sdk.njams.NjamsState;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.njams.metadata.NjamsMetadataFactory;
import com.im.njams.sdk.settings.Settings;

class NjamsFactory {
    private final NjamsSettings njamsSettings;
    private final NjamsMetadata njamsMetadata;
    private final NjamsArgos njamsArgos;
    private final NjamsState njamsState;
    private final NjamsFeatures njamsFeatures;
    private final NjamsJobs njamsJobs;
    private final NjamsSender njamsSender;
    private final NjamsConfiguration njamsConfiguration;
    private final NjamsProjectMessage njamsProjectMessage;
    private final NjamsReceiver njamsReceiver;

    NjamsFactory(Path clientPath, String category, Settings settings, String defaultClientVersion) {
        njamsSettings = new NjamsSettings(settings);
        njamsMetadata = NjamsMetadataFactory.createMetadataWith(clientPath, defaultClientVersion, category);
        njamsArgos = new NjamsArgos(settings);
        njamsState = new NjamsState();
        njamsFeatures = new NjamsFeatures();
        njamsJobs = new NjamsJobs(njamsMetadata, njamsState, njamsFeatures, settings);
        njamsSender = NjamsSenderFactory.getNjamsSender(settings, njamsMetadata);
        njamsConfiguration = NjamsConfigurationFactory.getNjamsConfiguration(njamsMetadata, njamsSender, settings);
        njamsProjectMessage = new NjamsProjectMessage(njamsMetadata, njamsFeatures, njamsConfiguration,
            njamsSender, njamsState, njamsJobs, settings);
        njamsReceiver = new NjamsReceiver(settings, njamsMetadata, njamsFeatures, njamsProjectMessage, njamsJobs,
            njamsConfiguration);
    }


    NjamsSettings getNjamsSettings() {
        return njamsSettings;
    }

    NjamsMetadata getNjamsMetadata() {
        return njamsMetadata;
    }

    NjamsArgos getNjamsArgos() {
        return njamsArgos;
    }

    NjamsState getNjamsState() {
        return njamsState;
    }

    NjamsFeatures getNjamsFeatures() {
        return njamsFeatures;
    }

    NjamsJobs getNjamsJobs() {
        return njamsJobs;
    }

    NjamsSender getNjamsSender() {
        return njamsSender;
    }

    NjamsConfiguration getNjamsConfiguration() {
        return njamsConfiguration;
    }

    NjamsProjectMessage getNjamsProjectMessage() {
        return njamsProjectMessage;
    }

    NjamsReceiver getNjamsReceiver() {
        return njamsReceiver;
    }
}
