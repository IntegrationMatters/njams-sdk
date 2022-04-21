/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 *
 */

package com.im.njams.sdk;

import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.njams.NjamsArgos;
import com.im.njams.sdk.njams.NjamsConfiguration;
import com.im.njams.sdk.njams.NjamsFeatures;
import com.im.njams.sdk.njams.NjamsJobs;
import com.im.njams.sdk.njams.NjamsProjectMessage;
import com.im.njams.sdk.njams.NjamsReceiver;
import com.im.njams.sdk.njams.NjamsSender;
import com.im.njams.sdk.njams.NjamsSettings;
import com.im.njams.sdk.njams.NjamsState;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.settings.Settings;

/**
 * This class will be used my nJAMS to create NjamsSender, NjamsReceiver etc. for hiding instantiation detail.
 */
public interface NjamsFactory {
    NjamsFactory createNewInstanceWith(Path clientPath, String category, Settings settings, String defaultClientVersion);

    NjamsSettings getNjamsSettings();

    NjamsMetadata getNjamsMetadata();

    NjamsArgos getNjamsArgos();

    NjamsState getNjamsState();

    NjamsFeatures getNjamsFeatures();

    NjamsJobs getNjamsJobs();

    NjamsSender getNjamsSender();

    NjamsConfiguration getNjamsConfiguration();

    NjamsProjectMessage getNjamsProjectMessage();

    NjamsReceiver getNjamsReceiver();
}
