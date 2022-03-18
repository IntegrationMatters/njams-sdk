package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.naming.Context;

import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.jms.JmsConstants;

public class NjamsConnectionFactoryTest {

    private Context context;
    private Properties properties;

    @Before
    public void setUp() throws Exception {
        context = mock(Context.class);
        properties = new Properties();
        properties.setProperty(JmsConstants.KEYSTORE, "keystore");
        properties.setProperty(JmsConstants.KEYSTOREPASSWORD, "keypwd");
        properties.setProperty(JmsConstants.KEYSTORETYPE, "keytype");
        properties.setProperty(JmsConstants.TRUSTSTORE, "truststore");
        properties.setProperty(JmsConstants.TRUSTSTOREPASSWORD, "trustpwd");
        properties.setProperty(JmsConstants.TRUSTSTORETYPE, "trusttype");
        properties.setProperty(JmsConstants.PASSWORD, "pwd");
        properties.setProperty(JmsConstants.USERNAME, "user");
        properties.setProperty(JmsConstants.PROVIDER_URL, "url");
    }

    @Test
    public void testGetFactoryAMQ() throws Exception {
        properties.setProperty(JmsConstants.CONNECTION_FACTORY, "ActiveMQSslConnectionFactory");
        ConnectionFactory cnf = NjamsConnectionFactory.getFactory(context, properties);
        verify(context, never()).lookup(any(String.class));

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
    public void testGetFactoryOther() throws Exception {
        properties.setProperty(JmsConstants.CONNECTION_FACTORY, "bla");
        NjamsConnectionFactory.getFactory(context, properties);
        verify(context, times(1)).lookup(eq("bla"));
    }

}
