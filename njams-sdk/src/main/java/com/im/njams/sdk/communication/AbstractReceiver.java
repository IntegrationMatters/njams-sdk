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
import com.im.njams.sdk.settings.ClientSettings;

/**
 * This class should be extended when implementing an new Receiver for a new
 * communication type.
 *
 * @author pnientiedt/krautenberg
 * @version 4.0.6
 */
public abstract class AbstractReceiver implements Receiver, SenderRecoveryListener {

    //The Logger
    private static final Logger LOG = LoggerFactory.getLogger(AbstractReceiver.class);
    //The time it needs before a new reconnection is tried after an exception throw.
    protected static final int INIT_RECONNECT_INTERVAL = 500;
    protected static final int MAX_RECONNECT_INTERVAL = 60_000;

    //This AtomicInteger is for debugging.
    final AtomicInteger verifyingCounter = new AtomicInteger();

    //The connection status of the receiver
    protected ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;

    private AtomicInteger reconnectIntervalIncreasing = new AtomicInteger(INIT_RECONNECT_INTERVAL * 10 + 1);

    private final AtomicBoolean connectBegun = new AtomicBoolean(false);

    private volatile Thread startupConnectThread;
    private volatile Thread reconnectThread;

    /**
     * Njams to hold
     */
    protected Njams njams;

    /**
     * The settings used to initialize this receiver.
     */
    protected ClientSettings settings;

