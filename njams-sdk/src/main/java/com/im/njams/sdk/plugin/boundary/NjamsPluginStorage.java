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

package com.im.njams.sdk.plugin.boundary;

import com.im.njams.sdk.api.plugin.Plugin;
import com.im.njams.sdk.api.plugin.PluginStorage;
import com.im.njams.sdk.api.plugin.replay.ReplayPlugin;
import com.im.njams.sdk.feature.NjamsFeatures;

import java.util.HashMap;
import java.util.Map;

public class NjamsPluginStorage implements PluginStorage {

    private Map<String, Plugin> availablePlugins = new HashMap<>();

    private NjamsFeatures features;

    public NjamsPluginStorage(NjamsFeatures features) {
        this.features = features;
    }

    @Override
    public void putPlugin(String pluginName, Plugin pluginToAdd) {
        availablePlugins.put(pluginName, pluginToAdd);
    }

    @Override
    public Plugin getPlugin(String pluginName) {
        return availablePlugins.get(pluginName);
    }

    @Override
    public Plugin removePlugin(String pluginName) {
        return availablePlugins.remove(pluginName);
    }

    @Override
    public void setReplayPlugin(ReplayPlugin replayPluginToSet) {
        putPlugin(ReplayPlugin.REPLAY_FEATURE, replayPluginToSet);
    }

    @Override
    public ReplayPlugin getReplayPlugin() {
        return (ReplayPlugin) getPlugin(ReplayPlugin.REPLAY_FEATURE);
    }

}
