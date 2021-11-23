package com.im.njams.sdk.communication;

import com.im.njams.sdk.communication.jms.JmsConstants;
import org.apache.activemq.ActiveMQSslConnectionFactory;

import javax.jms.ConnectionFactory;
import javax.naming.Context;
import java.util.Properties;

public class NjamsConnectionFactory {
    public static ConnectionFactory getFactory(Context context, Properties properties) throws Exception {
        ConnectionFactory factory;
        if(properties.getProperty(JmsConstants.CONNECTION_FACTORY).equalsIgnoreCase("ActiveMQSslConnectionFactory")) {
            ActiveMQSslConnectionFactory sslFactory = new ActiveMQSslConnectionFactory();
            if (properties.containsKey(JmsConstants.KEYSTORE))
                sslFactory.setKeyStore(properties.getProperty(JmsConstants.KEYSTORE));
            if (properties.containsKey(JmsConstants.KEYSTOREPASSWORD))
                sslFactory.setKeyStorePassword(properties.getProperty(JmsConstants.KEYSTOREPASSWORD));
            if (properties.containsKey(JmsConstants.KEYSTORETYPE))
                sslFactory.setKeyStoreType(properties.getProperty(JmsConstants.KEYSTORETYPE));
            if (properties.containsKey(JmsConstants.TRUSTSTORE))
                sslFactory.setTrustStore(properties.getProperty(JmsConstants.TRUSTSTORE));
            if (properties.containsKey(JmsConstants.TRUSTSTOREPASSWORD))
                sslFactory.setTrustStorePassword(properties.getProperty(JmsConstants.TRUSTSTOREPASSWORD));
            if (properties.containsKey(JmsConstants.TRUSTSTORETYPE))
                sslFactory.setTrustStoreType(properties.getProperty(JmsConstants.TRUSTSTORETYPE));
            if (properties.containsKey(JmsConstants.PASSWORD))
                sslFactory.setPassword(properties.getProperty(JmsConstants.PASSWORD));
            if (properties.containsKey(JmsConstants.USERNAME))
                sslFactory.setUserName(properties.getProperty(JmsConstants.USERNAME));
            if (properties.containsKey(JmsConstants.PROVIDER_URL))
                sslFactory.setBrokerURL(properties.getProperty(JmsConstants.PROVIDER_URL));
            factory = sslFactory;
        } else {
            factory = (ConnectionFactory) context
                    .lookup(properties.getProperty(JmsConstants.CONNECTION_FACTORY));
        }
        return factory;
    }
}
