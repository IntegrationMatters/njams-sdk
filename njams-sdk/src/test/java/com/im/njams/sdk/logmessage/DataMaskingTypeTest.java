package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.regex.PatternSyntaxException;

import org.junit.Test;

/**
 * Unit tests for {@link DataMaskingType}: it holds a named regex both as a {@link String} and as a
 * compiled {@link java.util.regex.Pattern}.
 */
public class DataMaskingTypeTest {

    @Test
    public void storesNameAndCompilesRegex() {
        DataMaskingType type = new DataMaskingType("creditcard", "\\d{16}");

        assertEquals("creditcard", type.getNameOfPattern());
        assertEquals("\\d{16}", type.getRegex());
        assertNotNull(type.getPattern());
        assertEquals("\\d{16}", type.getPattern().pattern());
        // the compiled pattern actually matches
        assertTrue(type.getPattern().matcher("1234567812345678").matches());
    }

    @Test
    public void toStringContainsNameAndRegex() {
        DataMaskingType type = new DataMaskingType("name", "abc");
        String s = type.toString();
        assertTrue(s.contains("name"));
        assertTrue(s.contains("abc"));
    }

    @Test(expected = PatternSyntaxException.class)
    public void invalidRegexThrows() {
        new DataMaskingType("broken", "[unclosed");
    }
}
