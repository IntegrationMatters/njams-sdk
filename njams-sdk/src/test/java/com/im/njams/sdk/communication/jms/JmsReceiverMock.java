/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.ConnectionStatus;

/**
 * This class is a extended version of the JmsReceiver to get its fields.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
class JmsReceiverMock extends JmsReceiver {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsReceiverMock.class);

    private static final String MESSAGESELECTORSTRING = "NJAMS_RECEIVER = '>SDK4>' OR NJAMS_RECEIVER = '>SDK4>TEST>'";

    public JmsReceiverMock() {
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("SDK4>TEST");
        setInstancePath(path);
    }

    //Getter
    public Connection getConnection() {
        return (Connection) getThisObjectPrivateField("connection");
    }

    public Session getSession() {
        return (Session) getThisObjectPrivateField("session");
    }

    public Properties getProperties() {
        return (Properties) getThisObjectPrivateField("properties");
    }

    public MessageConsumer getConsumer() {
        return (MessageConsumer) getThisObjectPrivateField("consumer");
    }

    public String getTopicName() {
        return (String) getThisObjectPrivateField("topicName");
    }

    public ObjectMapper getMapper() {
        return (ObjectMapper) getThisObjectPrivateField("mapper");
    }

    public String getMessageSelector() {
        return (String) getThisObjectPrivateField("messageSelector");
    }

    public void setConnectionStatus(ConnectionStatus connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    /**
     * This method gets a private field of this class
     *
     * @param fieldName the name of the field
     */
    private Object getThisObjectPrivateField(String fieldName) {
        try {
            Class clazz = JmsReceiver.class;
            Field privateField = clazz.getDeclaredField(fieldName);
            privateField.setAccessible(true);
            return privateField.get(this);
        } catch (NullPointerException | NoSuchFieldException | SecurityException | IllegalArgumentException
                | IllegalAccessException ex) {
            return null;
        }
    }

    /**
     * This method tests if the properties that are set in init are null.
     * @param impl the instance to look in
     */
    public static void testBeforeInit(JmsReceiverMock impl) {
        assertNull(impl.getProperties());
        assertNull(impl.getTopicName());
        assertNull(impl.getMapper());
        assertEquals(MESSAGESELECTORSTRING, impl.getMessageSelector());
        testBeforeConnect(impl);
    }

    /**
     * This method tests if the properties that are set in init are filled,
     * that the status is DISCONNECTED and that the properties that are set in
     * connect() are null.
     * @param impl the instance to look in
     * @param props the properties to compare with
     */
    public static void testAfterInit(JmsReceiverMock impl, Properties props) {
        assertTrue(impl.isDisconnected());
        testBeforeConnect(impl);
        //Stays the same
        assertNotNull(impl.getMapper());
        assertEquals(props, impl.getProperties());
        if (props.containsKey(JmsConstants.COMMANDS_DESTINATION)) {
            assertEquals(props.getProperty(JmsConstants.COMMANDS_DESTINATION), impl.getTopicName());
        } else {
            assertEquals(props.getProperty(JmsConstants.DESTINATION) + ".commands", impl.getTopicName());
        }
        assertEquals(MESSAGESELECTORSTRING, impl.getMessageSelector());
    }

    /**
     * This method tests if the properties that are set in connect() are null
     * @param impl the instance to look in
     */
    public static void testBeforeConnect(JmsReceiverMock impl) {
        assertNull(impl.getConnection());
        assertNull(impl.getSession());
        assertNull(impl.getConsumer());
    }

    /**
     * This method tests if the properties that are set in connect() are set
     * @param impl the instance to look in
     */
    public static void testAfterConnect(JmsReceiverMock impl) {
        assertTrue(impl.isConnected());
        assertNotNull(impl.getConnection());
        assertNotNull(impl.getSession());
        assertNotNull(impl.getConsumer());
    }

    /**
     * This method is overridden to avoid reconnecting with a connection that
     * doesn't exist.
     */
    @Override
    public void reconnect(Exception ex) {
        LOG.info("reconnected");
        connectionStatus = ConnectionStatus.CONNECTED;
    }
}
