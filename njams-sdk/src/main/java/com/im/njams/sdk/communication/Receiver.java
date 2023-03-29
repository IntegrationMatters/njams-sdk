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
package com.im.njams.sdk.communication;

import java.util.Properties;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.utils.ClasspathValidator;

/**
 * This interface must be implenmented by a new Receiver for a given
 * communication
 *
 * @author pnientiedt
 */
public interface Receiver extends ClasspathValidator {

    /**
     * Set njams instance
     *
     * @param njams instance
     */
    void setNjams(Njams njams);

    /**
     * The implementation should return its name here, by which it can be
     * identified. This name will be used as value in the
     * CommunicationConfiguration via the Key
     * {@value NjamsSettings#PROPERTY_COMMUNICATION}
     *
     * @return the name of the receiver implementation
     */
    String getName();

    /**
     * This implementation will initialize itself via the given Properties
     *
     * @param properties to be used for initialization
     */
    void init(Properties properties);

    /**
     * This function should be called by a implementation of the Receiver class
     * with the previously read instruction
     *
     * @param instruction will be executed for all listeners
     */
    void onInstruction(Instruction instruction);

    /**
     * Start the new Receiver
     */
    void start();

    /**
     * Stop the new Receiver
     */
    void stop();

}
