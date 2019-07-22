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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NjamsReplayPluginTest {

    private NjamsReplayPlugin njamsReplayPlugin;

    private NjamsFeatures featuresMock = mock(NjamsFeatures.class);

    private ReplayHandler replayHandlerMock = mock(ReplayHandler.class);


    @Before
    public void initialize(){
        njamsReplayPlugin = spy(new NjamsReplayPlugin(featuresMock));
    }

    @Test
    public void replayItemIsNotSetAfterConstruction(){
        assertNull(njamsReplayPlugin.getPluginItem());
    }

    @Test
    public void ifReplayHandlerIsNotSetThenIsReplayHandlerSetIsFalse(){
        njamsReplayPlugin.setPluginItem(null);
        assertNull(njamsReplayPlugin.getPluginItem());
        assertFalse(njamsReplayPlugin.isReplayHandlerSet());
    }

    @Test
    public void ifReplayHandlerIsSetThenIsReplayHandlerSetIsTrue(){
        njamsReplayPlugin.setPluginItem(replayHandlerMock);
        ReplayHandler actualReplayHandlerSet = njamsReplayPlugin.getPluginItem();

        assertNotNull(actualReplayHandlerSet);
        assertEquals(replayHandlerMock, actualReplayHandlerSet);
        assertTrue(njamsReplayPlugin.isReplayHandlerSet());
    }

    @Test
    public void whenReplayHandlerIsSetTheFeatureIsAdded(){
        njamsReplayPlugin.setPluginItem(replayHandlerMock);
        verify(featuresMock).addFeature(njamsReplayPlugin);
        verify(featuresMock, times(0)).removeFeature(any());
    }

    @Test
    public void whenReplayHandlerSetToNullTheFeatureIsRemoved(){
        njamsReplayPlugin.setPluginItem(replayHandlerMock);
        verify(featuresMock).addFeature(njamsReplayPlugin);
        verify(featuresMock, times(0)).removeFeature(any());
    }

    @Test
    public void getFeatureReturnsTheStringOfTheApi(){
        assertEquals(ReplayPlugin.REPLAY_FEATURE, njamsReplayPlugin.getFeature());
    }
}