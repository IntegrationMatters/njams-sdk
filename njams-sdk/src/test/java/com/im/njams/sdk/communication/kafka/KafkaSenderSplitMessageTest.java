package com.im.njams.sdk.communication.kafka;

import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_LOGID_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_MESSAGETYPE_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_MESSAGEVERSION_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_PATH_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.im.njams.sdk.NjamsSettings;

public class KafkaSenderSplitMessageTest {

    private KafkaSender toTest = null;

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
        Properties config = new Properties();
        config.setProperty(NjamsSettings.PROPERTY_KAFKA_CLIENT_PREFIX + ProducerConfig.MAX_REQUEST_SIZE_CONFIG,
                String.valueOf(maxSize));
        toTest.init(config);
    }

    @Test
    public void testSplitData() {
        init(20); // factor 0.9 -> 18
        String testData45 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRS";
        List<String> sliced = toTest.splitData(testData45);
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

        init(20); // factor 0.9 -> 18
        List<String> sliced = toTest.splitData(testData45);
        assertEquals(euros.length * 3 / 18 + 1, sliced.size());
        assertEquals(testData45, sliced.stream().collect(Collectors.joining()));
    }

    @Test
    public void testSplitData3() {
        init(20); // factor 0.9 -> 18
        List<String> sliced = toTest.splitData("abcde");
        assertEquals(1, sliced.size());
        assertEquals("abcde", sliced.get(0));

        init(10);
        sliced = toTest.splitData("abcdefghi€qwert");
        assertEquals(2, sliced.size());
        assertEquals("abcdefghi€qwert", sliced.stream().collect(Collectors.joining()));

    }

    @Test
    public void testSplitDataNull() {
        init(20);
        List<String> sliced = toTest.splitData(null);
        assertNotNull(sliced);
        assertTrue(sliced.isEmpty());

        sliced = toTest.splitData("");
        assertNotNull(sliced);
        assertTrue(sliced.isEmpty());

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
            assertEquals("3", headers.get(KafkaSender.NJAMS_CHUNKS));
            assertEquals(String.valueOf(i + 1), headers.get(KafkaSender.NJAMS_CHUNK_NO));
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
        assertFalse(headers.containsKey(KafkaSender.NJAMS_CHUNKS));
        assertFalse(headers.containsKey(KafkaSender.NJAMS_CHUNK_NO));
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
            assertEquals("3", headers.get(KafkaSender.NJAMS_CHUNKS));
            assertEquals(String.valueOf(i + 1), headers.get(KafkaSender.NJAMS_CHUNK_NO));
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
        assertFalse(headers.containsKey(KafkaSender.NJAMS_CHUNKS));
        assertFalse(headers.containsKey(KafkaSender.NJAMS_CHUNK_NO));
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
        assertTrue(records.isEmpty());

        msg = new ProjectMessage();
        msg.setPath(">a>b>c>");
        records = toTest.splitMessage(msg, "topic", "project", null, null);
        assertNotNull(records);
        assertTrue(records.isEmpty());
        records = toTest.splitMessage(msg, "topic", "project", "", null);
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }

    private Map<String, String> getHeaders(ProducerRecord<?, ?> record) {
        Map<String, String> headers = new TreeMap<>();
        record.headers().forEach(h -> headers.put(h.key(), new String(h.value(), StandardCharsets.UTF_8)));
        return headers;
    }

}
