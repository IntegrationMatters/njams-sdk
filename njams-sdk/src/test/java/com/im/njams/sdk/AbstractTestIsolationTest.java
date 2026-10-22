package com.im.njams.sdk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Guards the isolation contract of {@link AbstractTest} itself: it starts a real {@link Njams} per test
 * instance, and JUnit builds a new instance for every test method, so without a teardown every method in all of
 * its subclasses would leave a started instance behind for the whole surefire JVM — each with its sender pool
 * threads, its flush timer and its {@code CleanTracepointsTask} registration. That is the shared-state leak
 * {@code .claude/rules/testing-conventions.md} forbids.
 *
 * <p>The method order is fixed because the second test asserts on what the first one left behind.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AbstractTestIsolationTest extends AbstractTest {

    private static Njams fromTheFirstTest;

    @Test
    public void aTestGetsAStartedInstanceOfItsOwn() {
        assertTrue("AbstractTest is expected to start the instance it creates", njams.isStarted());
        fromTheFirstTest = njams;
    }

    @Test
    public void bTheInstanceOfThePrecedingTestWasStoppedWhenThatTestEnded() {
        assertNotNull("the preceding test must have run first", fromTheFirstTest);
        assertFalse("every test's Njams must be stopped when its test ends, not left running for the JVM",
            fromTheFirstTest.isStarted());
    }
}
