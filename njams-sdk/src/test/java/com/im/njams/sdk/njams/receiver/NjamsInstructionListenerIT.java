/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
 *
 */

package com.im.njams.sdk.njams.receiver;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsFactory;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.njams.util.NjamsFactoryUtils;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class NjamsInstructionListenerIT {

    private NjamsFactory njamsFactory;
    private Njams njams;
    private Instruction instruction;
    private InstructionListener acceptingListener;
    private InstructionListener rejectingListener;
    private InstructionListener increasingListener;

    @Before
    public void setUp() throws Exception {
        njamsFactory = NjamsFactoryUtils.createMinimalNjamsFactory();
        njams = new Njams(njamsFactory);

        instruction = new Instruction();
        acceptingListener = (i) -> {
            Response response = new Response();
            response.setResultMessage("Accepted");
            i.setResponse(response);
        };

        rejectingListener = (i) -> {
            Response response = new Response();
            response.setResultMessage("Rejected");
            i.setResponse(response);
        };

        increasingListener = (i) -> {
            Response response = i.getResponse();
            if(response == null) {
                response = new Response();
                response.setResultCode(1);
                i.setResponse(response);
            }else{
                response.setResultCode(response.getResultCode() + 1);
            }
        };
    }

    @Test
    public void instructionResponse_afterInitialization_isNull(){
        assertThat(instruction.getResponse(), is(nullValue()));
    }

    @Test
    public void onInstruction_afterNoListenerHasBeenAdded_doesNotDoAnything(){
        njamsFactory.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse(), is(nullValue()));
    }

    @Test
    public void onInstruction_afterAcceptingListenerHasBeenAdded_instructionWasAccepted(){
        njams.addInstructionListener(acceptingListener);

        njamsFactory.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse().getResultMessage(), is("Accepted"));
    }

    @Test
    public void onInstruction_afterRejectingListenerHasBeenAdded_instructionWasRejected(){
        njams.addInstructionListener(rejectingListener);

        njamsFactory.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse().getResultMessage(), is("Rejected"));
    }

    @Test
    public void onInstruction_acceptingAndRejectingListenerAdded_theLaterListenerSetsTheResponse(){
        njams.addInstructionListener(acceptingListener);
        njams.addInstructionListener(rejectingListener);

        njamsFactory.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse().getResultMessage(), is("Rejected"));
    }

    @Test
    public void onInstruction_increasingInstructionListener_increasesResultCode(){
        njams.addInstructionListener(increasingListener);

        njamsFactory.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse().getResultCode(), is(1));
    }

    @Test
    public void onInstruction_increasingInstructionListener_increasesResultCodeWithEachCall(){
        njams.addInstructionListener(increasingListener);

        njamsFactory.getNjamsReceiver().distribute(instruction);
        njamsFactory.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse().getResultCode(), is(2));
    }

    @Test
    public void onInstruction_increasingInstructionListener_canBeAddedMultipleTimes_andWillThereforeBeCalledMultipleTimes(){
        njams.addInstructionListener(increasingListener);
        njams.addInstructionListener(increasingListener);

        njamsFactory.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse().getResultCode(), is(2));
    }
}
