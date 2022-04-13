package com.im.njams.sdk;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication.InstructionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NjamsInstructionListeners {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsInstructionListeners.class);

    private final List<InstructionListener> instructionListeners = new ArrayList<>();

    public List<InstructionListener> get() {
        return Collections.unmodifiableList(instructionListeners);
    }

    public void add(InstructionListener instructionListener) {
        instructionListeners.add(instructionListener);
    }

    public void remove(InstructionListener listener) {
        instructionListeners.remove(listener);
    }

    public void removeAll() {
        instructionListeners.clear();
    }

    public void distribute(Instruction instruction) {
        for (InstructionListener listener : get()) {
            try {
                listener.onInstruction(instruction);
            } catch (Exception e) {
                LOG.error("Error in InstructionListener {}", listener.getClass().getSimpleName(), e);
            }
        }
    }
}
