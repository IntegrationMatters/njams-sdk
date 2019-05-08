/*
 */

/*
 */
package com.im.njams.sdk.communication.jms;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This class tests if the JmsSender works correctly.
 * 
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
public class JmsSenderTest {
/*
    private static final LocalDateTime JOBSTART = LocalDateTime.of(2018, 11, 20, 14, 55, 34, 555000000);
    private static final LocalDateTime BUSINESSSTART = LocalDateTime.of(2018, 11, 20, 14, 57, 55, 240000000);
    private static final LocalDateTime BUSINESSEND = LocalDateTime.of(2018, 11, 20, 14, 58, 12, 142000000);
    private static final LocalDateTime JOBEND = LocalDateTime.of(2018, 11, 20, 14, 59, 58, 856000000);
    private static final LocalDateTime SENTAT = LocalDateTime.of(2018, 11, 20, 15, 00, 01, 213000000);

    private static final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();

    *//**
     * The serializer should use ISO 8601 for serializing LocalDateTime.
     * Supported from the Server are
     * YYYY-MM-DDThh:mm:ss.sss and
     * YYYY-MM-DDThh:mm:ss.sssZ
     *//*
    @Test
    public void LocalDateTimeSerializerTest() {
        LogMessage message = new LogMessage();
        message.setJobStart(JOBSTART);
        message.setBusinessStart(BUSINESSSTART);
        message.setBusinessEnd(BUSINESSEND);
        message.setJobEnd(JOBEND);
        message.setSentAt(SENTAT);
        
        try {
            String data = mapper.writeValueAsString(message);
            assertTrue(data.contains("\"sentAt\" : \"2018-11-20T15:00:01.213\""));
            assertTrue(data.contains("\"jobStart\" : \"2018-11-20T14:55:34.555\""));
            assertTrue(data.contains("\"jobEnd\" : \"2018-11-20T14:59:58.856\""));
            assertTrue(data.contains("\"businessStart\" : \"2018-11-20T14:57:55.240\""));
            assertTrue(data.contains("\"businessEnd\" : \"2018-11-20T14:58:12.142\""));
        } catch (JsonProcessingException ex) {
            fail(ex.getMessage());
        }
    }*/
}
