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
 */
package com.im.njams.sdk.model.svg;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NjamsProcessDiagramFactoryTest {

    private Settings config;

    @Before
    public void setUp() {
        config = new Settings();
    }

    @Test
    public void secureProcessingIsEnabledPerDefault(){
        Njams njams = new Njams(new Path(), "SDK", config);
        NjamsProcessDiagramFactory factory = (NjamsProcessDiagramFactory) njams.getProcessDiagramFactory();

        isProcessingWith(factory).secure();
    }

    @Test
    public void testSecureProcessingEnabled() {
        config.put(Settings.PROPERTY_DISABLE_SECURE_PROCESSING, "false");

        Njams njams = new Njams(new Path(), "SDK", config);
        NjamsProcessDiagramFactory factory = (NjamsProcessDiagramFactory) njams.getProcessDiagramFactory();

        isProcessingWith(factory).secure();
    }

    @Test
    public void testSecureProcessingDisabled() {
        config.put(Settings.PROPERTY_DISABLE_SECURE_PROCESSING, "true");

        Njams njams = new Njams(new Path(), "SDK", config);
        NjamsProcessDiagramFactory factory = (NjamsProcessDiagramFactory) njams.getProcessDiagramFactory();

        isProcessingWith(factory).notSecure();
    }

    private AssertionHelper isProcessingWith(NjamsProcessDiagramFactory factory) {
        return new AssertionHelper(factory);
    }

    private static class AssertionHelper {
        private final NjamsProcessDiagramFactory factory;

        public AssertionHelper(NjamsProcessDiagramFactory factory) {
            this.factory = factory;
        }

        public void secure() {
            Assert.assertFalse(factory.disableSecureProcessing);
        }

        public void notSecure(){
            Assert.assertTrue(factory.disableSecureProcessing);
        }
    }
}


