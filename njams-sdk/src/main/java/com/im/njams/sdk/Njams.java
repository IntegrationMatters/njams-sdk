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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk;

import com.faizsiegeln.njams.messageformat.v4.common.TreeElementType;
import com.im.njams.sdk.client.CleanTracepointsTask;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.*;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ConfigurationInstructionListener;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.logmessage.LogMessageFlushTask;
import com.im.njams.sdk.serializer.Serializer;
import com.im.njams.sdk.settings.ClientSettings;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This is an instance of nJAMS. It cares about lifecycle and initializations
 * and holds references to the process models and global variables.
 *
 * @author bwand
 */
public class Njams {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Njams.class);

    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 30_000L;

    /**
     * Defines the standard set of optional features that an nJAMS client may support.
     */
    public enum Feature {
        /**
         * Value indicating that this instance supports replay functionality.
         */
        REPLAY("replay"),
        /**
         * Value indicating that this instance supports the header injection feature.
         */
        INJECTION("injection"),
        /**
         * Value indicating that this instance implements expression test functionality.
         */
        EXPRESSION_TEST("expressionTest"),
        /**
         * Value indicating that this instance implements replying to a "ping" request sent from nJAMS server.
         */
        PING("ping"),
        /**
         * Value indicating that this instance supports the container mode feature with unique client ids.
         */
        CONTAINER_MODE("containerMode"),
        /**
         * Whether the client supports processing fragmented commands.
         */
        COMMANDS_SPLIT("commandSplit");

        /**
         * Inherent features implemented in this SDK that are always active.
         */
        static final Collection<Feature> INHERENT_FEATURES = Collections.unmodifiableCollection(
            Arrays.asList(Feature.EXPRESSION_TEST, Feature.PING, Feature.COMMANDS_SPLIT));

        private final String key;

        private Feature(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return key;
        }

        /**
         * Raw string value to be used when sending information to nJAMS.
         *
         * @return Raw string value.
         */
        public String key() {
            return key;
        }

        /**
         * Tries to find the instance according to the given name.
         *
         * @param name The name of the instance that shall be returned.
         * @return The instance for the given name, or <code>null</code> if no matching instance was found.
         */
        public static Feature byName(String name) {
            for (Feature f : values()) {
                if (f.name().equalsIgnoreCase(name) || f.key.equalsIgnoreCase(name)) {
                    return f;
                }
            }
            return null;
        }
    }

    /**
     * Key for clientVersion
     */
    public static final String CLIENT_VERSION_KEY = "clientVersion";
    /**
     * Key for sdkVersion
     */
    public static final String SDK_VERSION_KEY = "sdk.version";
    /**
     * Key for build-year
     */
    public static final String BUILD_YEAR = "sdk.buildYear";

    // Synchronizes access to the project message resources:
    // process-models, images, global-variables, tree-elements
    final Object projectMessageLock = new Object();

    private final NjamsMetadata metadata;

    // same instance as metadata.getClientPath(); kept for equals/hashCode, which must not depend on the facet
    private final Path clientPath;

    private final NjamsModel model;

    // The settings of the client
    private final ClientSettings settings;

    private final NjamsJobs jobs;

    private final NjamsCommands commands;
    /** Registered with {@link #commands} while started; routes received instructions to the commands facet. */
    private final InstructionListener commandDispatcher;

    private final NjamsSerializers serializers = new NjamsSerializers();

    // must be declared before all facets that receive it in their field initializer
    private final LifecycleState lifecycle = new LifecycleState();

    private final NjamsFeatures features = new NjamsFeatures(lifecycle);

    private NjamsSender sender;
    private Receiver receiver;

    /** Receiver pre-created at construction time, transferred to {@link #receiver} inside {@link #startReceiver(NjamsSender)}. */
    private Receiver earlyReceiver;

    private NjamsConfiguration configuration;

    private final NjamsReplay replay;

    private final NjamsArgos argos;

    /**
     * Create a nJAMS client.
     *
     * @param path     the path in the tree
     * @param version  the version of the nNJAMS client
     * @param category the category of the nJAMS client, should describe the
     *                 technology
     * @param settings needed settings for client eg. for communication
     */
    public Njams(Path path, String version, String category, ClientSettings settings) {
        this.settings = settings;
        jobs = new NjamsJobs(lifecycle);
        replay = new NjamsReplay(lifecycle, features, jobs);
        metadata = new NjamsMetadata(path, version, category, lifecycle);
        clientPath = path;
        features.setContainerMode(settings.getBool(NjamsSettings.PROPERTY_CONTAINER_MODE, true));
        argos = new NjamsArgos(settings);
        configuration = new NjamsConfiguration(settings, this);
        model = new NjamsModel(this, lifecycle, metadata, features, configuration, projectMessageLock);
        commands = new NjamsCommands(model, replay, metadata, features);
        commandDispatcher = commands::dispatch;
        model.createTreeElements(path, TreeElementType.CLIENT);
        metadata.printStartupBanner(settings);
        beginConnect();
    }

    /**
     * Provides access to the identifying metadata of this client: path, category, versions,
     * machine, session id, and the global variables announced to the nJAMS server at start.
     *
     * @return the metadata facet of this client, never <code>null</code>
     */
    public NjamsMetadata metadata() {
        return metadata;
    }

    /**
     * Provides access to the optional-feature list and the container-mode flag of this client.
     * Features are announced to the nJAMS server at start.
     *
     * @return the features facet of this client, never <code>null</code>
     */
    public NjamsFeatures features() {
        return features;
    }

    /**
     * Provides access to the process models, taxonomy tree, images and process diagram tooling
     * of this client, including sending project messages.
     *
     * @return the model facet of this client, never <code>null</code>
     */
    public NjamsModel model() {
        return model;
    }

    /**
     * Provides access to the jobs of this client: the registry of currently running
     * {@link Job} instances.
     *
     * @return the jobs facet of this client, never <code>null</code>
     */
    public NjamsJobs jobs() {
        return jobs;
    }

    /**
     * Provides access to the {@link Serializer} registry of this client, used to serialize
     * activity data to strings.
     *
     * @return the serializers facet of this client, never <code>null</code>
     */
    public NjamsSerializers serializers() {
        return serializers;
    }

    /**
     * Provides access to the replay handling of this client: registering a
     * {@link ReplayHandler} enables the replay feature.
     *
     * @return the replay facet of this client, never <code>null</code>
     */
    public NjamsReplay replay() {
        return replay;
    }

    /**
     * Provides access to the {@link InstructionListener} registry of this client, which is
     * called for commands received from the nJAMS server.
     *
     * @return the commands facet of this client, never <code>null</code>
     */
    public NjamsCommands commands() {
        return commands;
    }

    /**
     * Provides access to the Argos metric collector registration of this client.
     *
     * @return the Argos facet of this client, never <code>null</code>
     */
    public NjamsArgos argos() {
        return argos;
    }

    /**
     * Provides access to the server-driven runtime configuration of this client: log mode,
     * process exclusions, and the underlying {@link Configuration}.
     *
     * @return the configuration facet of this client, never <code>null</code>
     */
    public NjamsConfiguration configuration() {
        return configuration;
    }

    /**
     * @return the current nJAMS settings
     */
    public ClientSettings getSettings() {
        return settings;
    }

    /**
     * Returns the sender of this instance, creating it on first use as configured in the settings.
     *
     * @return the Sender
     */
    NjamsSender sender() {
        if (sender == null) {
            if (settings.getBool(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, false)) {
                LOG.debug("Using shared sender pool for {}", metadata.getClientPath());
                sender = NjamsSender.takeSharedSender(settings);
            } else {
                LOG.debug("Creating individual sender pool for {}", metadata.getClientPath());
                sender = new NjamsSender(settings);
            }
        }
        return sender;
    }

    /**
     * Pre-creates the sender and receiver and starts both their connection attempts in the background, so the
     * connections overlap with the remaining application setup. Called automatically at construction time.
     * Idempotent and best-effort: any failure is swallowed and {@link #startReceiver(NjamsSender)} will retry
     * creating the receiver.
     */
    private void beginConnect() {
        if (earlyReceiver != null || lifecycle.isStarted()) {
            return;
        }
        NjamsSender earlySender = null;
        try {
            earlySender = sender();
        } catch (Exception e) {
            LOG.warn("beginConnect() failed to pre-warm sender; start() will retry.", e);
        }
        try {
            earlyReceiver = new CommunicationFactory(settings).getReceiver(this);
            if (earlyReceiver instanceof AbstractReceiver) {
                ((AbstractReceiver) earlyReceiver).beginConnect();
            }
        } catch (Exception e) {
            LOG.warn("beginConnect() failed to pre-initialize receiver; start() will retry.", e);
            earlyReceiver = null;
        }
        if (earlySender != null) {
            earlySender.beginConnect();
        }
    }

    /**
     * Best-effort receiver setup: resolves the receiver (from the pre-warmed {@code earlyReceiver} or newly
     * created), starts its connection, and, if the receiver reports send exceptions or implements
     * {@link SenderRecoveryListener}, registers it as a listener on the given sender group; {@link #stop()} (or
     * {@link #stopReceiverAfterStartupFailure(Receiver)} on a startup failure) removes it again. The
     * receiver's connection outcome never affects {@code start()} — a
     * construction or connection failure is logged and the SDK proceeds without a working receiver; only the
     * sender is critical to startup (see {@link #start()}). Connecting the receiver here never blocks: see
     * {@link #connectReceiver(Receiver)}.
     * <p>
     * Only exceptions raised while constructing the receiver itself are caught and logged here. This method does
     * not obtain the sender (that is {@link #start()}'s responsibility, before calling this) so that a
     * sender-construction failure is never misattributed to the receiver, and is left to propagate so
     * {@link #start()} can report and fail startup on it accurately.
     *
     * @param activeSender the sender group to register the receiver's exception listener on, or {@code null} if
     *         none is configured.
     */
    private void startReceiver(NjamsSender activeSender) {
        try {
            if (earlyReceiver != null) {
                receiver = earlyReceiver;
                earlyReceiver = null;
            } else {
                receiver = new CommunicationFactory(settings).getReceiver(this);
            }
            if (receiver instanceof SenderExceptionListener && activeSender != null) {
                activeSender.addSenderExceptionListener((SenderExceptionListener) receiver);
            }
            if (receiver instanceof SenderRecoveryListener && activeSender != null) {
                // The receiver object itself is the listener, never a lambda: the pool's listener set has identity
                // semantics, so a shared receiver registered by several instances collapses to one entry (one
                // cycle per outage), while dedicated per-instance receivers on a shared group each get their own.
                activeSender.addSenderRecoveryListener((SenderRecoveryListener) receiver);
            }
            connectReceiver(receiver);
        } catch (Exception e) {
            LOG.warn("Failed to initialize the receiver; the SDK will operate without receiving server "
                + "commands until this is resolved.", e);
            receiver = null;
        }
    }

    /**
     * Starts the given receiver's connection without blocking or otherwise affecting {@link #start()}'s outcome.
     * An {@link AbstractReceiver} is simply told to {@link AbstractReceiver#beginConnect() begin connecting} in
     * the background; that call is idempotent, so it correctly no-ops when {@code receiverToConnect} is the
     * constructor's already-connecting {@code earlyReceiver}, or a shared receiver a currently active sibling
     * {@link Njams} instance already connected, and correctly starts a fresh connection for a newly created one —
     * in particular when the constructor's own pre-warm could not create a receiver and left
     * {@code earlyReceiver} unset, so that {@link #startReceiver(NjamsSender)} had to build a replacement.
     * <p>
     * A plain {@link Receiver} from the SPI that does not extend {@link AbstractReceiver} has no such
     * background-connect hook, so {@link Receiver#start()} is instead run on a dedicated daemon thread; any
     * exception it throws is logged here and never propagated, so it can never affect {@link #start()} either.
     *
     * @param receiverToConnect the receiver to connect; {@code null} is a no-op.
     */
    private void connectReceiver(Receiver receiverToConnect) {
        if (receiverToConnect == null) {
            return;
        }
        if (receiverToConnect instanceof AbstractReceiver) {
            ((AbstractReceiver) receiverToConnect).beginConnect();
        } else {
            Thread starter = new Thread(() -> {
                try {
                    receiverToConnect.start();
                } catch (Exception e) {
                    LOG.warn("Receiver {} failed to start.", receiverToConnect.getName(), e);
                }
            });
            starter.setDaemon(true);
            starter.setName("Receiver-Start-" + receiverToConnect.getName());
            starter.start();
        }
    }

    /**
     * Start a client; it will initiate the connections and start processing.
     * <p>
     * If it returns <code>false</code>, the instance stays inactive and all further calls that require a started
     * instance throw an {@link NjamsSdkRuntimeException}. The SDK has already released everything this call
     * acquired, so no cleanup is required, and calling this method again performs a new startup attempt.
     * <p>
     * Clients that want to differentiate whether a failed startup should only withdraw the client from the
     * runtime or terminate the runtime must use {@link #startup()} instead.
     *
     * @return true if successful
     */
    public boolean start() {
        return startup() == StartupResult.SUCCESS;
    }

    /**
     * Start a client; it will initiate the connections and start processing. Does the same as {@link #start()} but
     * reports the outcome as a {@link StartupResult}, which is only needed by clients that want to differentiate
     * {@link StartupResult#FAIL} from {@link StartupResult#EXIT}:
     * <ul>
     * <li>{@link StartupResult#SUCCESS}: the client continues its normal startup.</li>
     * <li>{@link StartupResult#FAIL}: the runtime continues without the client, i.e., the client withdraws itself
     * from the runtime as far as possible.</li>
     * <li>{@link StartupResult#EXIT}: the client terminates the runtime. This is returned instead of
     * {@code FAIL} if the transport could not be connected and
     * {@link NjamsSettings#PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR} is {@code exit}.</li>
     * </ul>
     * The SDK treats {@code FAIL} and {@code EXIT} identically: the instance stays inactive. Reacting differently
     * has to be implemented by the client; see the respective client's documentation for how it reacts.
     *
     * @return the outcome of the startup.
     * @since 6.1.0
     */
    public StartupResult startup() {
        if (!isStarted()) {
            if (settings == null) {
                throw new NjamsSdkRuntimeException("Settings not set");
            }
            configuration.load();
            configuration.initializeDataMasking();
            final ConfigurationInstructionListener configurationListener = new ConfigurationInstructionListener(this);
            commands.add(commandDispatcher);
            commands.add(configurationListener);
            final NjamsSender activeSender;
            try {
                activeSender = sender();
            } catch (Exception e) {
                LOG.error("SDK startup failed: could not obtain a sender. The SDK instance is inactive.", e);
                releaseStartupRegistrations(configurationListener);
                releasePrewarmedSender();
                return StartupResult.FAIL;
            }
            startReceiver(activeSender);
            if (activeSender != null) {
                long timeoutMs = settings.getLong(
                    NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT_MS);
                boolean reconnectOnFailure = NjamsSender.reconnectOnStartupFailure(settings);
                if (!activeSender.startWithTimeout(timeoutMs, reconnectOnFailure)) {
                    boolean exit = NjamsSender.exitOnStartupFailure(settings);
                    LOG.error("SDK startup failed: sender could not connect and startup fail-behavior is '{}'. "
                        + "The SDK instance is inactive.", exit ? "exit" : "fail");
                    stopReceiverAfterStartupFailure(receiver);
                    receiver = null;
                    releaseStartupRegistrations(configurationListener);
                    releasePrewarmedSender();
                    return exit ? StartupResult.EXIT : StartupResult.FAIL;
                }
            }
            LogMessageFlushTask.start(this, activeSender);
            CleanTracepointsTask.start(this, activeSender);
            lifecycle.setStarted(true);
            try {
                model.send();
            } catch (Exception | Error e) {
                // A failure here (e.g. a classloading Error from image embedding) is unrecoverable within
                // this JVM run — no retry can help, so fail startup cleanly instead of leaving the
                // sender/background tasks running.
                LOG.error("The client SDK failed to initialize: could not send the initial project message. "
                    + "The SDK instance is inactive.", e);
                stop();
                return StartupResult.FAIL;
            }
            LOG.info("SDK instance {} started (client-session={})", metadata.getClientPath(), metadata.getClientSessionId());
        }
        return isStarted() ? StartupResult.SUCCESS : StartupResult.FAIL;
    }

    /**
     * Removes what {@link #startup()} registered before the sender was connected, so a failed startup leaves
     * nothing behind that a client would need to clean up, or that a retried startup would duplicate.
     *
     * @param configurationListener the configuration listener registered by the failing startup.
     */
    private void releaseStartupRegistrations(ConfigurationInstructionListener configurationListener) {
        commands.remove(commandDispatcher);
        commands.remove(configurationListener);
        argos.stop();
    }

    /**
     * Stops the given receiver after a sender startup failure, unregistering this instance from a shared receiver
     * via {@link ShareableReceiver#removeNjams(Njams)} where applicable instead of stopping it outright for every
     * instance still using it — mirroring {@link #stop()}'s own gating exactly. Only once that determines this
     * really was the last user (trivially true for a non-{@code ShareableReceiver} {@link AbstractReceiver}) does
     * this signal shutdown and cancel any reconnect the receiver may already be running on its own — the receiver
     * always retries its own connection unconditionally in the background (see {@link AbstractReceiver#beginConnect()}),
     * independently of this startup's sender-side fail-fast decision, so it may already be reconnecting by the
     * time the sender's own failure is discovered. Signalling/cancelling unconditionally instead would wrongly
     * stop a reconnect a still-registered sharing instance depends on. That same "really the last user" outcome
     * also atomically evicts a {@link ShareableReceiver} from {@link CommunicationFactory}'s cache (see
     * {@link CommunicationFactory#removeNjamsFromSharedReceiver(ShareableReceiver, Njams)}), so a later
     * {@link Njams} sharing the same receiver type builds a fresh instance instead of being handed this now-dead
     * one.
     * <p>
     * For a non-{@code ShareableReceiver}, the shutdown flag is set <em>before</em> {@link Receiver#stop()} is
     * called: {@link AbstractReceiver#beginConnect()}'s background startup connect checks the flag right after
     * connecting and only releases itself if it is already set, so a connect that completes concurrently with
     * this shutdown must observe it. A {@code ShareableReceiver} cannot be signalled this early — only once
     * {@code removeNjamsFromSharedReceiver} confirms this was the last user is it safe to shut down, since
     * signalling earlier would wrongly affect a receiver other {@code Njams} instances still use.
     * <p>
     * Also deregisters the receiver from the sender group's exception and recovery listeners, mirroring the
     * registration done in {@link #startReceiver(NjamsSender)}, so a stopped receiver is not left referenced by
     * (and reacting to) a sender group it no longer belongs to.
     *
     * @param failedReceiver the receiver to stop; {@code null} is a no-op.
     */
    private void stopReceiverAfterStartupFailure(Receiver failedReceiver) {
        if (failedReceiver == null) {
            return;
        }
        try {
            boolean reallyStopped;
            if (failedReceiver instanceof ShareableReceiver) {
                reallyStopped = CommunicationFactory.removeNjamsFromSharedReceiver(
                    (ShareableReceiver<?>) failedReceiver, this);
            } else {
                if (failedReceiver instanceof AbstractReceiver) {
                    // Signal shutdown before tearing down: a startup connect racing in the background
                    // (AbstractReceiver#beginConnect()) only releases itself if this flag is already set once it
                    // finishes connecting. Setting it again below is harmless — ConnectionCoordinator.setShouldShutdown
                    // backs an idempotent AtomicBoolean.
                    ((AbstractReceiver) failedReceiver).setShouldShutdown(true);
                }
                failedReceiver.stop();
                reallyStopped = true;
            }
            if (reallyStopped && failedReceiver instanceof AbstractReceiver) {
                ((AbstractReceiver) failedReceiver).setShouldShutdown(true);
                ((AbstractReceiver) failedReceiver).cancelReconnect();
            }
            if (reallyStopped && sender != null) {
                // Same reasoning as in stop(): a shared group must not keep a dead receiver registered.
                deregisterReceiverListeners(failedReceiver);
            }
        } catch (Exception ex) {
            LOG.debug("Unable to stop receiver after startup failure", ex);
        }
    }

    /**
     * Removes the given receiver from every sender-group listener set {@link #startReceiver(NjamsSender)} may have
     * registered it in. Must only be called with a non-{@code null} {@link #sender}.
     */
    private void deregisterReceiverListeners(Receiver stoppedReceiver) {
        if (stoppedReceiver instanceof SenderExceptionListener) {
            sender.removeSenderExceptionListener((SenderExceptionListener) stoppedReceiver);
        }
        if (stoppedReceiver instanceof SenderRecoveryListener) {
            sender.removeSenderRecoveryListener((SenderRecoveryListener) stoppedReceiver);
        }
    }

    /**
     * Releases the sender that was pre-warmed at construction time (via {@link #beginConnect()}) when
     * {@link #start()} fails. This balances the constructor's {@code sender()} acquisition with a matching
     * {@code close()} — symmetric with {@link #stop()} — so a shared sender's usage count is not pinned when
     * startup does not complete. On the success path {@link #stop()} performs the single balancing close instead.
     */
    private void releasePrewarmedSender() {
        if (sender != null) {
            sender.close();
            sender = null;
        }
    }

    /**
     * Stop a client; it stop processing and release the connections. It can't
     * be stopped before it started. (NjamsSdkRuntimeException)
     * <p>
     * The sender and receiver are signalled independently: closing the sender marks its own {@code
     * ConnectionCoordinator} as shutting down; separately, the receiver is signalled and stopped. For a
     * non-{@code ShareableReceiver}, shutdown is signalled <em>before</em> {@link Receiver#stop()} is called, so
     * a startup connect racing in the background ({@link AbstractReceiver#beginConnect()}) observes the flag and
     * releases itself instead of leaking; for a {@code ShareableReceiver}, shutdown can only be signalled once
     * the receiver is really stopped — i.e. once the last {@code Njams} instance using it has stopped it (see
     * {@link CommunicationFactory#removeNjamsFromSharedReceiver(ShareableReceiver, Njams)}, which performs the
     * "last user" check and the shared-receiver cache eviction atomically) — since signalling any earlier would
     * wrongly affect a receiver other instances still use. Either way, once shutdown is confirmed the
     * coordinator's in-progress reconnect is also cancelled.
     *
     * @return true is stopping was successful.
     */
    public boolean stop() {
        lifecycle.requireStarted();
        LogMessageFlushTask.stop(this);
        CleanTracepointsTask.stop(this);

        argos.stop();

        if (sender != null) {
            sender.close();
        }
        if (receiver != null) {
            boolean reallyStopped;
            if (receiver instanceof ShareableReceiver) {
                reallyStopped = CommunicationFactory.removeNjamsFromSharedReceiver(
                    (ShareableReceiver<?>) receiver, this);
            } else {
                if (receiver instanceof AbstractReceiver) {
                    // Signal shutdown before tearing down: a startup connect racing in the background
                    // (AbstractReceiver#beginConnect()) only releases itself if this flag is already set once it
                    // finishes connecting. Setting it again below is harmless — ConnectionCoordinator.setShouldShutdown
                    // backs an idempotent AtomicBoolean.
                    ((AbstractReceiver) receiver).setShouldShutdown(true);
                }
                receiver.stop();
                reallyStopped = true;
            }
            if (reallyStopped && receiver instanceof AbstractReceiver) {
                ((AbstractReceiver) receiver).setShouldShutdown(true);
                ((AbstractReceiver) receiver).cancelReconnect();
            }
            if (reallyStopped && sender != null) {
                // A shared sender group outlives the instances using it: leaving a stopped receiver registered
                // would keep it referenced for the group's lifetime and signal it on every later outage. Gated on
                // reallyStopped so a shared receiver a sibling still uses stays registered. Removing after
                // sender.close() is safe — close() shuts the pool down but keeps it reachable.
                deregisterReceiverListeners(receiver);
            }
        }
        // the closed sender must not be reused: a later start() acquires a new one
        sender = null;
        commands.clear();
        lifecycle.setStarted(false);
        return !isStarted();
    }

    @Override
    public int hashCode() {
        int hash = 5;
        return 83 * hash + Objects.hashCode(clientPath);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Njams other = (Njams) obj;
        return Objects.equals(clientPath, other.clientPath);
    }

    /**
     * @return if this client instance is started
     */
    public boolean isStarted() {
        return lifecycle.isStarted();
    }
}
