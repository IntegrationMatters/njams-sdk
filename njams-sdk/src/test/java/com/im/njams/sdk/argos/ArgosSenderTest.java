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
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.argos;

import com.im.njams.sdk.settings.Settings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.Properties;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class ArgosSenderTest {

    private static final String ADDRESS = "127.0.0.1";
    private static final int PORT = 6450;

    private ArgosSender argosSender;

    @Before
    public void init() {
        Settings settings = new Settings();
        //Argos relevant properties
        settings.put(ArgosSender.NJAMS_SUBAGENT_HOST, ADDRESS);
        settings.put(ArgosSender.NJAMS_SUBAGENT_PORT, Integer.toString(PORT));
        settings.put(ArgosSender.NJAMS_SUBAGENT_ENABLED, "true");

        this.argosSender = spy(new ArgosSender(settings));
        argosSender.start();
    }

    @After
    public void tearDown() {
        argosSender.close();
    }

    @Test
    public void addCollector() throws InterruptedException {
        ArgosCollector collector = mock(ArgosCollector.class);
        ArgosComponent comp = mock(ArgosComponent.class);

        when(collector.getArgosComponent()).thenReturn(comp);

        verify(collector, times(0)).collect();

        argosSender.addArgosCollector(collector);

        Thread.sleep(15000);

        verify(collector).collect();
    }
}