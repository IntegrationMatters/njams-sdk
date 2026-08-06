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

import static com.im.njams.sdk.NjamsSettings.*;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.factories.ThreadFactoryBuilder;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * This class enforces the maxQueueLength setting. It uses the
 * maxQueueLengthHandler to enforce the discardPolicy, if the maxQueueLength is
 * exceeded all message sending is funneled through this class, which creates
 * and uses a pool of senders to multi-thread message sending
 *
 * @author hsiegeln
 * @version 4.0.6
 */
public class NjamsSender {

    private static class NjamsSharedSender extends NjamsSender {
        private final Object lock = new Object();
        private int usage = 0;

        private NjamsSharedSender(ClientSettings settings) {
            super(settings);
        }

        @Override
        public void close() {
            // do not close the singleton before shutdown
            boolean doClose;
            synchronized (lock) {
                doClose = --usage < 1;
            }
            if (doClose) {
                super.close();
                LOG.info("Closed shared sender instance.");
            }

        }

        private void take() {
            synchronized (lock) {
                usage++;
            }
        }

        private boolean isDestroyed() {
            synchronized (lock) {
                return usage < 1;
            }
        }

    }

    //The logger to log messages.
    private static final Logger LOG = LoggerFactory.getLogger(NjamsSender.class);
    private static NjamsSharedSender sharedInstance = null;
    //The senderPool where the senders will be saved.
    protected SenderPool senderPool = null;

    //The executor Threadpool that send the messages to the right senders.
    protected ThreadPoolExecutor executor = null;

    //The settings will be used for the name and max-queue-length
    protected final ClientSettings settings;

    //The name for the executor threads.
    protected final String name;

    private MessageDebugDumper debugDumper = new MessageDebugDumper();

    /** The single sender pre-warmed at startup; held until start() awaits it, then returned to the pool. */
    private volatile AbstractSender startupSender;

    public NjamsSender() {
        settings = null;
        name = "defaultSender";
    }

    /**
     * This constructor initializes a NjamsSender. It saves
     * the settings and gets the name for the executor threads from the settings
     * with the key: njams.sdk.communication (or its alternative njams.sdk.communication.type).
     *
     * @param settings the setting where some settings will be taken from.
     */
    public NjamsSender(ClientSettings settings) {
        this.settings = settings;
        name = settings.getPropertyWithAlternativeKey(
                NjamsSettings.PROPERTY_COMMUNICATION, NjamsSettings.PROPERTY_COMMUNICATION_TYPE);
        init();
    }

    /**
     * Returns the one shared sender instance. On first access, the instance is lazily created. All later access will
     * get the same instance until the instance has closed. Then a new instance is created if required.
     * Calling this method tracks usage of the sender instance. I.e., after <i>taking</i> a sender, it must be
     * {@link #close()}d to return the instance and allow the implementation to keep track of usage. Calling
     * {@link #close()} does not really close the actual sender as long as it is still being used.
     * Only when it is no longer used (close has been called as often as it has been taken), the real sender instance
     * will be closed finally.
     *
     * @param settings Only used if a new instance needs to be created.
     * @return The shared sender instance as explained above.
     */
    public static synchronized NjamsSender takeSharedSender(ClientSettings settings) {
        if (sharedInstance == null || sharedInstance.isDestroyed()) {
            sharedInstance = new NjamsSharedSender(settings);
        }
        sharedInstance.take();
        LOG.debug("Providing shared sender instance (used {} times)", sharedInstance.usage);
        return sharedInstance;
    }

    /**
     * This method initializes a CommunicationFactory, a ThreadPoolExecutor and
     * a SenderPool using the settings provided at construction time.
     */
    public void init() {
        int minSenderThreads = (int) settings.getLong(PROPERTY_MIN_SENDER_THREADS, 1);
        int maxSenderThreads = (int) settings.getLong(PROPERTY_MAX_SENDER_THREADS, 8);
        int maxQueueLength = (int) settings.getLong(PROPERTY_MAX_QUEUE_LENGTH, 8);
        long idleTime = settings.getLong(PROPERTY_SENDER_THREAD_IDLE_TIME, 10000);
        LOG.debug("Init thread pool (min={}, max={}, queue={}, idle={})", minSenderThreads, maxSenderThreads,
            maxQueueLength, idleTime);
        validateThreadPool(minSenderThreads, maxSenderThreads, maxQueueLength, idleTime);
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
            .setNamePrefix(getName() + "-Sender-Thread").setDaemon(true).build();
        final CommunicationFactory communicationFactory = new CommunicationFactory(settings);
        senderPool = new SenderPool(communicationFactory, new ConnectionCoordinator());
        executor = new ThreadPoolExecutor(minSenderThreads, maxSenderThreads, idleTime, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(maxQueueLength), threadFactory,
            new MaxQueueLengthHandler(settings, senderPool::isConnectionFailure));
        debugDumper = new MessageDebugDumper(settings);
    }

