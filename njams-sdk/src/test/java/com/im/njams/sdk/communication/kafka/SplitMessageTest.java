package com.im.njams.sdk.communication.kafka;

import static com.im.njams.sdk.communication.MessageHeaders.*;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.SplitSupport;

public class SplitMessageTest {

    private KafkaSender toTest = null;
    private SplitSupport splitSupport = null;

    @Before
    public void setUp() throws Exception {
        toTest = new KafkaSender() {
            @Override
            public void connect() {
                // nothing
            }
        };
    }

    private void init(int maxSize) {
        Properties p = buildProps(maxSize);
        toTest.init(p);
        splitSupport = new SplitSupport(p, -1);
    }

    private Properties buildProps(int maxSize) {

        Properties config = new Properties();
        config.setProperty(NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, String.valueOf(maxSize));
        config.setProperty(SplitSupport.TESTING_NO_LIMIT_CHECKS, "true");
        config.setProperty(NjamsSettings.PROPERTY_COMMUNICATION, KafkaConstants.COMMUNICATION_NAME);
        return config;
    }

    @Test
    public void testSplitData() {
        init(20);
        String testData45 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRS";
        List<String> sliced = splitSupport.splitData(testData45);
        assertEquals(3, sliced.size());
        assertEquals(testData45, sliced.stream().collect(Collectors.joining()));
    }

    @Test
    public void testSplitData2() {
        // € is 3 bytes in UTF_8 !
        assertEquals(3, "€".getBytes(StandardCharsets.UTF_8).length);

        char[] euros = new char[45];
        Arrays.fill(euros, '€');
        String testData45 = String.copyValueOf(euros);
        assertEquals(45, testData45.length());

        init(20);
        List<String> sliced = splitSupport.splitData(testData45);
        assertEquals(8, sliced.size());
        assertEquals(testData45, sliced.stream().collect(Collectors.joining()));
    }

    @Test
    public void testSplitData3() {
        init(20);
        List<String> sliced = splitSupport.splitData("abcde");
        assertEquals(1, sliced.size());
        assertEquals("abcde", sliced.get(0));

        init(10);
        sliced = splitSupport.splitData("abcdefghi€qwert");
        assertEquals(2, sliced.size());
        assertEquals("abcdefghi€qwert", sliced.stream().collect(Collectors.joining()));

    }

    @Test
    public void testSplitDataNull() {
        init(20);
        List<String> sliced = splitSupport.splitData(null);
        assertNotNull(sliced);
        assertTrue(sliced.isEmpty());

        sliced = splitSupport.splitData("");
        assertNotNull(sliced);
        assertTrue(sliced.contains(""));

    }

    @Test
    public void testSplitMessage() {
        String testData45 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRS";
        init(20);
        LogMessage msg = new LogMessage();
        msg.setLogId("4711");
        msg.setPath(">a>b>c>");
        List<ProducerRecord<String, String>> records = toTest.splitMessage(msg, "topic", "event", testData45, null);
        assertEquals(3, records.size());
        String data = "";
        for (int i = 0; i < records.size(); i++) {
            ProducerRecord<String, String> record = records.get(i);
            assertEquals("4711", record.key());
            assertEquals("topic", record.topic());
            data += record.value();

            Map<String, String> headers = getHeaders(record);
            assertEquals(">a>b>c>", headers.get(NJAMS_PATH_HEADER));
            assertEquals("4711", headers.get(NJAMS_LOGID_HEADER));
            assertEquals("event", headers.get(NJAMS_MESSAGETYPE_HEADER));
            assertEquals("V4", headers.get(NJAMS_MESSAGEVERSION_HEADER));
            assertEquals("3", headers.get(NJAMS_CHUNKS_HEADER));
            assertEquals(String.valueOf(i + 1), headers.get(NJAMS_CHUNK_NO_HEADER));
        }
        assertEquals(testData45, data);

    }

