package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.jms.factory.ActiveMqSslJmsFactory;
import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.communication.jms.factory.JndiJmsFactory;

public class NjamsConnectionFactoryTest {

    private Properties properties;

    @Before
    public void setUp() {
        properties = new Properties();
        properties.put(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY,
            "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_KEYSTORE, "keystore");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_KEYSTOREPASSWORD, "keypwd");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_KEYSTORETYPE, "keytype");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_TRUSTSTORE, "truststore");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_TRUSTSTOREPASSWORD, "trustpwd");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_TRUSTSTORETYPE, "trusttype");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_PASSWORD, "pwd");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_USERNAME, "user");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, "url");
    }

    @Test
    public void testGetFactoryAMQnonJNDIwithSSL() throws Exception {
        properties.setProperty(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "ActiveMQSslConnectionFactory");
        JmsFactory jmsFactory = JmsFactory.find(properties);
        assertTrue(jmsFactory instanceof ActiveMqSslJmsFactory);
        ConnectionFactory cnf = jmsFactory.createConnectionFactory();

        assertTrue(cnf instanceof ActiveMQSslConnectionFactory);
        ActiveMQSslConnectionFactory amq = (ActiveMQSslConnectionFactory) cnf;
        assertEquals("keystore", amq.getKeyStore());
        assertEquals("keypwd", amq.getKeyStorePassword());
        assertEquals("keytype", amq.getKeyStoreType());
        assertEquals("truststore", amq.getTrustStore());
        assertEquals("trustpwd", amq.getTrustStorePassword());
        assertEquals("trusttype", amq.getTrustStoreType());
        assertEquals("pwd", amq.getPassword());
        assertEquals("user", amq.getUserName());
        assertEquals("url", amq.getBrokerURL());
    }

    @Test
    public void testGetFactoryAMQviaJNDI() throws Exception {
        properties.setProperty(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "QueueConnectionFactory");
        properties.setProperty(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY,
            "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
        JmsFactory jmsFactory = JmsFactory.find(properties);
        assertTrue(jmsFactory instanceof JndiJmsFactory);
        ConnectionFactory cnf = jmsFactory.createConnectionFactory();
        assertTrue(cnf instanceof ActiveMQConnectionFactory);
    }

}
