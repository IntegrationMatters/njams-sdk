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
package com.im.njams.sdk.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class should be extended when implementing an new Receiver for a new
 * communication type.
 *
 * @author pnientiedt/krautenberg
 * @version 4.0.5
 */
public abstract class AbstractReceiver implements Receiver {

    //The Logger
    private static final Logger LOG = LoggerFactory.getLogger(AbstractReceiver.class);
    //The time it needs before a new reconnection is tried after an exception throw.
    protected static final long RECONNECT_INTERVAL = 1000;
    
    //This AtomicInteger is for debugging.
    final AtomicInteger verifyingCounter = new AtomicInteger();

    //The connection status of the receiver
    protected ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;

    /**
     * Njams to hold
     */
    protected Njams njams;

    /**
     * This constructor sets the njams instance for getting the instruction
     * listeners.
     *
     * @param njams the instance that holds the instructionListeners.
     */
    @Override
    public void setNjams(Njams njams) {
        this.njams = njams;
    }

    /**
     * This function should be called by a implementation of the Receiver class
     * with the previously read instruction.
     *
     * @param instruction the instruction that will be handed to all
     * instructionListeners
     */
    @Override
    public void onInstruction(Instruction instruction) {
        if (njams == null) {
            LOG.error("Njams should not be null");
            return;
        }
        if (instruction == null) {
            LOG.error("Instruction should not be null");
            return;
        }
        if (instruction.getRequest() == null || instruction.getRequest().getCommand() == null) {
            LOG.error("Instruction should have a valid request with a command");
            Response response = new Response();
            response.setResultCode(1);
            response.setResultMessage("Instruction should have a valid request with a command");
            instruction.setResponse(response);
            return;
        }
        for (InstructionListener listener : njams.getInstructionListeners()) {
            try {
                listener.onInstruction(instruction);
            } catch (Exception e) {
                LOG.error("Error in InstructionListener {}", listener.getClass().getSimpleName(), e);
            }
        }
        //If response is empty, no InstructionListener found. Set default Response indicating this.
        if (instruction.getResponse() == null) {
            LOG.warn("No InstructionListener for {} found", instruction.getRequest().getCommand());
            Response response = new Response();
            response.setResultCode(1);
            response.setResultMessage("No InstructionListener for " + instruction.getRequest().getCommand() + " found");
            instruction.setResponse(response);
        }
    }

    /**
     * This method should be used to create a connection, and if the startup
     * fails, close all resources. It will be called by the
     * {@link #reconnect() reconnect} method. It should throw an
     * NjamsSdkRuntimeException if anything unexpected or unwanted happens.
     */
    public abstract void connect();

    /**
     * This method tries to establish the connection over and over as long as it
     * not connected. If {@link #connect() connect} throws an exception, the
     * reconnection threads sleeps for {@link #RECONNECT_INTERVAL RECONNECT_INTERVAL} second before trying again to
     * reconnect.
     */
    public synchronized void reconnect() {
        int got = verifyingCounter.incrementAndGet();
        boolean doReconnect = true;
        if (this.isConnecting() || this.isConnected()) {
            doReconnect = false;
        }
        if(got > 1){
            //This is just for debugging.
            LOG.debug("There are to many reconnections at the same time! There are {} method invocations.", got);
        }
        while (!this.isConnected() && doReconnect) {
            try {
                LOG.debug("Trying to reconnect receiver {}", this.getName());
                this.connect();
                LOG.info("Reconnected receiver {}", this.getName());
            } catch (NjamsSdkRuntimeException e) {
                try {
                    //Using Thread.sleep because this.wait would release the lock for this object, Thread.sleep doesn't.
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    LOG.error("The reconnecting thread was interrupted!", e1);
                    doReconnect = false;
                }
            }
        }
        verifyingCounter.decrementAndGet();
    }

    /**
     * This method starts the Receiver. It tries to establish the connection,
     * and if it fails, calls the method
     * {@link #onException(Exception) onException}.
     */
    @Override
    public void start() {
        try {
            this.connect();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Started receiver {}", this.getName());
            }
        } catch (NjamsSdkRuntimeException e) {
            LOG.error("Could not initialize receiver {}\n. Pushing reconnect task to background.",
                    this.getName(), e);
            // trigger reconnect
            this.onException(null);
        }
    }

    /**
     * This method should be used to stop the receiver and close all resources
     * that it uses.
     */
    @Override
    public abstract void stop();

    /**
     * This method is used to start a reconnector thread.
     *
     * @param exception the exception that caused this method invokation.
     */
    public void onException(Exception exception) {
        this.stop();
        // reconnect
        Thread reconnector = new Thread(() -> {
            reconnect();
        });
        reconnector.setDaemon(true);
        reconnector.setName(String.format("Reconnect %s receiver", this.getName()));
        reconnector.start();
    }

    /**
     * This method returns wether the Receiver is connected or not.
     *
     * @return true, if Receiver is connected, otherwise false
     */
    public boolean isConnected() {
        return this.connectionStatus == ConnectionStatus.CONNECTED;
    }

    /**
     * This method returns wether the Receiver is disconnected or not.
     *
     * @return true, if Receiver is disconnected, otherwise false
     */
    public boolean isDisconnected() {
        return this.connectionStatus == ConnectionStatus.DISCONNECTED;
    }

    /**
     * This method returns wether the Receiver is connecting or not.
     *
     * @return true, if Receiver is connecting, otherwise false
     */
    public boolean isConnecting() {
        return this.connectionStatus == ConnectionStatus.CONNECTING;
    }

}
