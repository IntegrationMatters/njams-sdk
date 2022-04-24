/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.njams.NjamsFeatures;
import com.im.njams.sdk.njams.NjamsJobs;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ReplayInstructionListenerTest {

    private NjamsJobs njamsJobs;

    @Before
    public void setUp(){
        NjamsFeatures features = new NjamsFeatures();
        njamsJobs = new NjamsJobs(null, null, features, null);
    }

    @Test
    public void testOnNoReplyHandlerFoundReplayMessageInstruction() {
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.REPLAY.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        njamsJobs.onInstruction(inst);

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
        njamsJobs.setReplayHandler(replayHandler);
        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.REPLAY.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        njamsJobs.onInstruction(inst);

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
        njamsJobs.setReplayHandler(replayHandler);
        Request req = new Request();
        req.setCommand(Command.REPLAY.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        njamsJobs.onInstruction(inst);

        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 2);
        assertEquals("Error while executing replay: TestException", resp.getResultMessage());
        assertEquals("java.lang.RuntimeException: TestException", inst.getResponseParameterByName("Exception"));
    }
}