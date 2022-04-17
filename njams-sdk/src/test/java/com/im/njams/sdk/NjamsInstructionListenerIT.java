package com.im.njams.sdk;

import com.faizsiegeln.njams.messageformat.command.Instruction;
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

    @Before
    public void setUp() throws Exception {
        njams = new Njams(new Path(), "SDK", new Settings());

        instruction = new Instruction();
        acceptingListener = (i) -> {
            Response response = new Response();
            response.setResultMessage("Accepted");
            i.setResponse(response);
        };
    }

    @Test
    public void instructionResponse_afterInitialization_isNull(){
        assertThat(instruction.getResponse(), is(nullValue()));
    }

}
