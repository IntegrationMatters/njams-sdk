package com.im.njams.sdk.communication;

import com.im.njams.sdk.NjamsSettings;
import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.junit.Before;
import org.junit.Test;

import javax.jms.ConnectionFactory;
import javax.naming.Context;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class NjamsConnectionFactoryTest {

    private Context context;
    private Properties properties;

    @Before
    public void setUp() throws Exception {
        context = mock(Context.class);
        properties = new Properties();
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
    public void testGetFactoryAMQ() throws Exception {
        properties.setProperty(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "ActiveMQSslConnectionFactory");
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
        properties.setProperty(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "bla");
        NjamsConnectionFactory.getFactory(context, properties);
        verify(context, times(1)).lookup(eq("bla"));
    }

}
