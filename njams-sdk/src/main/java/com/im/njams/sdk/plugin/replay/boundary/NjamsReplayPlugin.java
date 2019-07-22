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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.plugin.replay.boundary;

import com.im.njams.sdk.api.plugin.replay.ReplayHandler;
import com.im.njams.sdk.api.plugin.replay.ReplayPlugin;
import com.im.njams.sdk.feature.NjamsFeatures;
import org.slf4j.LoggerFactory;

public class NjamsReplayPlugin implements ReplayPlugin {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsReplayPlugin.class);

    private NjamsFeatures features;

    private ReplayHandler replayHandler;

    public NjamsReplayPlugin(NjamsFeatures features){
        this.features = features;
    }

    @Override
    public void setPluginItem(ReplayHandler replayHandlerToSet) {
        if (replayHandlerToSet == null) {
            LOG.info("ReplayHandler has been removed successfully.");
            features.removeFeature(this);
        } else {
            LOG.info("ReplayHandler has been set successfully.");
            features.addFeature(this);
        }
        replayHandler = replayHandlerToSet;
    }

    @Override
    public ReplayHandler getPluginItem() {
        return replayHandler;
    }

    @Override
    public boolean isReplayHandlerSet() {
        return replayHandler != null;
    }
}
