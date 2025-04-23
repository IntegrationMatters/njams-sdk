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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Njams.Feature;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.fragments.RawMessage;

/**
 * This class should be extended when implementing an new Receiver for a new
 * communication type.
 *
 * @author pnientiedt/krautenberg
 * @version 4.0.6
 */
public abstract class AbstractReceiver implements Receiver {

    //The Logger
    private static final Logger LOG = LoggerFactory.getLogger(AbstractReceiver.class);
    //The time it needs before a new reconnection is tried after an exception throw.
    protected static final int INIT_RECONNECT_INTERVAL = 500;
    protected static final int MAX_RECONNECT_INTERVAL = 60_000;

    //This AtomicInteger is for debugging.
    final AtomicInteger verifyingCounter = new AtomicInteger();

    //The connection status of the receiver
    protected ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;

    private static final AtomicBoolean hasConnected = new AtomicBoolean(false);

    private static final AtomicInteger connecting = new AtomicInteger(0);

    private AtomicInteger reconnectIntervalIncreasing = new AtomicInteger(INIT_RECONNECT_INTERVAL * 10 + 1);

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
     *                    instructionListeners
     */
    @Override
    public void onInstruction(Instruction instruction) {
        LOG.debug("Received instruction: {}", instruction == null ? "null" : instruction.getCommand());
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
        //Extend your request here. If something doesn't work as expected,
        //you can return a response that will be sent back to the server without further processing.
        Response exceptionResponse = extendRequest(instruction.getRequest());
        if (exceptionResponse != null) {
            //Set the exception response
            instruction.setResponse(exceptionResponse);
        } else {
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
                response.setResultMessage(
                    "No InstructionListener for " + instruction.getRequest().getCommand() + " found");
                instruction.setResponse(response);
            }
        }
    }

    /**
     * This method is for extending the incoming request if it is needed for the
     * concrete receiver.
     *
     * @param request request to extend
     * @return A response that will be sent back without further processing
     * of the request. If null is returned (as default), the request
     * has been extended successfully and can be processed normally.
     */
    protected Response extendRequest(Request request) {
        //Doesn't extend the request as default.
        //This can be used by the subclasses to alter the request.
        return null;
    }

    /**
     * This method tries to extract the {@link Instruction} out of the provided message. It
     * maps the Json string to an {@link Instruction} object.
     *
     * @param message the Json Message
     * @return the Instruction object that was extracted or null, if no valid
     * instruction was found or it could be parsed to an instruction object.
     * @throws IOException if the {@link Instruction} could not be extracted.
     */
    protected Instruction parseInstruction(final RawMessage message) throws IOException {
        return JsonSerializerFactory.getFastMapper().readValue(message.getBody(), Instruction.class);
    }

    /**
     * This method should be used to create a connection, and if the startup
     * fails, close all resources. It will be called by the
     * {@link #reconnect(Exception) reconnect} method. It should throw an
     * Exception if anything unexpected or unwanted happens.
     */
    public abstract void connect();

    /**
     * This method tries to establish the connection over and over as long as it
     * not connected. If {@link #connect() connect} throws an exception, the
     * reconnection threads sleeps for
     * {@link #INIT_RECONNECT_INTERVAL} second before trying again
     * to reconnect.
     *
     * @param ex the exception that initiated the reconnect
     */
    public synchronized void reconnect(Exception ex) {
        int got = verifyingCounter.incrementAndGet();
        boolean doReconnect = true;
        if (isConnecting() || isConnected()) {
            doReconnect = false;
        } else {
            synchronized (hasConnected) {
                hasConnected.set(false);
                if (LOG.isInfoEnabled() && ex != null) {
                    if (ex.getCause() == null) {
                        LOG.info("Initialized receiver reconnect, because of : {}", ex.toString());
                    } else {
                        LOG.info("Initialized receiver reconnect, because of : {}, {}", ex.toString(),
                            ex.getCause().toString());
                    }
                }
                LOG.debug("{} receivers are reconnecting now.", connecting.incrementAndGet());
            }
        }
        if (got > 1) {
            //This is just for debugging.
            LOG.debug("There are to many reconnections at the same time! There are {} method invocations.", got);
        }
        while (!isConnected() && doReconnect) {
            LOG.debug("Next try to reconnect receivers.");
            try {
                connect();
                synchronized (hasConnected) {
                    if (!hasConnected.get()) {
                        LOG.info("Reconnected receiver {}", getName());
                        hasConnected.set(true);
                        resetReconnectInterval();
                    }
                    LOG.debug("{} receivers still need to reconnect.", connecting.decrementAndGet());
                }
            } catch (NjamsSdkRuntimeException e) {
                try {
                    //Using Thread.sleep because this.wait would release the lock for this object, Thread.sleep doesn't.

                    Thread.sleep(nextReconnectInterval());
                } catch (InterruptedException e1) {
                    LOG.error("The reconnecting thread was interrupted!", e1);
                    doReconnect = false;
                }
            }
        }
        LOG.debug("Receiver reconnect loop ended!");
        verifyingCounter.decrementAndGet();
    }

    private void resetReconnectInterval() {
        reconnectIntervalIncreasing.set(INIT_RECONNECT_INTERVAL * 10);
    }

    /**
     * Try 10 times with the same value before increasing exponentially and then trying 10 times again
     * until {@link #MAX_RECONNECT_INTERVAL} is reached.
     *
     * @return
     */
    private int nextReconnectInterval() {
        int reconnect = reconnectIntervalIncreasing.get();
        if (reconnect / 10 >= MAX_RECONNECT_INTERVAL) {
            return MAX_RECONNECT_INTERVAL;
        }
        if (reconnect % 10 == 0) {
            reconnect = Math.min(MAX_RECONNECT_INTERVAL * 10, (reconnect / 10 - 1) * 20 + 1);
        } else {
            reconnect++;
        }
        reconnectIntervalIncreasing.set(reconnect);
        return (reconnect - 1) / 10;
    }

    /**
     * This method starts the Receiver. It tries to establish the connection,
     * and if it fails, calls the method
     * {@link #onException(Exception) onException}.
     */
    @Override
    public void start() {
        try {
            connect();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Started receiver {}", getName());
            }
        } catch (Exception e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            LOG.error("Could not initialize receiver {}. Pushing reconnect task to background.", getName(), e);
            // trigger reconnect
            onException(e);
        }
    }

    /**
     * This method is used to start a reconnect thread.
     *
     * @param exception the exception that caused this method invocation.
     */
    public void onException(Exception exception) {
        stop();
        // reconnect
        Thread reconnector = new Thread(() -> reconnect(exception));
        reconnector.setDaemon(true);
        reconnector
            .setName(String.format("Receiver-Sender-Reconnector-Thread[%s/%d]", getName(),
                System.identityHashCode(this)));
        reconnector.start();
    }

    /**
     * This method returns whether the receiver is connected or not.
     *
     * @return true, if Receiver is connected, otherwise false
     */
    public boolean isConnected() {
        return connectionStatus == ConnectionStatus.CONNECTED;
    }

    /**
     * This method returns whether the receiver is disconnected or not.
     *
     * @return true, if Receiver is disconnected, otherwise false
     */
    public boolean isDisconnected() {
        return connectionStatus == ConnectionStatus.DISCONNECTED;
    }

    /**
     * This method returns whether the receiver is connecting or not.
     *
     * @return true, if Receiver is connecting, otherwise false
     */
    public boolean isConnecting() {
        return connectionStatus == ConnectionStatus.CONNECTING;
    }

    /**
     * Returns <code>true</code> only if the given instruction is a {@link Command#GET_REQUEST_HANDLER} command and
     * the given target client does not support the {@link Feature#CONTAINER_MODE} feature.
     * @param instruction The instruction to check.
     * @param targetClient The client that shall receive the instruction.
     * @return <code>false</code> in all other cases.
     */
    protected static boolean suppressGetRequestHandlerInstruction(Instruction instruction, Njams targetClient) {
        if (Command.GET_REQUEST_HANDLER == Command.getFromInstruction(instruction) && !targetClient.isContainerMode()) {
            LOG.debug("Ignoring command {} because feature {} is disabled for target client: {}",
                Command.GET_REQUEST_HANDLER, Feature.CONTAINER_MODE, targetClient.getClientPath());

            return true;
        }
        return false;
    }

}
