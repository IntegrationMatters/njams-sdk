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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * This class should be extended when implementing an new Receiver for a new
 * communication type.
 *
 * @author pnientiedt
 */
public abstract class AbstractReceiver implements Receiver {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractReceiver.class);

    protected ConnectionStatus connectionStatus;

    /**
     * Njams to hold
     */
    protected Njams njams;

    /**
     * Set njams instance
     *
     * @param njams instance
     */
    @Override
    public void setNjams(Njams njams) {
        this.njams = njams;
    }

    /**
     * This function should be called by a implementation of the Receiver class
     * with the previously read instruction.
     *
     * @param instruction which will be handled to all listeners
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

    public abstract void connect();

    /**
     * same as connect(), but no verbose logging.
     */
    public synchronized void reconnect() {
        if (isConnecting() || isConnected()) {
            return;
        }
        while (!isConnected()) {
            try {
                connect();
                LOG.info("Reconnected receiver {}", getName());
            } catch (NjamsSdkRuntimeException e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    return;
                }
            }
        }
    }

    /**
     * Start the new Receiver.
     */
    @Override
    public void start() {
        try {
            connect();
            LOG.debug("Started receiver {}", getName());
        } catch (NjamsSdkRuntimeException e) {
            LOG.error("Could not initialize receiver {}\n. Pushing reconnect task to background.",
                    getName(), e);
            // trigger reconnect
            onException(null);
        }
    }

    @Override
    public abstract void stop();

    public void onException(Exception exception) {
        stop();
        // reconnect
        Thread reconnector = new Thread() {

            @Override
            public void run() {
                reconnect();
            }
        };
        reconnector.setDaemon(true);
        reconnector.setName(String.format("Reconnect %s receiver", getName()));
        reconnector.start();
    }

    public boolean isConnected() {
        return connectionStatus == ConnectionStatus.CONNECTED;
    }

    public boolean isDisconnected() {
        return connectionStatus == ConnectionStatus.DISCONNECTED;
    }

    public boolean isConnecting() {
        return connectionStatus == ConnectionStatus.CONNECTING;
    }

}
