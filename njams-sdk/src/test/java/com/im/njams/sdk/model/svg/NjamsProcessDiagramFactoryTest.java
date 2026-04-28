/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
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
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.model.svg;

import static org.mockito.Mockito.when;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.Settings;

public class NjamsProcessDiagramFactoryTest {

    @Test
    public void testSecureProcessingEnabled() {
        Settings config = new Settings();
        config.put(NjamsSettings.PROPERTY_DISABLE_SECURE_PROCESSING, "false");

        Njams njams = Mockito.mock(Njams.class);
        when(njams.getSettings()).thenReturn(config);
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(njams);

        Assert.assertFalse(factory.disableSecureProcessing);
    }

    @Test
    public void testSecureProcessingDisabled() {
        Settings config = new Settings();
        config.put(NjamsSettings.PROPERTY_DISABLE_SECURE_PROCESSING, "true");

        Njams njams = Mockito.mock(Njams.class);
        when(njams.getSettings()).thenReturn(config);
        NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(njams);

        Assert.assertTrue(factory.disableSecureProcessing);
    }
}
