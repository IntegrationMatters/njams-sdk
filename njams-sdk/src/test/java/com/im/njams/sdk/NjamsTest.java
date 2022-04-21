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

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.communication.ReplayRequest;
import com.im.njams.sdk.communication.ReplayResponse;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.serializer.Serializer;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author stkniep
 */
public class NjamsTest {

    private Njams instance;

    @Before
    public void createNewInstance() {
        instance = new Njams(new Path(), "", "", new Settings());
    }

    @Test
    public void testSerializer() {
        System.out.println("addSerializer");
        final Serializer<List> expResult = l -> "list";

        instance.addSerializer(ArrayList.class, a -> a.getClass().getSimpleName());
        instance.addSerializer(List.class, expResult);

        String serialized;

        // found ArrayList serializer
        serialized = instance.serialize(new ArrayList<>());
        assertNotNull(serialized);
        assertEquals("ArrayList", serialized);

        // found default string serializer
        serialized = instance.serialize(new HashMap<>());
        assertNotNull(serialized);
        assertEquals("{}", serialized);

        // found list serializer
        serialized = instance.serialize(new LinkedList<>());
        assertNotNull(serialized);
        assertEquals("list", serialized);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void testAddJobWithoutStart() {
        ProcessModel model = new ProcessModel(new Path("PROCESSES"), instance);
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
    public void testOnCorrectSendProjectMessageInstruction() {
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.SEND_PROJECTMESSAGE.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        instance.onInstruction(inst);
        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 0);
        assertEquals("Successfully sent ProjectMessage via NjamsClient", resp.getResultMessage());
    }

    @Test
    public void testOnNoReplyHandlerFoundReplayMessageInstruction() {
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.REPLAY.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        instance.onInstruction(inst);

        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 1);
        assertEquals("Client cannot replay processes. No replay handler is present.", resp.getResultMessage());
    }

    @Test
    public void testOnCorrectReplayMessageInstruction() {
        ReplayHandler replayHandler = (ReplayRequest request) -> {
            ReplayResponse resp = new ReplayResponse();
            resp.setResultCode(0);
            resp.setResultMessage("TestWorked");
            return resp;
        };
        instance.setReplayHandler(replayHandler);
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.REPLAY.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        instance.onInstruction(inst);

        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 0);
        assertEquals("TestWorked", resp.getResultMessage());
    }

    @Test
    public void testOnThrownExceptionReplayMessageInstruction() {
        Instruction inst = new Instruction();
        ReplayHandler replayHandler = (ReplayRequest request) -> {
            throw new RuntimeException("TestException");
        };
        instance.setReplayHandler(replayHandler);
        Request req = new Request();
        req.setCommand(Command.REPLAY.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        instance.onInstruction(inst);

        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 2);
        assertEquals("Error while executing replay: TestException", resp.getResultMessage());
        assertEquals("java.lang.RuntimeException: TestException", inst.getResponseParameterByName("Exception"));
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
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);

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
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);

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
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);

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
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);

        Njams njams = new Njams(new Path("TestPath"), "1.0.0", "SDK", settings);

        njams.start();

        assertEquals("Hello", DataMasking.maskString("Hello"));
    }
}
