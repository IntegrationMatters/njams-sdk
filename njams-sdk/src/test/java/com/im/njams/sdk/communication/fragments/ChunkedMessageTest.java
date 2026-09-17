package com.im.njams.sdk.communication.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for {@link ChunkedMessage}: chunk collection, completeness detection, bounds checking
 * and the value semantics used by {@link GenericChunkAssembly}.
 */
public class ChunkedMessageTest {

    private static Map<String, String> headers() {
        Map<String, String> m = new HashMap<>();
        m.put("h", "v");
        return m;
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsZeroChunks() {
        new ChunkedMessage(headers(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNegativeChunks() {
        new ChunkedMessage(headers(), -1);
    }

    @Test
    public void sizeReflectsExpectedChunks() {
        assertEquals(3, new ChunkedMessage(headers(), 3).size());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void addBelowRangeThrows() {
        new ChunkedMessage(headers(), 2).add(0, "x");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void addAboveRangeThrows() {
        new ChunkedMessage(headers(), 2).add(3, "x");
    }

    @Test
    public void incompleteAddReturnsNullCompleteReturnsMessage() {
        Map<String, String> headers = headers();
        ChunkedMessage cm = new ChunkedMessage(headers, 2);
        assertNull("not complete after first chunk", cm.add(1, "a"));
        RawMessage result = cm.add(2, "b");
        assertEquals("ab", result.getBody());
        assertSame(headers, result.getHeaders());
    }

    @Test
    public void chunksAreJoinedByPositionRegardlessOfArrivalOrder() {
        ChunkedMessage cm = new ChunkedMessage(headers(), 3);
        assertNull(cm.add(3, "c"));
        assertNull(cm.add(1, "a"));
        RawMessage result = cm.add(2, "b");
        assertEquals("abc", result.getBody());
    }

    @Test
    public void isNewReturnsTrueOnlyOnce() {
        ChunkedMessage cm = new ChunkedMessage(headers(), 1);
        assertTrue(cm.isNew());
        assertFalse(cm.isNew());
    }

    @Test
    public void equalsAndHashCodeUseHeadersAndChunks() {
        ChunkedMessage a = new ChunkedMessage(headers(), 2);
        ChunkedMessage b = new ChunkedMessage(headers(), 2);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        a.add(1, "x");
        assertNotEquals(a, b);
    }
}