    @Test
    public void testNoSplitMessage() {
        String testData15 = "abcdefghijklmno";
        init(20);
        LogMessage msg = new LogMessage();
        msg.setLogId("4711");
        msg.setPath(">a>b>c>");
        List<ProducerRecord<String, String>> records = toTest.splitMessage(msg, "topic", "event", testData15, null);
        assertEquals(1, records.size());
        ProducerRecord<String, String> record = records.get(0);
        assertEquals("4711", record.key());
        assertEquals("topic", record.topic());

        Map<String, String> headers = getHeaders(record);
        assertEquals(">a>b>c>", headers.get(NJAMS_PATH_HEADER));
        assertEquals("4711", headers.get(NJAMS_LOGID_HEADER));
        assertEquals("event", headers.get(NJAMS_MESSAGETYPE_HEADER));
        assertEquals("V4", headers.get(NJAMS_MESSAGEVERSION_HEADER));
        assertFalse(headers.containsKey(NJAMS_CHUNKS_HEADER));
        assertFalse(headers.containsKey(NJAMS_CHUNK_NO_HEADER));
        assertEquals(testData15, record.value());
    }

    @Test
    public void testSplitMessage2() {
        String testData45 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRS";
        init(20);
        ProjectMessage msg = new ProjectMessage();
        msg.setPath(">a>b>c>");
        List<ProducerRecord<String, String>> records = toTest.splitMessage(msg, "topic", "project", testData45, null);
        assertEquals(3, records.size());
        String data = "";
        String key = null;
        for (int i = 0; i < records.size(); i++) {
            ProducerRecord<String, String> record = records.get(i);
            // same, yet random key for all messages
            assertNotNull(record.key());
            assertTrue(key == null || key.equals(record.key()));
            key = record.key();
            assertEquals("topic", record.topic());
            data += record.value();

            Map<String, String> headers = getHeaders(record);
            assertEquals(">a>b>c>", headers.get(NJAMS_PATH_HEADER));
            assertEquals("project", headers.get(NJAMS_MESSAGETYPE_HEADER));
            assertEquals("V4", headers.get(NJAMS_MESSAGEVERSION_HEADER));
            assertEquals("3", headers.get(NJAMS_CHUNKS_HEADER));
            assertEquals(String.valueOf(i + 1), headers.get(NJAMS_CHUNK_NO_HEADER));
            assertNull(headers.get(NJAMS_LOGID_HEADER));
        }
        assertEquals(testData45, data);

    }

    @Test
    public void testNoSplitMessage2() {
        String testData15 = "abcdefghijklmno";
        init(20);
        ProjectMessage msg = new ProjectMessage();
        msg.setPath(">a>b>c>");
        List<ProducerRecord<String, String>> records = toTest.splitMessage(msg, "topic", "project", testData15, null);
        assertEquals(1, records.size());
        ProducerRecord<String, String> record = records.get(0);
        assertNull(record.key());
        assertEquals("topic", record.topic());

        Map<String, String> headers = getHeaders(record);
        assertEquals(">a>b>c>", headers.get(NJAMS_PATH_HEADER));
        assertEquals("project", headers.get(NJAMS_MESSAGETYPE_HEADER));
        assertEquals("V4", headers.get(NJAMS_MESSAGEVERSION_HEADER));
        assertFalse(headers.containsKey(NJAMS_CHUNKS_HEADER));
        assertFalse(headers.containsKey(NJAMS_CHUNK_NO_HEADER));
        assertNull(headers.get(NJAMS_LOGID_HEADER));
        assertEquals(testData15, record.value());

    }

    @Test
    public void testNoSplitMessageNull() {
        init(20);
        CommonMessage msg = new LogMessage();
        msg.setPath(">a>b>c>");
        List<ProducerRecord<String, String>> records = toTest.splitMessage(msg, "topic", "event", null, null);
        assertNotNull(records);
        assertTrue(records.isEmpty());
        records = toTest.splitMessage(msg, "topic", "event", "", null);
        assertNotNull(records);
        assertEquals(1, records.size());

        msg = new ProjectMessage();
        msg.setPath(">a>b>c>");
        records = toTest.splitMessage(msg, "topic", "project", null, null);
        assertNotNull(records);
        assertTrue(records.isEmpty());
        records = toTest.splitMessage(msg, "topic", "project", "", null);
        assertNotNull(records);
        assertEquals(1, records.size());
    }

    private Map<String, String> getHeaders(ProducerRecord<?, ?> record) {
        Map<String, String> headers = new TreeMap<>();
        record.headers().forEach(h -> headers.put(h.key(), new String(h.value(), StandardCharsets.UTF_8)));
        return headers;
    }

}
