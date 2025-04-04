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
