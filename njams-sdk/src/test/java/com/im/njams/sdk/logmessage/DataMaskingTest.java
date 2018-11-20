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

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.model.ProcessModel;
import static org.hamcrest.CoreMatchers.*;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.mockito.Matchers.anyObject;
import org.mockito.Mockito;
import static org.mockito.Mockito.doAnswer;

/**
 *
 * @author pnientiedt
 */
public class DataMaskingTest {

    private static JobImpl JOB = Mockito.mock(JobImpl.class);
    private static ProcessModel MODEL = Mockito.mock(ProcessModel.class);
    private static Njams NJAMS = Mockito.mock(Njams.class);
    private static ActivityImpl IMPL = new ActivityImpl(JOB);
    
    @BeforeClass
    public static void mockFields(){
        doAnswer(invocation -> true).when(JOB).isDeepTrace();
        doAnswer(invocation -> MODEL).when(JOB).getProcessModel();
        doAnswer(invocation -> NJAMS).when(MODEL).getNjams();
        doAnswer(invocation -> invocation.getArguments()[0]).when(NJAMS).serialize(anyObject());
        IMPL.start();
    }
    
    @After
    public void reset(){
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
    public void testRegExWithMaskingIBAN() throws Exception{
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
    public void testXmlField() throws Exception{
        DataMasking.addPattern("<requesturi>(\\p{Alpha}|/|\\p{Digit})*</requesturi>");
        IMPL.processInput("<requesturi>/DateServlet/DateServlet</requesturi>");
        assertThat(IMPL.getInput(), not("<requesturi>/DateServlet/DateServlet</requesturi>"));        
    }
    
    
}