    /**
     * This receiver's own connection-lifecycle state (reconnect counting, shutdown, "was ever connected").
     * Entirely independent of any sender group's coordinator — this receiver's connection lifecycle is never
     * shared with or affected by a sender's. Never reassigned after construction, hence {@code final}.
     */
    private final ConnectionCoordinator coordinator = new ConnectionCoordinator();

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
     * startup connection ({@link #beginConnect()}).
     * It should throw an Exception if anything unexpected or unwanted happens.
     */
    public abstract void connect();

    /**
     * Starts the background connect thread immediately, if not already started. Idempotent — subsequent calls
     * on the same instance have no effect.
     * <p>
     * The connection attempt runs entirely in the background and never blocks the caller: on success it marks
     * this receiver's {@link ConnectionCoordinator} connected — or, if {@link #setShouldShutdown(boolean)} was
     * called while the connect was still in flight, releases the now-unwanted connection again via
     * {@link #stop()} instead; on failure it hands off to {@link #reconnect(Exception)} unconditionally. Either
     * way, this receiver's connection outcome is never awaited and never gates or fails {@code Njams.start()}.
     * <p>
     * This method is intended for internal SDK use only.
     *
     * @since 6.0.0
     */
    public void beginConnect() {
        if (!connectBegun.compareAndSet(false, true)) {
            return;
        }
        LOG.debug("Receiver {}: starting connection attempt.", getName());
        startupConnectThread = new Thread(() -> {
            try {
                connect();
                coordinator.markStartupConnected();
            } catch (Exception e) {
                LOG.debug("Receiver {}: connection attempt failed.", getName(), e);
                reconnect(e);
                return;
            }
            if (coordinator.shouldShutdown()) {
                LOG.debug("Receiver {}: connection established after shutdown had already been requested; "
                    + "releasing resources.", getName());
                try {
                    stop();
                } catch (Exception e) {
                    LOG.debug("Failed to clean up {} resources after shutdown", getName(), e);
                }
            } else {
                LOG.debug("Receiver {}: connection established.", getName());
            }
        });
        startupConnectThread.setDaemon(true);
        startupConnectThread.setName("Receiver-Startup-" + getName());
        startupConnectThread.start();
    }

    /**
     * This method tries to establish the connection over and over as long as it
     * not connected. If {@link #connect() connect} throws an exception, the
     * reconnection threads sleeps for
     * {@link #INIT_RECONNECT_INTERVAL} second before trying again
     * to reconnect.
     * <p>
     * <strong>Blocks the calling thread</strong> for the entire retry duration — unlike the sender side, where the
     * group's reconnect loop runs on its own dedicated thread owned by the sender pool, this method runs the retry
     * loop synchronously and, being a {@code synchronized} instance method, holds this receiver's monitor for as
     * long as the loop runs (including any blocking {@link #connect()} call and the backoff sleep between
     * attempts). Callers that need to keep running should invoke this from a background thread themselves (see
     * {@link #onException(Exception)}).
     *
     * @param ex the exception that initiated the reconnect
     */
    public synchronized void reconnect(Exception ex) {
        if (coordinator.shouldShutdown()) {
            LOG.debug("Receiver {}: shutdown requested; not reconnecting.", getName());
            return;
        }
        int got = verifyingCounter.incrementAndGet();
        boolean doReconnect = true;
        if (isConnecting() || isConnected()) {
            doReconnect = false;
        } else {
            coordinator.beginReconnect();
            LOG.warn("Receiver connection lost. The client will not receive any commands from the server "
                + "until reconnected.");
            if (LOG.isDebugEnabled() && ex != null) {
                LOG.debug("Receiver reconnect triggered by: {}", ex.toString());
            }
        }
        if (got > 1) {
            //This is just for debugging.
            LOG.debug("There are to many reconnections at the same time! There are {} method invocations.", got);
        }
        if (doReconnect) {
            // Only assign the field once the loop below is actually about to run (and therefore actually needs
            // to be interruptible by cancelReconnect()) — not unconditionally at the top of this method. Doing it
            // unconditionally would leave reconnectThread aliasing the calling thread forever on every early-return
            // path above (already connecting/connected, or shutting down), including calls that never intended to
            // start a reconnect loop at all (e.g. a direct test call, or a foreign caller of this public method) —
            // a stale alias that a later cancelReconnect() would then wrongly interrupt.
            reconnectThread = Thread.currentThread();
        }
        try {
            while (!isConnected() && doReconnect && !coordinator.shouldShutdown()) {
                LOG.debug("Next try to reconnect receivers.");
                try {
                    connect();
                    if (coordinator.markConnected()) {
                        LOG.info("Receiver reconnected. Handling server commands resumed.");
                        resetReconnectInterval();
                    }
                } catch (NjamsSdkRuntimeException e) {
                    try {
                        //Using Thread.sleep because this.wait would release the lock for this object, Thread.sleep doesn't.
                        Thread.sleep(nextReconnectInterval());
                    } catch (InterruptedException e1) {
                        LOG.debug("The reconnecting thread was interrupted.", e1);
                        doReconnect = false;
                    }
                }
            }
        } finally {
            // Clear the field once the loop exits, but only if it still references this thread. reconnect()
            // itself is synchronized, so no second thread can be executing this method body concurrently — but
            // onException() creates-and-assigns a new reconnect thread to this same field before starting it,
            // and that new thread may be waiting on this method's monitor and then run its own
            // "if (doReconnect) { reconnectThread = ... }" assignment after this thread's loop exits but before
            // (or while) this finally block runs. Clearing unconditionally would then wrongly un-alias that
            // other, now-current reconnect attempt.
            if (reconnectThread == Thread.currentThread()) {
                reconnectThread = null;
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
        reconnectThread = new Thread(() -> reconnect(exception));
        reconnectThread.setDaemon(true);
        reconnectThread
            .setName(String.format("Receiver-Sender-Reconnector-Thread[%s/%d]", getName(),
                System.identityHashCode(this)));
        reconnectThread.start();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Cycles this receiver's connection: a receiver that passively waits for messages can miss a connection loss
     * entirely, so the sender group's recovery is taken as the moment to re-verify. Does nothing while this
     * receiver is shutting down, has never been connected, is currently connecting, or is already running its own
     * reconnect — in each of those cases its own lifecycle already owns its connection state.
     *
     * @since 6.0.0
     */
    @Override
    public void onSenderGroupRecovered() {
        // wasEverConnected() alone is not enough: it is set by beginConnect()/reconnect(), but not by a plain
        // start(), so a receiver started directly would look like it had never connected.
        final boolean neverConnected = !coordinator.wasEverConnected() && !isConnected();
        if (coordinator.shouldShutdown() || neverConnected || isConnecting() || isReconnectInFlight()) {
            LOG.debug("Receiver {}: ignoring the sender group's recovery; this receiver's own connection "
                + "lifecycle already owns its state.", getName());
            return;
        }
        LOG.info("Receiver {}: cycling the connection because the sender group recovered from a connection "
            + "outage.", getName());
        // Dispatched onto its own thread rather than calling onException(...) inline: the caller here is the
        // sender group's own single reconnect thread, and that thread must not block on this receiver's own
        // stop()/reconnect() cycle (which can block for a while, e.g. closing an already-dead connection) —
        // doing so would leave the sender group unable to elect a reconnector for any later, unrelated outage
        // until this cycle finishes.
        Thread cycler = new Thread(() -> {
            try {
                onException(new NjamsSdkRuntimeException(
                    "Cycling the receiver after the sender group recovered from a connection outage"));
            } catch (RuntimeException | Error e) {
                // Otherwise this would only reach the JVM's default uncaught-exception handler, since this runs
                // on its own thread rather than the caller's (see the comment above).
                LOG.error("Receiver {}: failed to cycle the connection after the sender group recovered.",
                    getName(), e);
            }
        });
        cycler.setDaemon(true);
        cycler.setName(String.format("Receiver-Recovery-Cycle-Thread[%s/%d]", getName(),
            System.identityHashCode(this)));
        cycler.start();
    }

    /** @return {@code true} while this receiver's own reconnect loop is running. */
    private boolean isReconnectInFlight() {
        final Thread rc = reconnectThread;
        return rc != null && rc.isAlive();
    }

    /**
     * Sets the shutdown flag on this receiver's own, independent {@link ConnectionCoordinator}, stopping this
     * receiver's reconnect loop. This coordinator is never shared with any sender group, so calling this has no
     * effect on any sender group, and a sender group shutting down has no effect here either.
     *
     * @param shutdown {@code true} to begin shutdown for this receiver.
     * @since 6.0.0
     */
    public void setShouldShutdown(boolean shutdown) {
        coordinator.setShouldShutdown(shutdown);
    }

    /**
     * Interrupts the startup connect thread and any in-progress reconnect thread of this receiver, so a blocking
     * {@link #connect()} is cancelled promptly on shutdown rather than only at the next loop check.
     *
     * @since 6.0.0
     */
    public void cancelReconnect() {
        final Thread startup = startupConnectThread;
        if (startup != null) {
            startup.interrupt();
        }
        final Thread rc = reconnectThread;
        if (rc != null) {
            rc.interrupt();
        }
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
