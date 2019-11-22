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
package com.im.njams.sdk.settings;

import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.http.HttpsSender;
import com.im.njams.sdk.communication.jms.JmsConstants;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.BeforeClass;

/**
 *
 * @author krautenberg@integrationmatters.com
 */
public class SettingsTest {
    
    private static Settings settings;
    
    @BeforeClass
    public static void configure(){
        settings = new Settings();           
    }
    
    @Before
    public void reset(){
        Properties communicationProperties = new Properties();       
        settings.setProperties(communicationProperties); 
    }
    
    /**
     * This test tests if the PrintPropertiesWithoutPasswords only prints the
     * passwords as "****" without changing them to "****".
     */
    @Test
    public void testPrintPropertiesWithoutChangingThem() {  
        Properties properties = settings.getProperties();
        properties.put(JmsConstants.SECURITY_CREDENTIALS, "njams");
        properties.put(JmsConstants.PASSWORD, "njams");
        properties.put(HttpsSender.SENDER_PASSWORD, "njams");
        settings.printPropertiesWithoutPasswords();
        String credentials = properties.getProperty(JmsConstants.SECURITY_CREDENTIALS);
        Assert.assertEquals("njams", credentials);
        String jmsPassword = properties.getProperty(JmsConstants.PASSWORD);
        Assert.assertEquals("njams", jmsPassword);
        String httpsPassword = properties.getProperty(HttpsSender.SENDER_PASSWORD);
        Assert.assertEquals("njams", httpsPassword);
    }
    
    /**
     * This test tests if the PrintPropertiesWithoutPasswords prints the
     * properties in correct order.
     */
    @Test
    public void testPrintPropertiesInCorrectOrder() {
        Properties properties = settings.getProperties();
        properties.put("a", "a");
        properties.put("c", "c");
        properties.put("d", "d");
        properties.put("b", "b");
        properties.put("ce", "ce");
        settings.printPropertiesWithoutPasswords();
    }
    
}