    private void validateThreadPool(int minThreads, int maxThreads, int maxQueueLen, long idleTime) {
        if (minThreads < 1) {
            throw new IllegalArgumentException("Minimum threads must be >0");
        }
        if (maxThreads < minThreads) {
            throw new IllegalArgumentException("Maximum threads must be >= minimum threads");
        }
        if (idleTime < 0) {
            throw new IllegalArgumentException("Idle time must be >0");
        }
    }

    /**
     * This method starts a thread that sends the message to a sender in the
     * senderpool.
     *
     * @param msg the message that will be send to the server.
     * @param clientSessionId The current client-session ID of the {@link Njams} instance sending this message
     */
    public void send(CommonMessage msg, String clientSessionId) {
        if (executor.isShutdown() || executor.isTerminating()) {
            return;
        }
        LOG.trace("Sending {}", msg);
        debugDumper.dump(msg, clientSessionId);
        executor.execute(() -> {
            AbstractSender sender = null;
            try {
                sender = senderPool.get();
                if (sender == null) {
                    throw new NullPointerException("No sender available");
                }
                sender.send(msg, clientSessionId);
            } catch (Exception e) {
                LOG.error("could not send message {}, {}", msg, e);
            } finally {
                if (sender != null) {
                    senderPool.close(sender);
                }
            }
        });
    }

    /**
     * Pre-warms one sender connection in the background so it overlaps application setup. Idempotent and
     * thread-safe: the check-and-borrow is done under the instance lock so that when several threads race the
     * first call (e.g. two {@link Njams} instances sharing one sender), only the first borrows and starts a
     * sender. Re-arms after {@link #startWithTimeout(long, boolean)} returns the borrowed sender to the pool.
     */
    public synchronized void beginConnect() {
        if (startupSender != null) {
            return;
        }
        final AbstractSender s = senderPool.get();
        if (s != null) {
            startupSender = s;
            s.beginConnect();
        }
    }

    /**
     * Awaits the pre-warmed startup connection up to {@code timeoutMs}, applying the configured
     * {@link NjamsSettings#PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR}.
     *
     * @param timeoutMs maximum time to wait for the initial connect.
     * @return {@code true} if the SDK may proceed; {@code false} to fail startup (fail-fast policy).
     */
    public boolean startWithTimeout(long timeoutMs) {
        return startWithTimeout(timeoutMs, StartupFailBehavior.fromSettings(settings).reconnectOnStartupFailure());
    }

    /**
     * Awaits the pre-warmed startup connection up to {@code timeoutMs}.
     *
     * @param timeoutMs          maximum time to wait for the initial connect.
     * @param reconnectOnFailure {@code true} for the {@code reconnect} startup policy: on failure the group enters
     *                           the background reconnect loop and this returns {@code true}. {@code false} for
     *                           fail-fast: on failure the connect is cancelled and this returns {@code false}.
     * @return {@code true} if the SDK may proceed (connected, or reconnecting in the background); {@code false} to
     *         fail startup.
     */
    public boolean startWithTimeout(long timeoutMs, boolean reconnectOnFailure) {
        beginConnect();
        final AbstractSender s = startupSender;
        if (s == null) {
            return false;
        }
        if (reconnectOnFailure) {
            senderPool.allowReconnectBeforeConnected();
        }
        boolean connected = s.awaitStartup(timeoutMs);
        try {
            if (connected) {
                LOG.debug("Sender connected during startup within {} ms.", timeoutMs);
                return true;
            }
            if (reconnectOnFailure) {
                LOG.info("Initial connect did not complete within {} ms; retrying in the background "
                    + "(startup fail-behavior 'reconnect').", timeoutMs);
                // Interrupt a still-blocked initial connect so it fails promptly and, being DISCONNECTED, starts
                // the reconnect loop from its own thread (see AbstractSender.beginConnect). The reconnect() call
                // below covers the case where the initial connect already failed fast (sender DISCONNECTED); it is
                // idempotent when a reconnect is already running.
                s.cancelReconnect();
                s.reconnect(new NjamsSdkRuntimeException(
                    "Startup connect did not complete within " + timeoutMs + " ms; reconnecting in background"));
                return true;
            }
            LOG.debug("Sender did not connect within {} ms; cancelling startup connect (fail-fast).", timeoutMs);
            s.cancelReconnect();
            return false;
        } finally {
            senderPool.close(s);
            startupSender = null;
        }
    }

