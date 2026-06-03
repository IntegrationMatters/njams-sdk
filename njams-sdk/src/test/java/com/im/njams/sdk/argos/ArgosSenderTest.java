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
package com.im.njams.sdk.argos;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.Settings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class ArgosSenderTest {

    private static final String ADDRESS = "127.0.0.1";
    private static final int PORT = 6450;

    private ArgosSender argosSender;

    @Before
    public void init() {
        Settings settings = new Settings();
        //Argos relevant properties
        settings.put(NjamsSettings.PROPERTY_ARGOS_SUBAGENT_HOST, ADDRESS);
        settings.put(NjamsSettings.PROPERTY_ARGOS_SUBAGENT_PORT, Integer.toString(PORT));
        settings.put(NjamsSettings.PROPERTY_ARGOS_SUBAGENT_ENABLED, "true");

        argosSender = ArgosSender.getInstance();
        argosSender.init(settings);
        argosSender.start();
    }

    @After
    public void tearDown() {
        argosSender.close();
    }

    @Test
    public void initWithDeprecatedKeys() throws Exception {
        ArgosSender sender = new ArgosSender();
        Settings settings = new Settings();
        // use the old .client. key names instead of the current .sdk. ones
        settings.put("njams.client.subagent.host", "legacy-host");
        settings.put("njams.client.subagent.port", "4711");
        settings.put("njams.client.subagent.enabled", "true");

        sender.init(settings);

        // verify via reflection
        java.lang.reflect.Field hostField = ArgosSender.class.getDeclaredField("host");
        hostField.setAccessible(true);
        java.lang.reflect.Field portField = ArgosSender.class.getDeclaredField("port");
        portField.setAccessible(true);
        java.lang.reflect.Field enabledField = ArgosSender.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);

        assertEquals("legacy-host", hostField.get(sender));
        assertEquals(4711, portField.get(sender));
        assertTrue((Boolean) enabledField.get(sender));
    }

    @Test
    public void addCollector() throws InterruptedException {
        ArgosCollector collector = mock(ArgosCollector.class);
        ArgosComponent comp = mock(ArgosComponent.class);

        when(collector.getArgosComponent()).thenReturn(comp);

        verify(collector, times(0)).collectAll();

        argosSender.addArgosCollector(collector);

        Thread.sleep(15000);

        verify(collector).collectAll();
    }

    @Test
    public void doesNotSendNullMetricElement() throws Exception {
        ArgosSender sender = new ArgosSender();
        DatagramSocket mockSocket = mock(DatagramSocket.class);
        ArgosComponent component = new ArgosComponent("id", "name", "container", "measurement", "type");
        ArgosMetric valid = new TestMetric(component);

        injectSendingState(sender, mockSocket, component,
            new TestCollector(component, Arrays.asList(valid, null)));

        invokePublishData(sender);

        // the null element must be skipped; only the valid metric is sent
        verify(mockSocket, times(1)).send(any(DatagramPacket.class));
    }

    @Test
    public void doesNotSendEmptySerializedMetric() throws Exception {
        ArgosSender sender = new ArgosSender();
        DatagramSocket mockSocket = mock(DatagramSocket.class);
        ArgosComponent component = new ArgosComponent("id", "name", "container", "measurement", "type");
        ArgosMetric valid = new TestMetric(component);
        ArgosMetric unserializable = new ThrowingMetric(component);

        injectSendingState(sender, mockSocket, component,
            new TestCollector(component, Arrays.asList(valid, unserializable)));

        invokePublishData(sender);

        // the metric whose serialization yields empty data must be skipped
        verify(mockSocket, times(1)).send(any(DatagramPacket.class));
    }

    @SuppressWarnings("unchecked")
    private void injectSendingState(ArgosSender sender, DatagramSocket socket, ArgosComponent component,
        ArgosMultiCollector<ArgosMetric> collector) throws Exception {
        setField(sender, "socket", socket);
        setField(sender, "ip", InetAddress.getByName(ADDRESS));
        setField(sender, "port", PORT);
        Field collectorsField = ArgosSender.class.getDeclaredField("argosCollectors");
        collectorsField.setAccessible(true);
        Map<ArgosComponent, ArgosMultiCollector> collectors =
            (Map<ArgosComponent, ArgosMultiCollector>) collectorsField.get(sender);
        collectors.put(component, collector);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = ArgosSender.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void invokePublishData(ArgosSender sender) throws Exception {
        Method method = ArgosSender.class.getDeclaredMethod("publishData");
        method.setAccessible(true);
        method.invoke(sender);
    }

    private static class TestMetric extends ArgosMetric {
        TestMetric(ArgosComponent component) {
            super(component);
        }
    }

    private static class ThrowingMetric extends ArgosMetric {
        ThrowingMetric(ArgosComponent component) {
            super(component);
        }

        public int getBoom() {
            throw new IllegalStateException("cannot serialize");
        }
    }

    private static class TestCollector extends ArgosMultiCollector<ArgosMetric> {
        private final Collection<ArgosMetric> metrics;

        TestCollector(ArgosComponent component, Collection<ArgosMetric> metrics) {
            super(component);
            this.metrics = metrics;
        }

        @Override
        protected Collection<ArgosMetric> createAll() {
            return metrics;
        }
    }
}