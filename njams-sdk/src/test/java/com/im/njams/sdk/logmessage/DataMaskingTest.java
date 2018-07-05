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
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import static org.mockito.Matchers.anyObject;
import org.mockito.Mockito;
import static org.mockito.Mockito.doAnswer;

/**
 *
 * @author pnientiedt
 */
public class DataMaskingTest {

    @Test
    public void test() throws Exception {
        DataMasking.addPattern("test");

        JobImpl job = Mockito.mock(JobImpl.class);
        doAnswer(invocation -> true).when(job).isDeepTrace();
        ProcessModel model = Mockito.mock(ProcessModel.class);
        doAnswer(invocation -> model).when(job).getProcessModel();
        Njams njams = Mockito.mock(Njams.class);
        doAnswer(invocation -> njams).when(model).getNjams();
        doAnswer(invocation -> invocation.getArguments()[0]).when(njams).serialize(anyObject());

        ActivityImpl impl = new ActivityImpl(job);
        impl.start();
        impl.processInput("this is a test");
        assertThat(impl.getInput(), is("this is a ****"));
        impl.processOutput("test123test");
        impl.end();
        assertThat(impl.getOutput(), is("****123****"));
    }
}
