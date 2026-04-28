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
package com.im.njams.sdk.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.jms.FailingJmsFactory;
import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.communication.jms.factory.JndiJmsFactory;

public class ServiceLoaderSupportTest {
    private ServiceLoaderSupport<JmsFactory> toTest = null;

    @Before
    public void setUp() {
        // There is a FailingJmsFactory test class that fails to initialize during SPI lookup
        // However, there is no test for the failing instance since the internal iterator skips that
        // entry except for the very first access. At least there is a DEBUG message printed once
        // for that case.
        toTest = new ServiceLoaderSupport<>(JmsFactory.class);
    }

    @Test
    public void testIterator() {
        for (JmsFactory f : toTest) {
            assertNotNull(f);
        }
    }

    @Test
    public void testStream() {
        toTest.stream().forEach(Assert::assertNotNull);
    }

    @Test
    public void testGetAll() {
        Collection<JmsFactory> all = toTest.getAll();
        assertNotNull(all);
        assertEquals(4, all.size());
        for (JmsFactory f : all) {
            assertNotNull(f);
        }
    }

    @Test
    public void testFind() {
        JmsFactory f = toTest.find(j -> j.getName().equalsIgnoreCase("JNDI"));
        assertTrue(f instanceof JndiJmsFactory);
        f = toTest.find(j -> false);
        assertNull(f);
    }

    @Test
    public void testFindByClassName() {
        JmsFactory f = toTest.findByClassName(JndiJmsFactory.class.getName());
        assertTrue(f instanceof JndiJmsFactory);
        f = toTest.findByClassName(JndiJmsFactory.class.getSimpleName());
        assertTrue(f instanceof JndiJmsFactory);
        f = toTest.findByClassName("xxx");
        assertNull(f);
    }

    public void testFindByClass() {
        JndiJmsFactory f = toTest.findByClass(JndiJmsFactory.class);
        assertTrue(f instanceof JndiJmsFactory);
        FailingJmsFactory g = toTest.findByClass(FailingJmsFactory.class);
        assertNull(g);
    }

}
