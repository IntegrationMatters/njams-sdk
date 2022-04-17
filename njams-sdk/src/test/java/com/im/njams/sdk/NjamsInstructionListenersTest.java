package com.im.njams.sdk;

import com.faizsiegeln.njams.messageformat.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

public class NjamsInstructionListenersTest {

    private Njams njams;
    private InstructionListener instructionListenerDummy;

    @Before
    public void setUp() throws Exception {
        njams = new Njams(new Path(), "SDK", new Settings());

        instructionListenerDummy = (i) -> {};
    }

    @Test
    public void instructionListeners_afterInitialization_areEmpty(){
        assertThat(njams.getInstructionListeners(), is(empty()));
    }

    @Test
    public void addInstructionListeners_onNjams_canBeRetrieved(){
        njams.addInstructionListener(instructionListenerDummy);

        assertThat(njams.getInstructionListeners(), contains(instructionListenerDummy));
    }

    @Test
    public void removeInstructionListeners_onNjams_removesInstructionListener(){
        njams.addInstructionListener(instructionListenerDummy);
        njams.removeInstructionListener(instructionListenerDummy);

        assertThat(njams.getInstructionListeners(), is(empty()));
    }
}
