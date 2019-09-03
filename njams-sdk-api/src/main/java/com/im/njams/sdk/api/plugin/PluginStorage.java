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

package com.im.njams.sdk.api.plugin;

import com.im.njams.sdk.api.plugin.replay.ReplayPlugin;

/**
 * This interface provides a data structure for interacting with the plugins.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public interface PluginStorage {

    /**
     * Maps a plugin name to a {@link Plugin plugin}.
     *
     * @param pluginName  the identifier of the plugin
     * @param pluginToAdd the plugin
     */
    void putPlugin(String pluginName, Plugin pluginToAdd);

    /**
     * Returns the {@link Plugin plugin} that is mapped to the given plugin name.
     *
     * @param pluginName the identifier for the {@link Plugin plugin} to search for
     * @return the plugin that is linked to the plugin name or null, if nothing was found.
     */
    Plugin getPlugin(String pluginName);

    /**
     * Removes and returns the {@link Plugin plugin} that is mapped to the given plugin name.
     *
     * @param pluginName the identifier for the {@link Plugin plugin} to delete.
     * @return the plugin that was deleted or null, if nothing was found.
     */
    Plugin removePlugin(String pluginName);

    /**
     * Sets the {@link ReplayPlugin replay plugin} in the plugin storage.
     *
     * @param replayPluginToSet the replay plugin to set in the storage.
     */
    void setReplayPlugin(ReplayPlugin replayPluginToSet);

    /**
     * Returns the {@link ReplayPlugin replay plugin} in the plugin storage.
     *
     * @return the ReplayPlugin that is safed in the plugin storage or null, if no {@link ReplayPlugin replayPlugin}
     * is set.
     */
    ReplayPlugin getReplayPlugin();
}
