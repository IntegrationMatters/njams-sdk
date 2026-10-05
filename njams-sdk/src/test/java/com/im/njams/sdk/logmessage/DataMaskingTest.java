/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.logmessage;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.im.njams.sdk.settings.ClientSettings;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSerializers;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.serializer.SerializerResult;

/**
 * @author pnientiedt
 */
public class DataMaskingTest {

    private static JobImpl JOB = Mockito.mock(JobImpl.class);
    private static ProcessModel MODEL = Mockito.mock(ProcessModel.class);
    private static Njams NJAMS = Mockito.mock(Njams.class);
    private static NjamsSerializers SERIALIZERS = Mockito.mock(NjamsSerializers.class);
    private static final JobTracing TRACING = new JobTracing();
    private static final JobAttributes ATTRIBUTES = Mockito.mock(JobAttributes.class);
    private static ActivityImpl IMPL = null;

    @BeforeClass
    public static void mockFields() {
        // ActivityImpl reads the deep-trace flag via the tracing facet
        TRACING.setDeepTrace(true);
        doAnswer(invocation -> ATTRIBUTES).when(JOB).attributes();
        // ActivityImpl flags the job as instrumented via the tracing facet
        doAnswer(invocation -> TRACING).when(JOB).tracing();
        doAnswer(invocation -> NJAMS).when(MODEL).getNjams();
        doAnswer(invocation -> NJAMS).when(JOB).getNjams();
        // input/output truncation no longer applies a limit in these masking tests: pass values through
        doAnswer(invocation -> invocation.getArgument(0, String.class)).when(JOB).limitPayload(any());
        doAnswer(invocation -> invocation.getArgument(0, String.class)).when(JOB).applyLimit(any(), anyBoolean());
        // ActivityImpl serializes input/output via the serializers facet, returning a SerializerResult
        doAnswer(invocation -> SERIALIZERS).when(NJAMS).serializers();
        doAnswer(invocation -> new SerializerResult((String) invocation.getArguments()[0], false))
                .when(SERIALIZERS).serialize(any(), anyInt());
        doAnswer(invocation -> invocation.getArguments()[0]).when(SERIALIZERS).serialize(any());
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
    public void testFields() {
        DataMasking.addPattern("o");
        String in = "Hello World";
        String expected = "Hell* W*rld";

        IMPL.setInput(in);
        assertEquals(expected, IMPL.getInput());
        IMPL.setOutput(in);
        assertEquals(expected, IMPL.getOutput());
        IMPL.setEventPayload(in);
        assertEquals(expected, IMPL.getEventPayload());
        IMPL.setEventCode(in);
        assertEquals(expected, IMPL.getEventCode());
        IMPL.setEventMessage(in);
        assertEquals(expected, IMPL.getEventMessage());
        IMPL.setStackTrace(in);
        assertEquals(expected, IMPL.getStackTrace());
        IMPL.addAttribute("key", in);
        assertEquals(expected, IMPL.getAttributes().get("key"));
    }

    @Test
    public void addPatternsFromSettings() {
        DataMasking.removePatterns();
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX + "ssn", "\\d{3}-\\d{2}-\\d{4}");
        settings.put("njams.sdk.other.key", "irrelevant");

        DataMasking.addPatterns(settings);

        assertEquals(1, DataMasking.getPatterns().size());
    }

    @Test
    public void sameRegexIsRegisteredOnlyOnce() {
        DataMasking.addPattern("first", "secret");
        DataMasking.addPattern("second", "secret");

        assertEquals(1, DataMasking.getPatterns().size());
        assertEquals("first", DataMasking.getPatterns().get(0).getNameOfPattern());
        assertEquals("a ******", DataMasking.maskString("a secret"));
    }

    @Test
    public void unnamedPatternsAreNotRegisteredTwice() {
        DataMasking.addPattern("secret");
        DataMasking.addPattern("secret");
        DataMasking.addPatterns(Arrays.asList("secret", "other"));

        assertEquals(2, DataMasking.getPatterns().size());
    }

    @Test
    public void repeatedAddPatternsFromSettingsDoesNotDuplicate() {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX + "ssn", "\\d{3}-\\d{2}-\\d{4}");
        settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX + "iban", "IBAN\\d+");

        DataMasking.addPatterns(settings);
        DataMasking.addPatterns(settings);
        DataMasking.addPatterns(settings);

        assertEquals(2, DataMasking.getPatterns().size());
        assertEquals("a *********** b", DataMasking.maskString("a 123-45-6789 b"));
    }

    @Test
    public void differentRegexesWithSameNameAreBothRegistered() {
        DataMasking.addPattern("name", "one");
        DataMasking.addPattern("name", "two");

        assertEquals(2, DataMasking.getPatterns().size());
        assertEquals("*** ***", DataMasking.maskString("one two"));
    }

    @Test
    public void concurrentRegistrationAndMaskingIsSafe() throws Exception {
        final int threads = 8;
        final int patterns = 50;
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < patterns; i++) {
                        DataMasking.addPattern("secret" + i);
                        // reads concurrently to the writes of the other threads
                        DataMasking.maskString("some secret0 and secret" + i + " text");
                        DataMasking.getPatterns().size();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(patterns, DataMasking.getPatterns().size());
    }

}
