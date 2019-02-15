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

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.im.njams.sdk.communication.CommunicationFactory;

import java.util.Properties;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.slf4j.LoggerFactory;

/**
 * This class tests some methods that try to use a real JMS connection.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
public class JmsReceiverIT {
    
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsReceiverIT.class);

    private JmsReceiverMock impl;

    private static final Properties FILLEDPROPS = new Properties();

    @BeforeClass
    public static void setUpClass() {
        FILLEDPROPS.put(CommunicationFactory.COMMUNICATION, "JMS");
        FILLEDPROPS.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        FILLEDPROPS.put(JmsConstants.SECURITY_PRINCIPAL, "njams");
        FILLEDPROPS.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        FILLEDPROPS.put(JmsConstants.PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        FILLEDPROPS.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        FILLEDPROPS.put(JmsConstants.USERNAME, "njams");
        FILLEDPROPS.put(JmsConstants.PASSWORD, "njams");
        FILLEDPROPS.put(JmsConstants.DESTINATION, "njams4.sdk.test");
    }

    @Before
    public void createNewJmsReceiverImpl() {
        impl = new JmsReceiverMock();
    }

    @After
    public void tearDown() {
        impl.stop();
    }

    //connect tests
    /**
     * This method checks if the method connect() connects correctly without a
     * real existing topic.
     */
    @Test
    public void testConnectWithPropertiesButWithMissingRealDestination() {
        JmsReceiverMock.testBeforeInit(impl);
        impl.init(FILLEDPROPS);
        JmsReceiverMock.testAfterInit(impl, FILLEDPROPS);
        impl.connect();
        JmsReceiverMock.testAfterConnect(impl);
    }

    /**
     * This method checks if the method connect() connects correctly with a real
     * exisiting topic.
     */
    @Test
    public void testConnectWithPropertiesWithRealDestination() {
        Properties props = new Properties();
        props.put(CommunicationFactory.COMMUNICATION, "JMS");
        props.put(JmsConstants.INITIAL_CONTEXT_FACTORY,
                "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
        props.put(JmsConstants.SECURITY_PRINCIPAL, "njams");
        props.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        props.put(JmsConstants.PROVIDER_URL, "tibjmsnaming://vslems01:7222");
        props.put(JmsConstants.CONNECTION_FACTORY, "ConnectionFactory");
        props.put(JmsConstants.USERNAME, "njams");
        props.put(JmsConstants.PASSWORD, "njams");
        props.put(JmsConstants.DESTINATION, "njams4.dev.kai");
        JmsReceiverMock.testBeforeInit(impl);
        impl.init(props);
        JmsReceiverMock.testAfterInit(impl, props);
        impl.connect();
        JmsReceiverMock.testAfterConnect(impl);
    }

    //stop tests
    /**
     * This method tests if the connection stops normally if the connection has
     * been established before.
     */
    @Test
    public void testStopNormally() {
        JmsReceiverMock.testBeforeInit(impl);
        impl.init(FILLEDPROPS);
        JmsReceiverMock.testAfterInit(impl, FILLEDPROPS);
        impl.connect();
        JmsReceiverMock.testAfterConnect(impl);
        impl.stop();
        JmsReceiverMock.testAfterInit(impl, FILLEDPROPS);
    }
    
    //onMessage tests
    
    /**
     * This method tests if onMessage delivers messages if there is a connection 
     * established. In this test case a temporary queue will be created where 
     * the message will be sent to.
     * 
     * @throws JMSException isn't thrown
     * @throws com.fasterxml.jackson.core.JsonProcessingException isn't thrown
     */
    @Test
    public void testOnMessageWithJsonContentAndValidInstruction() throws JMSException, JsonProcessingException {
        impl.init(FILLEDPROPS);
        impl.connect();
        TextMessage msg = mock(TextMessage.class);
        when(msg.getStringProperty("NJAMS_CONTENT")).thenReturn("json");
        
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand("SendProjectmessage");
        inst.setRequest(req);
        ObjectMapper mapper = impl.getMapper();
        String writeValueAsString = mapper.writeValueAsString(inst);
        
        when(msg.getText()).thenReturn(writeValueAsString);
        Session session = impl.getSession();
        TemporaryQueue tempQueue = session.createTemporaryQueue();
        LOG.info("The message will be sent to: " + tempQueue.getQueueName());
        //Check if it has been created
        when(msg.getJMSReplyTo()).thenReturn(tempQueue);
        when(msg.getJMSCorrelationID()).thenReturn("TestCorrelationJmsReceiverIT");
        impl.onMessage(msg);
    }
}
