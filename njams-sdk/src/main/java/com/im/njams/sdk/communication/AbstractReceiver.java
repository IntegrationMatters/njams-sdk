/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import com.im.njams.sdk.settings.ClientSettings;

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

    private final AtomicBoolean connectBegun = new AtomicBoolean(false);
    private volatile CountDownLatch startupLatch;
    private final AtomicReference<Exception> startupError = new AtomicReference<>();
    private final AtomicBoolean startupTimedOut = new AtomicBoolean(false);

    /**
     * Njams to hold
     */
    protected Njams njams;

    /**
     * The settings used to initialize this receiver.
     */
    protected ClientSettings settings;

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
     * Initializes this receiver with the given settings.
     *
     * @param settings the settings to be used for initialization
     */
    @Override
    public void init(ClientSettings settings) {
        this.settings = settings;
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
    // @Deprecated flags external API consumers only; internal use of Jackson factory is intentional.
    @SuppressWarnings("deprecation")
    protected Instruction parseInstruction(final RawMessage message) throws IOException {
        return JsonSerializerFactory.getFastMapper().readValue(message.getBody(), Instruction.class);
    }

    /**
     * This method should be used to create a connection, and if the startup
     * fails, close all resources. It is called by the
     * {@link #reconnect(Exception) reconnect} method as well as during the initial
     * startup connection ({@link #beginConnect()} / {@link #startWithTimeout(long)}).
     * It should throw an Exception if anything unexpected or unwanted happens.
     */
    public abstract void connect();

    /**
     * Starts the background connect thread immediately. Idempotent — subsequent calls on the same
     * instance have no effect. Call this before {@link #startWithTimeout(long)} to overlap the
     * connection attempt with other application setup work.
     * <p>
     * This method is intended for internal SDK use only.
     *
     * @since 6.0.0
     */
    public void beginConnect() {
        if (!connectBegun.compareAndSet(false, true)) {
            return;
        }
        startupLatch = new CountDownLatch(1);
        LOG.debug("Receiver {}: starting connection attempt.", getName());
        Thread connectThread = new Thread(() -> {
            try {
                connect();
            } catch (Exception e) {
                LOG.debug("Receiver {}: connection attempt failed.", getName(), e);
                startupError.set(e);
                startupLatch.countDown();
                return;
            }
            if (startupTimedOut.get()) {
                LOG.debug("Receiver {}: connection established after the startup timeout had already "
                    + "elapsed; releasing resources.", getName());
                try {
                    stop();
                } catch (Exception e) {
                    LOG.debug("Failed to clean up {} resources after startup timeout", getName(), e);
                }
            } else {
                LOG.debug("Receiver {}: connection established.", getName());
                startupLatch.countDown();
            }
        });
        connectThread.setDaemon(true);
        connectThread.setName("Receiver-Startup-" + getName());
        connectThread.start();
    }

    /**
     * Starts this receiver for the initial connection, waiting at most {@code timeoutMs} milliseconds
     * for {@link #connect()} to complete.
     * <p>
     * Calls {@link #beginConnect()} as its first step (idempotent — a no-op if already called).
     * If the deadline passes before {@link #connect()} completes, or if {@link #connect()} throws,
     * this method sets the connection status to {@link ConnectionStatus#DISCONNECTED}, throws a
     * {@link NjamsSdkRuntimeException}, and returns without triggering the reconnect mechanism.
     * The SDK becomes inactive; it is the caller's responsibility to handle the failure.
     * <p>
     * If the background thread eventually establishes a connection after the timeout has already been
     * signalled, {@link #stop()} is called to release any acquired resources.
     *
     * @param timeoutMs maximum time in milliseconds to wait for the connection
     * @throws NjamsSdkRuntimeException if the timeout elapses before the connection is established,
     *         if {@link #connect()} throws, or if the calling thread is interrupted
     * @since 6.0.0
     */
    @Override
    public void startWithTimeout(long timeoutMs) {
        beginConnect();
        try {
            if (!startupLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                startupTimedOut.set(true);
                connectionStatus = ConnectionStatus.DISCONNECTED;
                LOG.debug("Receiver {}: connection timed out after {} ms.", getName(), timeoutMs);
                throw new NjamsSdkRuntimeException(
                        "Startup timeout: " + getName() + " did not connect within " + timeoutMs + " ms");
            }
        } catch (InterruptedException e) {
            startupTimedOut.set(true);
            Thread.currentThread().interrupt();
            connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException(
                    "Interrupted while waiting for " + getName() + " to connect", e);
        }
        Exception error = startupError.get();
        if (error != null) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("Failed to connect " + getName() + " during startup", error);
        }
    }

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
