/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.NamingException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.communication.jms.factory.JndiJmsFactory;

public class AMQInitTest {

    private Properties props = null;

    @Before
    public void setup() {
        props = new Properties();
        props.put("njams.sdk.communication.jms.connectionFactory", "QueueConnectionFactory");
        props.put("njams.sdk.communication.jms.username", "njams");
        props.put("njams.sdk.communication.jms.password", "njams");
        props.put("njams.sdk.communication.jms.destination", "njams");
        props.put("njams.sdk.communication.jms.java.naming.factory.initial",
            "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
        props.put("njams.sdk.communication.jms.java.naming.provider.url", "tcp://vslamq01:61616");
        props.put("njams.sdk.communication.jms.destination", "dynamicQueues/njams.sophie");
        props.put("njams.sdk.communication.jms.connectionFactory", "ConnectionFactory");
        props.put("njams.sdk.communication.jms.destination.commands", "dynamicTopics/njams.sophie.commands");

    }

    @Test
    @Ignore("This should only be executed manually")
    public void testConnectToActiveMQ() throws JMSException, NamingException {
        try (JmsFactory factory = JmsFactory.find(props)) {
            assertTrue(factory instanceof JndiJmsFactory);
            ConnectionFactory conFactory = factory.createConnectionFactory();
            assertTrue(conFactory instanceof ActiveMQConnectionFactory);
            Connection con = conFactory.createConnection();
            assertNotNull(con);
            Session session = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Queue queue = factory.createQueue(session, "njams.event");
            assertNotNull(queue);
        }
    }

}
