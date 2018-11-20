/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * The Software shall be used for Good, not Evil.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.jms;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author krautenberg@integrationmatters.com
 */
public class JmsSenderTest {

    private static final LocalDateTime JOBSTART = LocalDateTime.of(2018, 11, 20, 14, 55, 34, 555000000);
    private static final LocalDateTime BUSINESSSTART = LocalDateTime.of(2018, 11, 20, 14, 57, 55, 240000000);
    private static final LocalDateTime BUSINESSEND = LocalDateTime.of(2018, 11, 20, 14, 58, 12, 142000000);
    private static final LocalDateTime JOBEND = LocalDateTime.of(2018, 11, 20, 14, 59, 58, 856000000);
    private static LocalDateTime SENTAT = LocalDateTime.of(2018, 11, 20, 15, 00, 01, 213000000);

    private static final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();

    public JmsSenderTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * The serializer should use ISO 8601 for serializing LocalDateTime.
     * Supported from the Server are
     * YYYY-MM-DDThh:mm:ss.sss and
     * YYYY-MM-DDThh:mm:ss.sssZ
     */
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
    }
}
