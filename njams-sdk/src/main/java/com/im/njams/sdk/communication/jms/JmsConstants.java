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

import javax.naming.Context;

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
     * Prefix for the jms communication
     */
    public static final String PROPERTY_PREFIX = "njams.sdk.communication.jms";

    /**
     * Name of the JMS Communication Component
     */
    public static final String COMMUNICATION_NAME = "JMS";

    /**
     * Property key for the communication properties. Specifies the
     * ConnectionFactory.
     */
    public static final String CONNECTION_FACTORY = PROPERTY_PREFIX + ".connectionFactory";
    /**
     * Property key for the communication properties. Specifies the username.
     */
    public static final String USERNAME = PROPERTY_PREFIX + ".username";
    /**
     * Property key for the communication properties. Specifies the password.
     */
    public static final String PASSWORD = PROPERTY_PREFIX + ".password";
    /**
     * Property key for the communication properties. Specifies the destination.
     */
    public static final String DESTINATION = PROPERTY_PREFIX + ".destination";
    /**
     * Property key for the communication properties. Specifies the commands
     * destination.
     */
    public static final String COMMANDS_DESTINATION = PROPERTY_PREFIX + ".destination.commands";

    /**
     * Property key for the communication properties. Specifies the jndi initial
     * context factory.
     */
    public static final String INITIAL_CONTEXT_FACTORY = PROPERTY_PREFIX + "." + Context.INITIAL_CONTEXT_FACTORY;
    /**
     * Property key for the communication properties. Specifies the jndi
     * security principal.
     */
    public static final String SECURITY_PRINCIPAL = PROPERTY_PREFIX + "." + Context.SECURITY_PRINCIPAL;
    /**
     * Property key for the communication properties. Specifies the jndi
     * security credentials.
     */
    public static final String SECURITY_CREDENTIALS = PROPERTY_PREFIX + "." + Context.SECURITY_CREDENTIALS;
    /**
     * Property key for the communication properties. Specifies the jndi
     * provider url.
     */
    public static final String PROVIDER_URL = PROPERTY_PREFIX + "." + Context.PROVIDER_URL;
}
