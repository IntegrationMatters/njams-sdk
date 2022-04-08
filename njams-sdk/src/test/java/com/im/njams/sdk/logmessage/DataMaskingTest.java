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
package com.im.njams.sdk.logmessage;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.ser.Serializers;
import com.im.njams.sdk.NjamsSerializers;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

import java.util.Properties;

/**
 *
 * @author pnientiedt
 */
public class DataMaskingTest {

    private static JobImpl JOB = Mockito.mock(JobImpl.class);
    private static ProcessModel MODEL = Mockito.mock(ProcessModel.class);
    private static Njams NJAMS = Mockito.mock(Njams.class);
    private static NjamsSerializers SERIALIZERS = Mockito.mock(NjamsSerializers.class);
    private static ActivityImpl IMPL = null;

    @BeforeClass
    public static void mockFields() {
        doAnswer(invocation -> true).when(JOB).isDeepTrace();
        doAnswer(invocation -> NJAMS).when(MODEL).getNjams();
        doAnswer(invocation -> NJAMS).when(JOB).getNjams();
        doAnswer(invocation -> SERIALIZERS).when(NJAMS).getSerializers();
        doAnswer(invocation -> invocation.getArguments()[0]).when(SERIALIZERS).serialize(anyObject());
        IMPL = new ActivityImpl(JOB, Mockito.mock(ActivityModel.class));
        IMPL.start();
    }

    @After
    public void reset() {
        DataMasking.removePatterns();
    }

    @Test
    public void testString() throws Exception {
        DataMasking.addPattern("test");

        IMPL.processInput("this is a test");
        assertThat(IMPL.getInput(), is("this is a ****"));
        IMPL.processOutput("test123test");
        IMPL.end();
        assertThat(IMPL.getOutput(), is("****123****"));
    }

    @Test
    public void testRegExWithMaskingEverything() throws Exception {
        DataMasking.addPattern(".*");

        IMPL.processInput("This is a test");
        assertThat(IMPL.getInput(), is("**************"));
    }

    @Test
    public void testRegExWithMaskingIBAN() throws Exception {
        DataMasking.addPattern("IBAN: \\p{Alpha}\\p{Alpha}\\p{Digit}+");

        IMPL.processInput("IBAN: DE1542346541531");
        assertThat(IMPL.getInput(), is("*********************"));

        IMPL.processInput("IBAN: DE1542346541531 is an IBAN");
        assertThat(IMPL.getInput(), is("********************* is an IBAN"));

        IMPL.processInput("IBAN: IBAN: DE1542346541531");
        assertThat(IMPL.getInput(), is("IBAN: *********************"));

        DataMasking.addPattern("IBAN");
        IMPL.processInput("IBAN: DE1542346541531 is an IBAN");
        assertThat(IMPL.getInput(), is("********************* is an ****"));
    }

    @Test
    public void testXmlField() throws Exception {
        DataMasking.addPattern("<requesturi>(\\p{Alpha}|/|\\p{Digit})*</requesturi>");
        IMPL.processInput("<requesturi>/DateServlet/DateServlet</requesturi>");
        assertThat(IMPL.getInput(), not("<requesturi>/DateServlet/DateServlet</requesturi>"));
    }

    @Test
    public void addPatternsFromProperties(){
        Properties properties = new Properties();
        properties.put(DataMasking.DATA_MASKING_REGEX_PREFIX + "creditcard", "Creditcard Number : \\p{Digit}+");
        properties.put("SomeOtherString", ".*");
        DataMasking.addPatterns(properties);
        final String maskedString1 = DataMasking.maskString("Creditcard Number : 1234");
        final String maskedString2 = DataMasking.maskString("Anything else");
        assertEquals("************************", maskedString1);
        assertEquals("Anything else", maskedString2);

    }

}
