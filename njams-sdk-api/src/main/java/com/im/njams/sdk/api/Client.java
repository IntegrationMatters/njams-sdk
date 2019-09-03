/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.api;

import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.InstructionFactory;
import com.im.njams.sdk.api.communication.Communication;
import com.im.njams.sdk.api.plugin.Plugin;
import com.im.njams.sdk.api.plugin.PluginStorage;

/**
 * This interface is the mediator between all other interfaces that communicate with each other.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public interface Client {

    /**
     * Starts the client; It will initiate the connections and start processing.
     *
     * @return true, if everything started successfully, otherwise false
     */
    boolean start();

    /**
     * Returns a facility to create {@link Instruction instruction} instances.
     *
     * @return a factory to produce {@link Instruction instructions}.
     */
    InstructionFactory getInstructionFactory();

    /**
     * Returns the {@link Communication communication} that is responsible for the senders and receivers for this
     * client.
     *
     * @return the communication of the client.
     */
    Communication getCommunication();

//    ClientCondition getClientCondition();

    /**
     * Returns the {@link PluginStorage pluginStorage} that holds the {@link Plugin plugins} that can be used by this
     * client.
     *
     * @return the pluginStorage with all plugins of this client.
     */
    PluginStorage getPluginStorage();

//    DataMasking getDataMasking();

    /**
     * Stops a client; It will stop the processing and release the connections.
     *
     * @return true, if everything stopped successfully, otherwise false
     */
    boolean stop();
}
