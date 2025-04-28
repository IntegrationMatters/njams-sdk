package com.im.njams.sdk.communication.fragments;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.MessageHeaders;
import com.im.njams.sdk.communication.fragments.SplitSupport.Range;
import com.im.njams.sdk.communication.fragments.SplitSupport.SplitIterator;
import com.im.njams.sdk.communication.http.HttpSender;
import com.im.njams.sdk.communication.jms.JmsSender;

public class SplitSupportTest {
    private SplitSupport splitSupport = null;

    private void init(int maxSize) {
        init(maxSize, false);
    }

    private void init(int maxSize, boolean http) {
        Properties p = buildProps(maxSize, http);
        splitSupport = new SplitSupport(p, -1);
    }

    private Properties buildProps(int maxSize, boolean http) {
        Properties config = new Properties();
        config.setProperty(NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, String.valueOf(maxSize));
        config.setProperty(NjamsSettings.PROPERTY_COMMUNICATION, http ? HttpSender.NAME : JmsSender.COMMUNICATION_NAME);
        config.setProperty(SplitSupport.TESTING_NO_LIMIT_CHECKS, "true");
        return config;
    }

    private void assertEqual(String expected, Iterator<String> it, int chunksSize) {
        StringBuilder sb = new StringBuilder();
        while (it.hasNext()) {
            String s = it.next();
            assertTrue(s.getBytes(StandardCharsets.UTF_8).length <= chunksSize);
            sb.append(s);
        }
        assertEquals(expected, sb.toString());
    }

    private void assertEqual(String expected, List<Range> idx, int chunksSize) {
        StringBuilder sb = new StringBuilder();
        for (Range e : idx) {
            String s = expected.substring(e.from(), e.to());
            assertTrue(s.getBytes(StandardCharsets.UTF_8).length <= chunksSize);
            sb.append(s);
        }
        assertEquals(expected, sb.toString());
    }

    private void initWithLimits(int sdk, int tech) {
        Properties config = new Properties();
        config.setProperty(NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, String.valueOf(sdk));
        splitSupport = new SplitSupport(config, tech);
    }

    @Test
    public void testLimits() {
        initWithLimits(20_000, 0);
        assertTrue(splitSupport.isSplitting());
        assertEquals(20_000, splitSupport.getMaxMessageSize());
        initWithLimits(5_000, 0);
        assertTrue(splitSupport.isSplitting());
        assertEquals(10_240, splitSupport.getMaxMessageSize());
        initWithLimits(0, 0);
        assertFalse(splitSupport.isSplitting());
        assertEquals(0, splitSupport.getMaxMessageSize());
        initWithLimits(-1, 0);
        assertFalse(splitSupport.isSplitting());
        assertEquals(0, splitSupport.getMaxMessageSize());
    }

