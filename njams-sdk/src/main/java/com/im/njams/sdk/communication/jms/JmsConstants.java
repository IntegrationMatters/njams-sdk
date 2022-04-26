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

import com.im.njams.sdk.NjamsSettings;

/**
 * Contains constants by which the JmsSender and the JmsReceiver could be
 * configured
 *
 * @author pnientiedt
 */
public class JmsConstants {

    private JmsConstants() {
        //constants
    }

    /**
     * Name of the JMS Communication Component
     */
    public static final String COMMUNICATION_NAME = "JMS";

    /**
     * Property key for the communication properties. Specifies the
     * ConnectionFactory.
     */
    @Deprecated
    public static final String CONNECTION_FACTORY = NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY;
    /**
     * Property key for the communication properties. Specifies the username.
     */
    @Deprecated
    public static final String USERNAME = NjamsSettings.PROPERTY_JMS_USERNAME;
    /**
     * Property key for the communication properties. Specifies the password.
     */
    @Deprecated
    public static final String PASSWORD = NjamsSettings.PROPERTY_JMS_PASSWORD;
    /**
     * Property key for the communication properties. Specifies the destination.
     */
    @Deprecated
    public static final String DESTINATION = NjamsSettings.PROPERTY_JMS_DESTINATION;
    /**
     * Property key for the communication properties. Specifies the commands
     * destination.
     */
    @Deprecated
    public static final String COMMANDS_DESTINATION = NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION;

    /**
     * Property key for the communication properties. Specifies the jndi initial
     * context factory.
     */
    @Deprecated
    public static final String INITIAL_CONTEXT_FACTORY = NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY;
    /**
     * Property key for the communication properties. Specifies the jndi
     * security principal.
     */
    @Deprecated
    public static final String SECURITY_PRINCIPAL = NjamsSettings.PROPERTY_JMS_SECURITY_PRINCIPAL;
    /**
     * Property key for the communication properties. Specifies the jndi
     * security credentials.
     */
    @Deprecated
    public static final String SECURITY_CREDENTIALS = NjamsSettings.PROPERTY_JMS_SECURITY_CREDENTIALS;
    /**
     * Property key for the communication properties. Specifies the jndi
     * provider url.
     */
    @Deprecated
    public static final String PROVIDER_URL = NjamsSettings.PROPERTY_JMS_PROVIDER_URL;

    /**
     * Property key for the ssl communication properties. Specifies the keyStore.
     */
    @Deprecated
    public static final String KEYSTORE = NjamsSettings.PROPERTY_JMS_KEYSTORE;
    /**
     * Property key for the ssl communication properties. Specifies the keyStore password.
     */
    @Deprecated
    public static final String KEYSTOREPASSWORD = NjamsSettings.PROPERTY_JMS_KEYSTOREPASSWORD;
    /**
     * Property key for the ssl communication properties. Specifies the keyStore Type.
     */
    @Deprecated
    public static final String KEYSTORETYPE = NjamsSettings.PROPERTY_JMS_KEYSTORETYPE;
    /**
     * Property key for the ssl communication properties. Specifies the trustStore.
     */
    @Deprecated
    public static final String TRUSTSTORE = NjamsSettings.PROPERTY_JMS_TRUSTSTORE;
    /**
     * Property key for the ssl communication properties. Specifies the trustStore password.
     */
    @Deprecated
    public static final String TRUSTSTOREPASSWORD = NjamsSettings.PROPERTY_JMS_TRUSTSTOREPASSWORD;
    /**
     * Property key for the ssl communication properties. Specifies the trustStore Type.
     */
    @Deprecated
    public static final String TRUSTSTORETYPE = NjamsSettings.PROPERTY_JMS_TRUSTSTORETYPE;

    /**
     * Delivery mode for JMS Sender. Attention: NonPersistent might lead to data loss.
     */
    @Deprecated
    public static final String DELIVERY_MODE = NjamsSettings.PROPERTY_JMS_DELIVERY_MODE;
}
