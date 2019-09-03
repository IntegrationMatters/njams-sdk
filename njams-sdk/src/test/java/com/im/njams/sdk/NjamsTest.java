/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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
import com.im.njams.sdk.adapter.messageformat.command.boundary.NjamsInstructionFactory;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.api.adapter.messageformat.command.ReplayInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.api.plugin.replay.ReplayHandler;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
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

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.*;

/**
 *
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
    public void testOnCorrectSendProjectMessageInstruction(){
        instance.start();
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.SEND_PROJECTMESSAGE.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        NjamsInstructionFactory instructionFactory = (NjamsInstructionFactory) instance.getInstructionFactory();
        com.im.njams.sdk.api.adapter.messageformat.command.Instruction wrappedInst = instructionFactory
                .getInstructionOf(inst);
        instance.getInstructionListener().onInstruction(wrappedInst);
        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 0);
        assertEquals("Successfully send ProjectMessage via NjamsClient", resp.getResultMessage());
    }

    @Test
    public void testOnNoReplyHandlerFoundReplayMessageInstruction() {
        instance.start();
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.REPLAY.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        NjamsInstructionFactory instructionFactory = (NjamsInstructionFactory) instance.getInstructionFactory();
        com.im.njams.sdk.api.adapter.messageformat.command.Instruction wrappedInst = instructionFactory
                .getInstructionOf(inst);
        instance.getInstructionListener().onInstruction(wrappedInst);

        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 1);
        assertEquals("Client cannot replay processes. No replay handler is present.", resp.getResultMessage());
    }

    @Test
    public void testOnCorrectReplayMessageInstruction() {
        instance.start();
        ReplayHandler replayHandler = (ReplayInstruction instruction) -> {
            instruction.getResponseWriter().
                    setResultCode(ResultCode.SUCCESS).
                    setResultMessage("TestWorked");
        };
        instance.getPluginStorage().getReplayPlugin().setPluginItem(replayHandler);
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.REPLAY.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        NjamsInstructionFactory instructionFactory = (NjamsInstructionFactory) instance.getInstructionFactory();
        com.im.njams.sdk.api.adapter.messageformat.command.Instruction wrappedInst = instructionFactory
                .getInstructionOf(inst);
        instance.getInstructionListener().onInstruction(wrappedInst);

        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 0);
        assertEquals("TestWorked", resp.getResultMessage());
    }

    @Test
    public void testOnThrownExceptionReplayMessageInstruction() {
        instance.start();
        Instruction inst = new Instruction();
        ReplayHandler replayHandler = (ReplayInstruction instruction) -> {
            throw new RuntimeException("TestException");
        };
        instance.getPluginStorage().getReplayPlugin().setPluginItem(replayHandler);
        Request req = new Request();
        req.setCommand(Command.REPLAY.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        NjamsInstructionFactory instructionFactory = (NjamsInstructionFactory) instance.getInstructionFactory();
        com.im.njams.sdk.api.adapter.messageformat.command.Instruction wrappedInst = instructionFactory
                .getInstructionOf(inst);
        instance.getInstructionListener().onInstruction(wrappedInst);

        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 2);
        assertEquals("Error while executing replay: TestException", resp.getResultMessage());
        assertEquals("java.lang.RuntimeException: TestException", inst.getResponseParameterByName("Exception"));
    }
}
