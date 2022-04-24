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
import com.im.njams.sdk.njams.configuration.NjamsConfiguration;
import com.im.njams.sdk.njams.configuration.NjamsConfigurationFactory;
import com.im.njams.sdk.njams.NjamsFeatures;
import com.im.njams.sdk.njams.NjamsJobs;
import com.im.njams.sdk.njams.NjamsProjectMessage;
import com.im.njams.sdk.njams.communication.receiver.NjamsReceiver;
import com.im.njams.sdk.njams.communication.sender.NjamsSender;
import com.im.njams.sdk.njams.communication.sender.NjamsSenderFactory;
import com.im.njams.sdk.njams.NjamsSettings;
import com.im.njams.sdk.njams.NjamsState;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.njams.metadata.NjamsMetadataFactory;
import com.im.njams.sdk.settings.Settings;

/**
 * This class is used to create all the objects that are needed for {@link Njams} to work correctly.
 * You can set an instance of this to the constructor of {@link Njams}.
 *
 * It creates all the needed objects once and returns them with every get call.
 */
public class NjamsFactory {
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

    /**
     * Creates all the necessary fields with all the in between dependencies.
     * They can be retrieved by using the corresponding get operation.
     *
     * @param clientPath unique path per nJAMS instance.
     * @param category should describe the technology for the client that is used, e.g. BW5, BW6, MULE4EE
     * @param settings needed for client initialization of communication, sending intervals and sizes, etc.
     */
    public NjamsFactory(Path clientPath, String category, Settings settings){
        this(clientPath, category, settings, null);
    }

    /**
     * Creates all the necessary fields with all the in between dependencies.
     * They can be retrieved by using the corresponding get operation.
     *
     * @param clientPath unique path per nJAMS instance.
     * @param defaultClientVersion  the default version of the nJAMS client instance if no client version can be found otherwise.
     * @param category should describe the technology for the client that is used, e.g. BW5, BW6, MULE4EE
     * @param settings needed for client initialization of communication, sending intervals and sizes, etc.
     */
    public NjamsFactory(Path clientPath, String category, Settings settings, String defaultClientVersion) {
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

    public NjamsSettings getNjamsSettings() {
        return njamsSettings;
    }

    public NjamsMetadata getNjamsMetadata() {
        return njamsMetadata;
    }

    public NjamsArgos getNjamsArgos() {
        return njamsArgos;
    }

    public NjamsState getNjamsState() {
        return njamsState;
    }

    public NjamsFeatures getNjamsFeatures() {
        return njamsFeatures;
    }

    public NjamsJobs getNjamsJobs() {
        return njamsJobs;
    }

    public NjamsSender getNjamsSender() {
        return njamsSender;
    }

    public NjamsConfiguration getNjamsConfiguration() {
        return njamsConfiguration;
    }

    public NjamsProjectMessage getNjamsProjectMessage() {
        return njamsProjectMessage;
    }

    public NjamsReceiver getNjamsReceiver() {
        return njamsReceiver;
    }
}
