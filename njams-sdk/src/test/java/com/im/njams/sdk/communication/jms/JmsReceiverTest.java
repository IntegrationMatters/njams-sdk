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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.ConnectionStatus;

/**
 * This class tests the JmsReceiver
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
public class JmsReceiverTest {

    private static final Properties FILLEDPROPS = new Properties();
    private static final Properties EMPTYPROPS = new Properties();

    private JmsReceiverMock impl;

    @BeforeClass
    public static void initReceiver() {
        JmsReceiverTest.fillProps(FILLEDPROPS);
    }

    private static void fillProps(Properties props) {
        props.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        props.put(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY,
            "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        props.put(NjamsSettings.PROPERTY_JMS_SECURITY_PRINCIPAL, "njams");
        props.put(NjamsSettings.PROPERTY_JMS_SECURITY_CREDENTIALS, "njams");
        props.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, "tibjmsnaming://blablub:7222");
        props.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "NoopJmsFactory");
        props.put(NjamsSettings.PROPERTY_JMS_USERNAME, "njams");
        props.put(NjamsSettings.PROPERTY_JMS_PASSWORD, "njams");
        props.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams4.sdk.test");
    }

    @Before
    public void createNewJmsReceiverMock() {
        impl = new JmsReceiverMock();
    }

    @After
    public void stopReceiverImpl() {
        impl.stop();
    }

    //getName tests

    /**
     * This method tests if getName() returns the
     * JmsConstants.COMMUNICATION_NAME.
     */
    @Test
    public void testGetNameWithProperties() {
        assertEquals(impl.getName(), JmsSender.COMMUNICATION_NAME);
    }

    //init tests

    /**
     * This method tests if the init works with normal Properties, but without
     * JmsConstants.COMMAND_DESTINATION set.
     */
    @Test
    public void testInitWithFilledProps() {
        JmsReceiverMock.testBeforeInit(impl);
        impl.init(FILLEDPROPS);
        //Initialized
        JmsReceiverMock.testAfterInit(impl, FILLEDPROPS);
    }

    //Delete this when SDK-111 has been resolved and uncomment the method with
    //the identical name.

    /**
     * This method tests if the init works with empty Properties.
     */
    @Test
    public void testInitWithEmptyProps() {
        JmsReceiverMock.testBeforeInit(impl);
        impl.init(EMPTYPROPS);
        //Initialized
        JmsReceiverMock.testAfterInit(impl, EMPTYPROPS);
    }

    //Uncomment this when SDK-111 has been resolved and delete the method with
    //the identical name. Maybe it is not expected NjamsSdkRuntimeException,
    //but an other exception.
    /**
     * @Test(expected = NjamsSdkRuntimeException.class) public void
     * testInitWithEmptyProps(){ this.testBeforeInit(impl);
     * impl.init(EMPTYPROPS); }
     */
    //Delete this when SDK-111 has been resolved and uncomment the method with
    //the identical name.

    /**
     * This method tests if the init works with null Properties. It should throw
     * a NullPointerException.
     */
    @Test(expected = NullPointerException.class)
    public void testInitWithNullProps() {
        JmsReceiverMock.testBeforeInit(impl);
        //Should throw a NullPointerException because of props.getProperty(..) with null Properties
        impl.init(null);
    }

    //Uncomment this when SDK-111 has been resolved and delete the method with
    //the identical name. Maybe it is not expected NjamsSdkRuntimeException,
    //but an other exception.
    /**
     * @Test(expected = NjamsSdkRuntimeException.class) public void
     * testInitWithNullProps(){ this.testBeforeInit(impl); //Should throw a
     * NullPointerException because of props.getProperty(..) with null
     * Properties impl.init(null); }
     *
     */
    /**
     * This method tests if the init works with normal Properties where
     * JmsConstants.NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION is set.
     */
    @Test
    public void testInitWithCustomProps() {
        JmsReceiverMock.testBeforeInit(impl);
        Properties props = new Properties();
        props.put(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION, "njams4.sdk.test.custom.commands");
        impl.init(props);
        //Initialized
        JmsReceiverMock.testAfterInit(impl, props);
    }

    /**
     * This method tests if a second initialize overwrites the first
     * initialisation.
     */
    @Test
    public void testMultipleInitWithSameProps() {
        JmsReceiverMock.testBeforeInit(impl);
        Properties props = new Properties();
        props.put(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION, "njams4.sdk.test.custom.commands");
        //first initialize
        impl.init(props);
        JmsReceiverMock.testAfterInit(impl, props);
        //second initialize
        impl.init(FILLEDPROPS);
        JmsReceiverMock.testAfterInit(impl, FILLEDPROPS);
    }

    /**
     * This method tests if
     */
    @Test
    public void testDestinationAndCommandsSet() {
        JmsReceiverMock.testBeforeInit(impl);
        Properties props = new Properties();
        props.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams4.overwrite");
        props.put(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION, "njams4.overwritten");
        //first initialize
        impl.init(props);
        assertEquals("njams4.overwritten", impl.getTopicName());
    }

    //connect tests

    /**
     * This method checks if the method connect() throws an
     * NjamsSdkRuntimeException if the method init wasn't invoked beforehand.
     */
    @Test(expected = NjamsSdkRuntimeException.class)
    public void testConnectWithoutInit() {
        JmsReceiverMock.testBeforeInit(impl);
        //This should throw an NjamsSdkRuntimeException because
        //connect() was called before init() was called.
        impl.connect();
    }

    /**
     * This method checks if the method connect() throws an
     * NjamsSdkRuntimeException if a connection to a real topic should be
     * established but the username hasn't been set. By that, the default
     * username and password will be used, which can't be used with our jms
     * service.
     */
    @Test(expected = NjamsSdkRuntimeException.class)
    public void testConnectWithPropertiesWithRealDestinationButWithoutUsername() {
        Properties props = new Properties();
        props.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        props.put(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY,
            "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        props.put(NjamsSettings.PROPERTY_JMS_SECURITY_PRINCIPAL, "njams");
        props.put(NjamsSettings.PROPERTY_JMS_SECURITY_CREDENTIALS, "njams");
        props.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, "tibjmsnaming://blablub:7222");
        props.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "NoopJmsFactory");
        props.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams4.dev.kai");

        JmsReceiverMock.testBeforeInit(impl);
        impl.init(props);
        JmsReceiverMock.testAfterInit(impl, props);
        impl.connect();
    }

    /**
     * This method tests if connect throws an NjamsSdkRuntimeException if the
     * properties are empty. It should be thrown when the ConnectionFactory is
     * looked up.
     */
    @Test(expected = NjamsSdkRuntimeException.class)
    public void testConnectWithEmptyPropertiesSetInInit() {
        JmsReceiverMock.testBeforeInit(impl);
        impl.init(EMPTYPROPS);
        JmsReceiverMock.testAfterInit(impl, EMPTYPROPS);
        impl.connect();
    }

    //stop tests

    /**
     * This method tests if the stop method works while it is disconnected.
     */
    @Test
    public void testStopWhileDisconnected() {
        JmsReceiverMock.testBeforeInit(impl);
        impl.init(FILLEDPROPS);
        JmsReceiverMock.testAfterInit(impl, FILLEDPROPS);
        impl.stop();
        JmsReceiverMock.testAfterInit(impl, FILLEDPROPS);
    }

    /**
     * This method tests if the stop method works while it is connecting.
     */
    @Test
    public void testStopWhileConnecting() {
        JmsReceiverMock.testBeforeInit(impl);
        impl.init(FILLEDPROPS);
        JmsReceiverMock.testAfterInit(impl, FILLEDPROPS);
        impl.setConnectionStatus(ConnectionStatus.CONNECTING);
        impl.stop();
        assertTrue(impl.isConnecting());
    }

    /**
     * This method looks if stop works correctly if init() hasn't been called
     * before.
     */
    @Test
    public void testStopWithoutInitialize() {
        JmsReceiverMock.testBeforeInit(impl);
        impl.stop();
        assertTrue(impl.isDisconnected());
        JmsReceiverMock.testBeforeInit(impl);
    }

    //onMessageTests

    /**
     * This method tests if onMessage ignores messages whose content is not
     * "json".
     *
     * @throws javax.jms.JMSException isn't thrown
     */
    @Test
    public void testOnMessageWithoutJsonContent() throws JMSException {
        Message msg = mock(Message.class);
        when(msg.getStringProperty("NJAMS_CONTENT")).thenReturn("notJson");
        impl.onMessage(msg);
        //No exception will be thrown, it will just be ignored.
    }

    /**
     * This method tests if onMessage ignores messages if the ObjectMapper
     * hasn't been initialized.
     *
     * @throws JMSException isn't thrown
     */
    @Test
    public void testOnMessageWithJsonContentButNotInitialized() throws JMSException {
        TextMessage msg = mock(TextMessage.class);
        when(msg.getStringProperty("NJAMS_CONTENT")).thenReturn("json");
        impl.onMessage(msg);
    }

    /**
     * This method tests if onMessage ignores messages the instruction is not
     * valid.
     *
     * @throws JMSException isn't thrown
     */
    @Test
    public void testOnMessageWithJsonContentButWithoutValidInstruction() throws JMSException {
        impl.init(FILLEDPROPS);
        TextMessage msg = mock(TextMessage.class);
        when(msg.getStringProperty("NJAMS_CONTENT")).thenReturn("json");
        when(msg.getText()).thenReturn("No_Valid_Content");
        impl.onMessage(msg);
    }

    /**
     * This method tests if onMessage ignores messages if there is no connection
     * established. (May occur if a message came in and the connection is lost
     * afterwards).
     *
     * @throws JMSException                                       isn't thrown
     * @throws com.fasterxml.jackson.core.JsonProcessingException isn't thrown
     */
    @Test
    public void testOnMessageWithJsonContentAndValidInstructionButWithoutConnection()
        throws JMSException, JsonProcessingException {
        impl.init(FILLEDPROPS);
        TextMessage msg = mock(TextMessage.class);
        when(msg.getStringProperty("NJAMS_CONTENT")).thenReturn("json");

        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand("SendProjectmessage");
        inst.setRequest(req);
        ObjectMapper mapper = impl.getMapper();
        String writeValueAsString = mapper.writeValueAsString(inst);

        when(msg.getText()).thenReturn(writeValueAsString);
        impl.onMessage(msg);
        JmsReceiverMock.testAfterInit(impl, FILLEDPROPS);
    }

    //onExceptionTests

    /**
     * This method tests if the onException method triggers a reconnect.
     *
     * @throws java.lang.InterruptedException isn't thrown
     */
    @Test
    public void testOnException() throws InterruptedException {
        JMSException ex = new JMSException("Test");
        assertTrue(impl.isDisconnected());
        impl.onException(ex);
        Thread.sleep(100);
        assertTrue(impl.isConnected());
    }
}