    @Test
    public void testTechLimits() {
        initWithLimits(20_000, 50_000);
        assertTrue(splitSupport.isSplitting());
        assertEquals(20_000, splitSupport.getMaxMessageSize());
        initWithLimits(70_000, 50_000);
        assertTrue(splitSupport.isSplitting());
        assertEquals(50_000, splitSupport.getMaxMessageSize());
        initWithLimits(5_000, 50_000);
        assertTrue(splitSupport.isSplitting());
        assertEquals(10_240, splitSupport.getMaxMessageSize());
        initWithLimits(0, 50_000);
        assertTrue(splitSupport.isSplitting());
        assertEquals(50_000, splitSupport.getMaxMessageSize());
        initWithLimits(-1, 50_000);
        assertTrue(splitSupport.isSplitting());
        assertEquals(50_000, splitSupport.getMaxMessageSize());
        try {
            initWithLimits(1_000, 1_000);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGetSplitIndexes1() {
        init(20);
        String testData45 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRS";
        List<Range> idx = splitSupport.getSplitIndexes(testData45);
        assertEquals(3, idx.size());
        assertEqual(testData45, idx, 20);
    }

    @Test
    public void testtSplitIterator1() {
        init(20);
        String testData45 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRS";
        SplitIterator it = splitSupport.iterator(testData45);
        assertEquals(3, it.size());
        assertEqual(testData45, it, 20);
    }

    @Test
    public void testtSplitIteratorIndex() {
        init(20);
        String testData45 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRS";
        SplitIterator it = splitSupport.iterator(testData45);
        // test iterator index
        int i = 0;
        String s = "";
        assertEquals(-1, it.currentIndex());
        while (it.hasNext()) {
            s += it.next();
            assertNotNull(s);
            assertEquals(i++, it.currentIndex());
        }
        assertEquals(testData45, s);
    }

    @Test
    public void testGetSplitIndexes2() {
        // € is 3 bytes in UTF_8 !
        assertEquals(3, "€".getBytes(StandardCharsets.UTF_8).length);

        char[] euros = new char[45];
        Arrays.fill(euros, '€');
        String testData45 = String.copyValueOf(euros);
        assertEquals(45, testData45.length());

        init(20);
        List<Range> idx = splitSupport.getSplitIndexes(testData45);
        assertEquals(8, idx.size());
        assertEqual(testData45, idx, 20);
    }

    @Test
    public void testtSplitIterator2() {
        // € is 3 bytes in UTF_8 !
        assertEquals(3, "€".getBytes(StandardCharsets.UTF_8).length);

        char[] euros = new char[45];
        Arrays.fill(euros, '€');
        String testData45 = String.copyValueOf(euros);
        assertEquals(45, testData45.length());

        init(20);
        SplitIterator it = splitSupport.iterator(testData45);
        assertEquals(8, it.size());
        assertEqual(testData45, it, 20);
    }

    @Test
    public void testGetSplitIndexes3() {
        init(20);
        String test = "abcde";
        List<Range> idx = splitSupport.getSplitIndexes(test);
        assertEquals(1, idx.size());
        assertEqual("abcde", idx, 20);

        init(10);
        test = "abcdefghi€qwert";
        idx = splitSupport.getSplitIndexes(test);
        assertEquals(2, idx.size());
        assertEqual("abcdefghi€qwert", idx, 10);

    }

    @Test
    public void testtSplitIterator3() {
        init(20);
        SplitIterator it = splitSupport.iterator("abcde");
        assertEquals(1, it.size());
        assertEquals("abcde", it.next());
        assertFalse(it.hasNext());

        init(10);
        it = splitSupport.iterator("abcdefghi€qwert");
        assertEquals(2, it.size());
        assertEqual("abcdefghi€qwert", it, 10);

    }

    @Test
    public void testGetSplitIndexesNull() {
        init(20);
        List<Range> idx = splitSupport.getSplitIndexes(null);
        assertNotNull(idx);
        assertTrue(idx.isEmpty());

        idx = splitSupport.getSplitIndexes("");
        assertNotNull(idx);
        assertEqual("", idx, 20);

    }

    @Test
    public void testtSplitIteratorNull() {
        init(20);
        SplitIterator it = splitSupport.iterator(null);
        assertNotNull(it);
        assertFalse(it.hasNext());

        it = splitSupport.iterator("");
        assertNotNull(it);
        assertEquals("", it.next());
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("NoSuchElementException expected");
        } catch (NoSuchElementException e) {
            // expected
        }

    }

    @Test
    public void testSurrogate() {
        String s = "😀"; // is actually two characters long, and is encoded as 4 bytes in UTF-8
        assertEquals(2, s.length());
        assertEquals(4, s.getBytes(StandardCharsets.UTF_8).length);

        String testData45 = "";
        for (int i = 0; i < 45; i++) {
            testData45 += "😀";
        }
        assertEquals(45 * 2, testData45.length()); // even two characters are needed for this
        assertEquals(45 * 4, testData45.getBytes(StandardCharsets.UTF_8).length);

        init(20);
        SplitIterator it = splitSupport.iterator(testData45);
        assertEquals(9, it.size());
        assertEqual(testData45, it, 20);
    }

    @Test
    public void testSurrogate2() {
        String testData10 = "😀😀😀😀😀😀😀😀😀😀";
        init(7); // this breaks the two-character symbol, i.e., just one 😀 fits into each chunk, leaving 3 bytes unused
        SplitIterator it = splitSupport.iterator(testData10);
        assertEquals(10, it.size());
        assertEqual(testData10, it, 7);
    }

    @Test
    public void testAddChunkHeadersJMS() {
        init(20, false);
        Map<String, String> headers = new HashMap<>();
        splitSupport.addChunkHeaders(headers::put, 5, 10, "uuid");
        assertEquals("6", headers.get(MessageHeaders.NJAMS_CHUNK_NO_HEADER));
        assertEquals("10", headers.get(MessageHeaders.NJAMS_CHUNKS_HEADER));
        assertEquals("uuid", headers.get(MessageHeaders.NJAMS_CHUNK_MESSAGE_KEY_HEADER));

        headers = new HashMap<>();
        splitSupport.addChunkHeaders(headers::put, 5, 1, "uuid");
        assertTrue(headers.isEmpty());

    }

    @Test
    public void testAddChunkHeadersHttp() {
        init(20, true);
        Map<String, String> headers = new HashMap<>();
        splitSupport.addChunkHeaders(headers::put, 5, 10, "uuid");
        assertEquals("6", headers.get(MessageHeaders.NJAMS_CHUNK_NO_HTTP_HEADER));
        assertEquals("10", headers.get(MessageHeaders.NJAMS_CHUNKS_HTTP_HEADER));
        assertEquals("uuid", headers.get(MessageHeaders.NJAMS_CHUNK_MESSAGE_KEY_HTTP_HEADER));

        headers = new HashMap<>();
        splitSupport.addChunkHeaders(headers::put, 5, 1, "uuid");
        assertTrue(headers.isEmpty());

    }

    @Test
    public void testIterationLoopIndex() {
        init(20);
        String testData45 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRS";
        SplitIterator it = splitSupport.iterator(testData45);
        int i = 0;
        while (it.hasNext()) {
            verify(it.next(), i++, it.currentIndex());
        }

    }

    private void verify(String chunk, int expectedIndex, int isIndex) {
        assertNotNull(chunk);
        assertEquals(expectedIndex, isIndex);
    }

}