    /**
     * Wires this sender group and the given receiver together for cross-side connection verification: a failure
     * detected on either side prompts the other to proactively cycle its own connection ("assume-and-cycle" — no
     * active probing, just each side's existing reconnect machinery triggered from the other side too). This is
     * a one-way trigger in each direction, not shared state — the sender group and the receiver keep fully
     * independent {@code ConnectionCoordinator}s, reconnect loops, and shutdown signaling. No-op if
     * {@code receiver} is not an {@link AbstractReceiver} (custom {@link Receiver} implementations outside
     * {@code AbstractReceiver} have no reconnect mechanism to trigger). Safe to call repeatedly, including with
     * different receivers sharing this same sender group (e.g. {@code njams.sdk.communication.shared=true} on
     * HTTP, where each {@code Njams} instance has its own receiver but shares one sender pool) — every wired
     * receiver is notified, not just the most recently wired one.
     *
     * @param receiver the receiver to wire for cross-side verification with this sender group.
     * @since 6.0.0
     */
    public void wireReceiver(Receiver receiver) {
        if (receiver instanceof AbstractReceiver) {
            AbstractReceiver abstractReceiver = (AbstractReceiver) receiver;
            senderPool.addCrossSideTrigger(() -> abstractReceiver.onException(new NjamsSdkRuntimeException(
                "Cross-side connection check: the wired sender group detected a connection failure.")));
            abstractReceiver.addCrossSideTrigger(senderPool::triggerConnectionCheck);
        }
    }

    /**
     * Resolves whether {@link NjamsSettings#PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR} is set to {@code
     * reconnect} for the given settings, without exposing the internal {@code StartupFailBehavior} type. Used by
     * {@link com.im.njams.sdk.Njams#start()} to apply the identical decision to the receiver that {@link
     * #startWithTimeout(long)} already applies to the sender.
     *
     * @param settings the settings to read the setting from.
     * @return {@code true} if the startup fail-behavior is {@code reconnect}, {@code false} for the default
     *         {@code fail}.
     * @since 6.0.0
     */
    public static boolean reconnectOnStartupFailure(ClientSettings settings) {
        return StartupFailBehavior.fromSettings(settings).reconnectOnStartupFailure();
    }

    /**
     * This method closes the ThreadPoolExecutor safely. It awaits the
     * termination for 10 seconds, after that, an InterruptedException will be
     * thrown and the senders will be closed.
     */
    public void close() {
        final int waitTime = 10;
        final TimeUnit unit = TimeUnit.SECONDS;
        boolean terminated = false;
        senderPool.beginShutdown(); // set shutdown flag + cancel reconnects BEFORE draining
        try {
            LOG.info("Shutdown the sender's threadpool executor.");
            executor.shutdown();
            terminated = executor.awaitTermination(waitTime, unit);
            if (terminated) {
                LOG.debug("Shutdown of the sender's threadpool executor finished.");
            }
        } catch (InterruptedException ex) {
            LOG.error("The shutdown of the sender's threadpool has been interrupted. {}", ex);
            terminated = false;
        } finally {
            senderPool.declareShutdown();
            if (!terminated) {
                LOG.warn(
                    "The termination time of the sender's threadpool has been exceeded ({} {}). Forcing shutdown now.",
                    waitTime, unit);
                //This will call the interrupt() function of the threads
                executor.shutdownNow();
            }
            senderPool.shutdown();
            LOG.debug("Expire all sender pools finished.");
        }
    }

    /**
     * This method returns the name that was set in the settings with the key
     * njams.sdk.communication (or its alternative njams.sdk.communication.type).
     *
     * @return the value to key njams.sdk.communication in the
     * settings
     */
    public String getName() {
        return name;
    }

    /**
     * This method return the ThreadPoolExecutor
     *
     * @return the ThreadPoolExecutor
     */
    ThreadPoolExecutor getExecutor() {
        return executor;
    }

    public void addSenderExceptionListener(SenderExceptionListener listener) {
        if (senderPool == null) {
            throw new IllegalStateException("Sender not initialized.");
        }
        senderPool.addSenderExceptionListener(listener);
    }
}
