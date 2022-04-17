package com.im.njams.sdk.njams;

import com.im.njams.sdk.communication.InstructionListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class NjamsInstructionListeners {

    private final List<InstructionListener> instructionListeners = new ArrayList<>();

    List<InstructionListener> get() {
        return Collections.unmodifiableList(instructionListeners);
    }

    void add(InstructionListener instructionListener) {
        instructionListeners.add(instructionListener);
    }

    void remove(InstructionListener listener) {
        instructionListeners.remove(listener);
    }

    void removeAll() {
        instructionListeners.clear();
    }
}
