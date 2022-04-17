package com.im.njams.sdk;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class NjamsInstructionListenerIT {

    private Njams njams;
    private Instruction instruction;
    private InstructionListener acceptingListener;
    private InstructionListener rejectingListener;
    private InstructionListener increasingListener;

    @Before
    public void setUp() throws Exception {
        njams = new Njams(new Path(), "SDK", new Settings());

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
        njams.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse(), is(nullValue()));
    }

    @Test
    public void onInstruction_afterAcceptingListenerHasBeenAdded_instructionWasAccepted(){
        njams.addInstructionListener(acceptingListener);

        njams.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse().getResultMessage(), is("Accepted"));
    }

    @Test
    public void onInstruction_afterRejectingListenerHasBeenAdded_instructionWasRejected(){
        njams.addInstructionListener(rejectingListener);

        njams.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse().getResultMessage(), is("Rejected"));
    }

    @Test
    public void onInstruction_acceptingAndRejectingListenerAdded_theLaterListenerSetsTheResponse(){
        njams.addInstructionListener(acceptingListener);
        njams.addInstructionListener(rejectingListener);

        njams.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse().getResultMessage(), is("Rejected"));
    }

    @Test
    public void onInstruction_increasingInstructionListener_increasesResultCode(){
        njams.addInstructionListener(increasingListener);

        njams.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse().getResultCode(), is(1));
    }

    @Test
    public void onInstruction_increasingInstructionListener_increasesResultCodeWithEachCall(){
        njams.addInstructionListener(increasingListener);

        njams.getNjamsReceiver().distribute(instruction);
        njams.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse().getResultCode(), is(2));
    }

    @Test
    public void onInstruction_increasingInstructionListener_canBeAddedMultipleTimes_andWillThereforeBeCalledMultipleTimes(){
        njams.addInstructionListener(increasingListener);
        njams.addInstructionListener(increasingListener);

        njams.getNjamsReceiver().distribute(instruction);

        assertThat(instruction.getResponse().getResultCode(), is(2));
    }
}
