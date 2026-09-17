package com.im.njams.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Unit tests for {@link GlobalVariables}: the global-variable map and the validation of the
 * global-variable matching pattern.
 */
public class GlobalVariablesTest {

    private GlobalVariables globalVariables;

    @Before
    public void setUp() {
        globalVariables = new GlobalVariables(new Object());
    }

    @Test
    public void emptyByDefault() {
        assertTrue(globalVariables.getAll().isEmpty());
        assertNull(globalVariables.getPattern());
    }

    @Test
    public void addMergesVariables() {
        Map<String, String> first = new HashMap<>();
        first.put("a", "1");
        globalVariables.add(first);
        Map<String, String> second = new HashMap<>();
        second.put("b", "2");
        globalVariables.add(second);

        assertEquals("1", globalVariables.getAll().get("a"));
        assertEquals("2", globalVariables.getAll().get("b"));
    }

    @Test
    public void nullPatternIsAllowed() {
        globalVariables.setPattern(null);
        assertNull(globalVariables.getPattern());
    }

    @Test
    public void validPatternWithRequiredGroupsIsStored() {
        String pattern = "\\$\\{(?<full>(?<name>[a-zA-Z]+))\\}";
        globalVariables.setPattern(pattern);
        assertEquals(pattern, globalVariables.getPattern());
    }

    @Test
    public void invalidRegexThrows() {
        try {
            globalVariables.setPattern("(?<full>(?<name>[a-z]+)"); // unbalanced parenthesis
            fail("expected NjamsSdkRuntimeException for invalid regex");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    @Test
    public void patternMissingNameGroupThrows() {
        try {
            globalVariables.setPattern("(?<full>[a-z]+)");
            fail("expected NjamsSdkRuntimeException for missing 'name' group");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    @Test
    public void patternMissingFullGroupThrows() {
        try {
            globalVariables.setPattern("(?<name>[a-z]+)");
            fail("expected NjamsSdkRuntimeException for missing 'full' group");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }
}
