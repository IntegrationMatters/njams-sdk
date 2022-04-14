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
package com.im.njams.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.logmessage.NjamsFeatures;
import com.im.njams.sdk.logmessage.NjamsJobs;
import com.im.njams.sdk.logmessage.NjamsState;
import com.im.njams.sdk.model.ProcessModelUtils;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.serializer.Serializer;
import com.im.njams.sdk.settings.Settings;

/**
 *
 * @author stkniep
 */
public class NjamsTest {

    private Njams instance;

    @Before
    public void createNewInstance() {
        final Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestReceiver.NAME);
        instance = new Njams(new Path(), "", "", settings);
    }

    @Test
    public void testSerializer() {
        System.out.println("addSerializer");
        final Serializer<List> expResult = l -> "list";

        instance.getNjamsSerializers().add(ArrayList.class, a -> a.getClass().getSimpleName());
        instance.getNjamsSerializers().add(List.class, expResult);

        String serialized;

        // found ArrayList serializer
        serialized = instance.getNjamsSerializers().serialize(new ArrayList<>());
        assertNotNull(serialized);
        assertEquals("ArrayList", serialized);

        // found default string serializer
        serialized = instance.getNjamsSerializers().serialize(new HashMap<>());
        assertNotNull(serialized);
        assertEquals("{}", serialized);

        // found list serializer
        serialized = instance.getNjamsSerializers().serialize(new LinkedList<>());
        assertNotNull(serialized);
        assertEquals("list", serialized);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void testCreateJobWithoutStart_throwsAnError() {
        ProcessModelUtils utils = new ProcessModelUtils(new NjamsJobs(null, new NjamsState(), new NjamsFeatures(), null), null, null, null, new Settings(), null, null, null);
        ProcessModel model = new ProcessModel(new Path("PROCESSES"), utils);
        //This should throw an NjamsSdkRuntimeException
        Job job = model.createJob();
    }

    @Test
    public void testAddJobAfterStart() {
        final NjamsState njamsState = new NjamsState();
        njamsState.start();
        ProcessModelUtils utils = new ProcessModelUtils(new NjamsJobs(null, njamsState, new NjamsFeatures(), null), null, null, null, new Settings(), null, null, null);
        ProcessModel model = new ProcessModel(new Path("PROCESSES"), utils);
        //This should throw an NjamsSdkRuntimeException
        Job job = model.createJob();
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void testStopBeforeStart() {
        //This should throw an NjamsSdkRuntimeException
        instance.stop();
    }

    @Test
    public void testStartStopStart() {
        instance.start();
        instance.stop();
        instance.start();
    }

    @Test
    public void testHasNoProcessModel() {
        assertFalse(instance.hasProcessModel(new Path("PROCESSES")));
    }

    @Test
    public void testNoProcessModelForNullPath() {
        assertFalse(instance.hasProcessModel(null));
    }

    @Test
    public void testHasProcessModel() {
        instance.createProcess(new Path("PROCESSES"));
        assertTrue(instance.hasProcessModel(new Path("PROCESSES")));
    }

    @Test
    public void setDataMaskingViaSettings() {
        DataMasking.removePatterns();

        Settings settings = new Settings();
        settings.put(DataMasking.DATA_MASKING_ENABLED, "true");
        settings.put(DataMasking.DATA_MASKING_REGEX_PREFIX + "MaskAll", ".*");
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);

        Njams njams = new Njams(new Path("TestPath"), "1.0.0", "SDK", settings);
        njams.start();

        assertEquals("*****", DataMasking.maskString("Hello"));
    }

    @Test
    public void disableDataMaskingViaSettings() {
        DataMasking.removePatterns();
        Settings settings = new Settings();
        settings.put(DataMasking.DATA_MASKING_ENABLED, "false");
        settings.put(DataMasking.DATA_MASKING_REGEX_PREFIX, ".*");
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);

        Njams njams = new Njams(new Path("TestPath"), "1.0.0", "SDK", settings);
        njams.start();

        assertEquals("Hello", DataMasking.maskString("Hello"));
    }

    @Test
    public void disableDataMaskingDisablesAllDataMasking() {
        DataMasking.removePatterns();

        Settings settings = new Settings();
        settings.put(DataMasking.DATA_MASKING_ENABLED, "false");
        settings.put(DataMasking.DATA_MASKING_REGEX_PREFIX, ".*");
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);

        Njams njams = new Njams(new Path("TestPath"), "1.0.0", "SDK", settings);

        List<String> dataMaskingStrings = new ArrayList<>();
        dataMaskingStrings.add("Hello");
        njams.getConfiguration().setDataMasking(dataMaskingStrings);
        njams.start();

        assertEquals("Hello", DataMasking.maskString("Hello"));
    }

    @Test
    public void enableDataMaskingWithoutRegex() {
        DataMasking.removePatterns();
        Settings settings = new Settings();
        settings.put(DataMasking.DATA_MASKING_ENABLED, "true");
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);

        Njams njams = new Njams(new Path("TestPath"), "1.0.0", "SDK", settings);

        njams.start();

        assertEquals("Hello", DataMasking.maskString("Hello"));
    }
}
