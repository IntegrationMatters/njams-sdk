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
package com.im.njams.sdk.communication.connectable.receiver;


import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.connectable.Connectable;
import com.im.njams.sdk.service.NjamsService;

import java.util.Properties;

/**
 * This interface must be implenmented by a new Receiver for a given
 * communication
 *
 * @author pnientiedt
 */
public interface Receiver extends Connectable, NjamsService {

    /**
     * Set njams instance
     *
     * @param njams instance
     */
    void setNjams(Njams njams);


    /**
     * This function should be called by a implementation of the Receiver class
     * with the previously read instruction
     *
     * @param instruction will be executed for all listeners
     */
    void onInstruction(Instruction instruction);

    /**
     * This method is deprecated. {@link #init(Properties) init(Properties)} should establish the connection.
     */
    @Deprecated
    default void start(){}


}
