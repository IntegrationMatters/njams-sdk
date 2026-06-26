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
package com.im.njams.sdk;

import javax.jms.ConnectionFactory;
import javax.naming.Context;

import com.im.njams.sdk.communication.fragments.SplitSupport;
import com.im.njams.sdk.communication.http.HttpSender;
import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.communication.jms.factory.JndiJmsFactory;
import com.im.njams.sdk.communication.kafka.KafkaUtil;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * This class is a list and documentation for all settings, which can be used in SDK.
 */
public class NjamsSettings {

    /**
     * Property key for communication properties which specifies which
     * communication implementation will be used.
     * <p>
     * Possible values:
     * <ul>
     * <li>JMS</li>
     * <li>HTTP</li>
     * <li>KAFKA</li>
     * <li>YOUR-OWN-COMMUNICATION</li>
     * </ul>
     */
    public static final String PROPERTY_COMMUNICATION = "njams.sdk.communication";

    /**
     * Limits the message size for messages being sent to the server. The given value is the maximum size in bytes.
     * Only the message body (JSON) is truncated by this value. Message headers are not considered. If the transport's
     * limitation includes the headers, the configured value has to be accordingly smaller. A value of 0 or less
     * disables splitting large messages. This is the default. The minimum allowed is
     * {@value SplitSupport#MIN_SIZE_LIMIT} bytes.<br>
     * The configured value does not take into account when message compressing is used. If the transport compresses
     * messages, the configured value should expect zero compression since the actual compression ratio cannot be
     * estimated.<br>
     * <b>KAFKA:</b> When using Kafka transport, this setting is limited by the Kafka client producer's max message
     * size setting.
     * I.e., the smaller setting is used. Additionally a {@value KafkaUtil#HEADERS_OVERHEAD} bytes overhead has to be
     * considered which increases the allowed minimum size setting by this value when using Kafka.
     *
     * @since 5.0.3
     */
    public static final String PROPERTY_MAX_MESSAGE_SIZE = "njams.sdk.communication.maxMessageSize";

    /**
     * When this settings is false (default) nJAMS creates error events only for error situations that are not
     * handled by the execution engine. If set to true, nJAMS will also create error events for errors that are handled.
     */
    public static final String PROPERTY_LOG_ALL_ERRORS = "njams.sdk.logAllErrors";

    /**
     * When setting this option to a positive number, the nJAMS client will stop sending detailed activity- and
     * transition information to the server once that number of activities has been reached. The client will
     * however continue sending job status- and event information. In nJAMS server you will not see transitions
     * between activities for such job instances.
     * <p>
     * This option goes along with the according setting in nJAMS server. But when using different settings here
     * and in the server, the smaller value (greater than 0) will take effect.
     */
    public static final String PROPERTY_TRUNCATE_LIMIT = "njams.sdk.truncateActivitiesLimit";

    /**
     * When this setting is enabled (true), the nJAMS client will not send detailed activity- and transition information
     * for job instances that completed successfully. The client will however send job status- and event information.
     * Job instances completing with any other status than success are not affected by this setting.
     * In nJAMS server you will not see transitions between activities for such job instances.
     * This setting has only limited effect on job instances that send more than a single message because it only
     * affects the last message being sent.
     * This option goes along with the according setting in nJAMS server (compress successful transactions).
     * It will be effective if it is either set here in the client, or in the server.
     */
    public static final String PROPERTY_TRUNCATE_ON_SUCCESS = "njams.sdk.truncateOnSuccess";

    /**
     * If this setting is true (which is the default), the SDK will generate a unique client id, which is used to identify
     * the correct client in a multi- node/replica/pod container environment.
     * <p>
     * An additional feature flag (named containerMode) indicates that the feature is activated and nJAMS server then
     * uses the according server feature for communicating with the specific clients in the container environment.
     * <p>
     * Some commands will only be sent to just one of the multi-container nodes (eg. replay and all get commands). The rest
     * will be sent to all nodes because for example a change to log-mode has to be processed by all nodes.
     */
    public static final String PROPERTY_CONTAINER_MODE = "njams.sdk.communication.containerMode";

    /**
     * Maximum time in milliseconds the SDK waits for the initial communication connection to be
     * established during {@link com.im.njams.sdk.Njams#start()}. If the connection is not ready within
     * this time, {@code start()} returns {@code false} and the SDK instance remains inactive — no
     * reconnect thread is started.
     * <p>
     * The connection attempt is started automatically in the background when the
     * {@link com.im.njams.sdk.Njams} instance is constructed, so that it overlaps with application
     * setup. {@code start()} then awaits the already-running attempt and applies this timeout only for
     * the remaining wait.
     * <p>
     * The default is 30000 ms.
     *
     * @since 6.0.0
     */
    public static final String PROPERTY_COMMUNICATION_CONNECT_TIMEOUT =
            "njams.sdk.communication.connect.timeout";

    /**
     * This property is a flush criteria with a default of 5mb.
     * <p>
     * If the flush size of the @{@link com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage}
     * exceeds this threshold, the message will be flushed
     */
    public static final String PROPERTY_FLUSH_SIZE = "njams.sdk.flushsize";

    /**
     * This property is a flush criteria with a default of 30s.
     * <p>
     * If no @{@link com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage}
     * has been sent in the last 30 seconds, it will be flushed
     */
    public static final String PROPERTY_FLUSH_INTERVAL = "njams.sdk.flush_interval";

    /**
     * This property's default is 1 sender thread as core thread
     * (that means it can't be closed even if its idle time has been exceeded)
     * that can send project and log messages to the server.
     */
    public static final String PROPERTY_MIN_SENDER_THREADS = "njams.sdk.minsenderthreads";

    /**
     * This property's default is 8 sender threads as maximum threads that can be used.
     * This means if there are more messages to handle than there are sender threads at the moment
     * and the threshold hasn't exceeded, a new thread will be started. If the thread isn't in use for
     * (look below njams.client.sdk.senderthreadidletime), the thread will be removed.
     */
    public static final String PROPERTY_MAX_SENDER_THREADS = "njams.sdk.maxsenderthreads";

    /**
     * This property's default is 8 messages that can be hold in the message Queue before the
     * messages will be discarded or client will stop processing until the queue has space again.
     */
    public static final String PROPERTY_MAX_QUEUE_LENGTH = "njams.sdk.maxqueuelength";

    /**
     * This property's default is 10000 (ms) that means that idle sender threads that haven't send any
     * message in the last 10 seconds and are not core threads will be removed.
     */
    public static final String PROPERTY_SENDER_THREAD_IDLE_TIME = "njams.sdk.senderthreadidletime";

    /**
     * This property decides what to do with a logmessage that couldn't be delivered (because of connection loss, full queue, etc.)
     * Possible values are:
     * <ul>
     * <li>none
     * <li>onconnectionloss
     * <li>discard (Default since 6.0.0; was none in earlier versions)
     * </ul>
     */
    public static final String PROPERTY_DISCARD_POLICY = "njams.sdk.discardpolicy";

    /**
     * If set to <code>true</code> communications (senders and receivers) will be shared across multiple {@link Njams}
     * instances if supported by the configured implementations. By default (or if set to <code>false</code>) each
     * {@link Njams} instance uses a dedicated instance of sender and receiver pools.
     */
    public static final String PROPERTY_SHARED_COMMUNICATIONS = "njams.sdk.communication.shared";

    /**
     * If set to <code>true</code> secure XML processing feature will NOT be initialized:
     * <p>
     * factory.setAttribute(XMLConstants.FEATURE_SECURE_PROCESSING, false);
     * <p>
     * This can be helpful for an environment containing an old XML lib, which does not support this.
     */
    public static final String PROPERTY_DISABLE_SECURE_PROCESSING = "njams.sdk.disable.secure.processing";

    /**
     * Since 5.0.0.
     * This setting is used only once when creating a fresh configuration for an {@link Njams} instance.
     * It is used in that case as default setting for the client- (engine-) wide recording flag.
     * Default is <code>true</code>.
     */
    public static final String PROPERTY_BOOSTRAP_RECORDING = "njams.sdk.bootstrap.recording";

    /**
     * Since 5.0.0
     * When this setting is set to either <code>truncate</code> or <code>discard</code> large payloads like
     * <code>input, output, event-payload, event-stack, or attribute-values</code> are either truncated
     * or discarded when they reach the limit configured with property{@value #PROPERTY_PAYLOAD_LIMIT_SIZE}.<br>
     * By default payloads are not limited.
     */
    public static final String PROPERTY_PAYLOAD_LIMIT_MODE = "njams.sdk.payload.limit.mode";

    /**
     * Since 5.0.0
     * When property {@value #PROPERTY_PAYLOAD_LIMIT_MODE} is set to limiting payloads, this setting specifies
     * the maximum size (in characters) before the payload is limited.<br>
     * This value is required when using payload limitations.
     */
    public static final String PROPERTY_PAYLOAD_LIMIT_SIZE = "njams.sdk.payload.limit.size";

    /**
     * Since 6.0.0
     * When set to <code>true</code>, the payload limit configured via {@value #PROPERTY_PAYLOAD_LIMIT_MODE} and
     * {@value #PROPERTY_PAYLOAD_LIMIT_SIZE} is also applied to a job's start data. Start data that is truncated or
     * discarded this way makes the job non-replayable (the <code>$njams_recorded</code> flag is cleared).<br>
     * By default (<code>false</code>) start data is never limited, preserving replay.<br>
     * Note: to actually bound memory while serializing large start data, a payload-size-honouring serializer (e.g.
     * the JSON serializer) must be registered for the start-data type; the default <code>toString</code>-based
     * serializer builds the full representation first, so this option then only bounds the transmitted/stored size.
     */
    public static final String PROPERTY_APPLY_PAYLOAD_LIMIT_TO_START_DATA = "njams.sdk.payload.limit.applyToStartData";

    /**
     * Since 5.0.0
     * Defines the default log-mode that is used until the client's configuration contains a specific setting for that.
     */
    public static final String PROPERTY_LOG_MODE_DEFAULT = "njams.sdk.logMode.default";
    /**
     * Since 5.0.0
     * Defines the default log-level that is used until a process' configuration contains a specific setting for that.
     */
    public static final String PROPERTY_LOG_LEVEL_DEFAULT = "njams.sdk.logLevel.default";

    /**
     * Optional path to a directory where outbound messages are written as individual files for debug purposes.
     * When set, each message submitted to the sender is written to a per-run subdirectory named after the
     * start time of the current JVM run ({@code yyyyMMdd-HHmmss}). Filenames include a zero-padded sequence
     * counter, the message type, and a message identifier.
     * <p>
     * <b>For development and debug use only.</b> When this property is not set, the feature is completely inactive
     * with negligible overhead on the send path.
     *
     * @since 6.0.0
     */
    public static final String PROPERTY_DEBUG_MESSAGE_DIR = "njams.sdk.debug.messagedir";

    /**
     * Property key for the settings properties. Specifies which implementation will be loaded.
     * Possible values:
     *
     * <ul>
     * <li>file
     * <li>propertiesFile
     * <li>memory
     * <li>systemProperties
     * </ul>
     *
     * @deprecated The settings provider/factory layer is replaced by the {@link ClientSettings}
     *     factory methods such as {@link ClientSettings#from(java.util.Map)} and
     *     {@link ClientSettings#from(java.util.Properties)}; no provider needs to be selected.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public static final String PROPERTY_SETTINGS_PROVIDER = "njams.sdk.settings.provider";

    /**
     * Specifies the path to the settings file for "file" settings provider.
     *
     * @deprecated Part of the deprecated settings provider layer. Load the file yourself and pass
     *     the resulting properties to {@link ClientSettings#from(java.util.Properties)} instead.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public static final String PROPERTY_FILE_SETTINGS_FILE = "njams.sdk.settings.file";

    /**
     * Property key for the propertiesFile provider, specifying the path to the properties file to be used.
     *
     * @deprecated Part of the deprecated settings provider layer. Load the file yourself and pass
     *     the resulting properties to {@link ClientSettings#from(java.util.Properties)} instead.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public static final String PROPERTY_PROPERTIES_FILE_SETTINGS_FILE = "njams.sdk.settings.properties.file";
    /**
     * Default property key for loading parent (default) configuration file for the propertiesFile provider.
     * See {@link #PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_KEY} for using an alternative key.
     *
     * @deprecated Part of the deprecated settings provider layer. Load the file yourself and pass
     *     the resulting properties to {@link ClientSettings#from(java.util.Properties)} instead.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public static final String PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_FILE = "njams.sdk.settings.properties.parent";
    /**
     * Allows to override the default parent file key for the propertiesFile provider.
     * ({@value #PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_FILE}).
     *
     * @deprecated Part of the deprecated settings provider layer. Load the file yourself and pass
     *     the resulting properties to {@link ClientSettings#from(java.util.Properties)} instead.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    public static final String PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_KEY = "njams.sdk.settings.properties.parentKey";

    /**
     * Name of the property flag to enable or disable collecting Argos Metrics.
     */
    public static final String PROPERTY_ARGOS_SUBAGENT_ENABLED = "njams.sdk.subagent.enabled";

    /**
     * Name of the property port where the nJAMS Agent runs and ArgosSender will send metrics
     */
    public static final String PROPERTY_ARGOS_SUBAGENT_PORT = "njams.sdk.subagent.port";

    /**
     * Name of the property host where the nJAMS Agent runs and ArgosSender will send metrics
     */
    public static final String PROPERTY_ARGOS_SUBAGENT_HOST = "njams.sdk.subagent.host";

    /**
     * The URL, where the nJAMS Server is running and reachable
     * (eg. <code>http://localhost:8080/njams/</code>)
     */
    public static final String PROPERTY_HTTP_BASE_URL = "njams.sdk.communication.http.base.url";

    /**
     * This is the http path suffix of the nJAMS dataprovider you want to send your events to.
     */
    public static final String PROPERTY_HTTP_DATAPROVIDER_SUFFIX = "njams.sdk.communication.http.dataprovider.suffix";

    /**
     * This is the full path to your certificate file to use for SSL
     */
    public static final String PROPERTY_HTTP_SSL_CERTIFICATE_FILE = "njams.sdk.communication.http.ssl.certificate.file";
    /**
     * nJAMS user if nJAMS server is required to use authentication for the ingest-service
     */
    public static final String PROPERTY_HTTP_USER = "njams.sdk.communication.http.user";
    /**
     * Password of the nJAMS user if nJAMS server is required to use authentication for the ingest-service
     */
    public static final String PROPERTY_HTTP_PASSWORD = "njams.sdk.communication.http.password";
    /**
     * Optional proxy host for http connection
     */
    public static final String PROPERTY_HTTP_PROXY_HOST = "njams.sdk.communication.http.proxy.host";
    /**
     * Http proxy port is using a proxy. Default is 80.
     */
    public static final String PROPERTY_HTTP_PROXY_PORT = "njams.sdk.communication.http.proxy.port";
    /**
     * User-name for proxy authentication.
     */
    public static final String PROPERTY_HTTP_PROXY_USER = "njams.sdk.communication.http.proxy.user";
    /**
     * Password for proxy authentication.
     */
    public static final String PROPERTY_HTTP_PROXY_PASSWORD = "njams.sdk.communication.http.proxy.password";

    /**
     * The path to a custom truststore file to be used for SSL connection.
     */
    public static final String PROPERTY_HTTP_TRUSTSTORE_PATH = "njams.sdk.communication.http.ssl.trustStore";
    /**
     * Custom truststore type. Default: <code>jks</code>
     */
    public static final String PROPERTY_HTTP_TRUSTSTORE_TYPE = "njams.sdk.communication.http.ssl.trustStoreType";
    /**
     * Optional truststore password.
     */
    public static final String PROPERTY_HTTP_TRUSTSTORE_PASSWORD =
        "njams.sdk.communication.http.ssl.trustStorePassword";
    /**
     * The path to a custom keystore file to be used for SSL connection.
     */
    public static final String PROPERTY_HTTP_KEYSTORE_PATH = "njams.sdk.communication.http.ssl.keyStore";
    /**
     * Custom keystore type. Default: <code>jks</code>
     */
    public static final String PROPERTY_HTTP_KEYSTORE_TYPE = "njams.sdk.communication.http.ssl.keyStoreType";
    /**
     * Optional keystore password.
     */
    public static final String PROPERTY_HTTP_KEYSTORE_PASSWORD = "njams.sdk.communication.http.ssl.keyStorePassword";
    /**
     * Allows disabling host-name verification when setting up a SSL connection.
     */
    public static final String PROPERTY_HTTP_DISABLE_HOSTNAME_VERIFICATION =
        "njams.sdk.communication.http.ssl.unsafe.disableHostnameVerification";
    /**
     * Trust all SSL certificates
     */
    public static final String PROPERTY_HTTP_TRUST_ALL_CERTIFICATES =
        "njams.sdk.communication.http.ssl.unsafe.trustAllCertificates";
    /**
     * Set to <code>true</code> to enabled GZIP compression for request bodies. Needs to be supported by
     * nJAMS server! Disabled by default.
     */
    public static final String PROPERTY_HTTP_COMPRESSION_ENABLED =
        "njams.sdk.communication.http.compression.enabled";

    /**
     * This is just the prefix for all Kafka Properties
     */
    public static final String PROPERTY_KAFKA_PREFIX = "njams.sdk.communication.kafka.";

    /**
     * This is the only mandatory client property that is used to list bootstrap servers
     * (comma separated) for connecting to a Kafka cluster.
     */
    public static final String PROPERTY_KAFKA_BOOTSTRAP_SERVERS =
        "njams.sdk.communication.kafka.client.bootstrap.servers";

    /**
     * This is the prefix of the event, project, commands, and optional error topics.
     * The topics have to exist in the according Kafka cluster. Default is 'njams'.
     */
    public static final String PROPERTY_KAFKA_TOPIC_PREFIX = "njams.sdk.communication.kafka.topicPrefix";

    /**
     * The producer sending replies for processed commands will be closed if it has not been used for the specified time
     * in milliseconds. The default is 30000.
     */
    public static final String PROPERTY_KAFKA_REPLY_PRODUCER_IDLE_TIME =
        "njams.sdk.communication.kafka.replyProducerIdleTime";

    /**
     * All properties with these prefixes are directly passed to the Kafka clients used by the SDK:
     * <ul>
     * <li>njams.sdk.communication.kafka.client.*
     * <li>njams.sdk.communication.kafka.consumer.*
     * <li>njams.sdk.communication.kafka.producer.*
     * <li>njams.sdk.communication.kafka.admin.*
     * </ul>
     * E.g., the only one mandatory setting is boostrap.servers (see above). pref.client.* is a shortcut for
     * properties that shall be used for all client types, i.e., it includes the consumer, producer, and admin prefix.
     * Properties using one of the other prefixes will only be used for the respective client type, e.g., any setting
     * starting with pref.consumer.* will only be used when creating a Kafka consumer, while settings starting with
     * pref.client.* will be used for all client types.
     * <p>
     * Refer to the official Kafka client documentation for supported properties.
     */
    public static final String PROPERTY_KAFKA_CLIENT_PREFIX = "njams.sdk.communication.kafka.client.";

    /**
     * Consumer properties
     */
    public static final String PROPERTY_KAFKA_CONSUMER_PREFIX = "njams.sdk.communication.kafka.consumer.";

    /**
     * Producer properties
     */
    public static final String PROPERTY_KAFKA_PRODUCER_PREFIX = "njams.sdk.communication.kafka.producer.";

    /**
     * Admin client properties
     */
    public static final String PROPERTY_KAFKA_ADMIN_PREFIX = "njams.sdk.communication.kafka.admin.";

    /**
     * Prefix for the jms communication properties
     */
    public static final String PROPERTY_JMS_PREFIX = "njams.sdk.communication.jms.";

    /**
     * Delivery mode for JMS Sender. Attention: NonPersistent might lead to data loss.<br>
     * Supported values (ignoring case)
     * <ul>
     * <li><code>NON_PERSISTENT</code> or <code>NONPERSISTENT</code>: JMS non-persistent delivery mode</li>
     * <li><code>RELIABLE</code> (Tibco EMS only): Tibco EMS reliable delivery mode (since 5.0.3)</li>
     * <li><i>all others</i>: JMS persistent delivery mode (default)</li>
     * </ul>
     */
    public static final String PROPERTY_JMS_DELIVERY_MODE = "njams.sdk.communication.jms.delivery.mode";

    /**
     * This is for determining which {@link ConnectionFactory} to use.<br>
     * Since 5.0.3 this property is used for a two-step lookup. At first, the value is used to lookup a
     * {@link JmsFactory} implementation which defaults to {@link JndiJmsFactory}
     * if no match was found. In the second step, the {@link JndiJmsFactory} uses the value
     * for a JNDI lookup to obtain a {@link ConnectionFactory}.
     * @see JmsFactory#find(ClientSettings)
     * @see #PROPERTY_JMS_JMSFACTORY
     */
    public static final String PROPERTY_JMS_CONNECTION_FACTORY = "njams.sdk.communication.jms.connectionFactory";

    /**
     * This property allows specifying a {@link JmsFactory} to be loaded via SPI. This property is usually not
     * necessary since the the {@value #PROPERTY_JMS_CONNECTION_FACTORY} property is used for the same. I.e., this
     * separate property is only needed when a specific {@link JmsFactory} is needed that also uses the
     * {@value #PROPERTY_JMS_CONNECTION_FACTORY} key, e.g., when implementing a different JNDI lookup mechanism.
     * @since 5.0.3
     */
    public static final String PROPERTY_JMS_JMSFACTORY = "njams.sdk.communication.jms.jmsFactory";

    /**
     * This is the username to connect to JMS provider.
     */
    public static final String PROPERTY_JMS_USERNAME = "njams.sdk.communication.jms.username";

    /**
     * This is the password to connect to the JMS provider
     */
    public static final String PROPERTY_JMS_PASSWORD = "njams.sdk.communication.jms.password";

    /**
     * This is the prefix of the event, command and error queue, and the commands topic.
     */
    public static final String PROPERTY_JMS_DESTINATION = "njams.sdk.communication.jms.destination";

    /**
     * This is the the commands topic to use, if its name does not match the common convention using the
     * destination prefix and <code>.commands</code> suffix.
     */
    public static final String PROPERTY_JMS_COMMANDS_DESTINATION = "njams.sdk.communication.jms.destination.commands";
    /**
     * This setting specifies whether the used JMS implementation supports message selectors for consumers.<br>
     * The default is <code>true</code> since this is actually JMS standard, and it's always preferable to use message
     * selectors if available.<br>
     * If set to <code>false</code> a workaround implementation is used that requires an additional queue with
     * suffix <code>.project</code>
     * @since 5.0.3
     */
    public static final String PROPERTY_JMS_SUPPORTS_MESSAGE_SELECTOR =
        "njams.sdk.communication.jms.supportsMessageSelector";

    /**
     * Specifies the JNDI initial context factory.
     */
    public static final String PROPERTY_JMS_INITIAL_CONTEXT_FACTORY = PROPERTY_JMS_PREFIX
        + Context.INITIAL_CONTEXT_FACTORY;

    /**
     * Specifies the JNDI security principal.
     */
    public static final String PROPERTY_JMS_SECURITY_PRINCIPAL = PROPERTY_JMS_PREFIX + Context.SECURITY_PRINCIPAL;

    /**
     * Specifies the JNDI security credentials.
     */
    public static final String PROPERTY_JMS_SECURITY_CREDENTIALS = PROPERTY_JMS_PREFIX + Context.SECURITY_CREDENTIALS;

    /**
     * Specifies the JNDI provider URL.
     */
    public static final String PROPERTY_JMS_PROVIDER_URL = PROPERTY_JMS_PREFIX + Context.PROVIDER_URL;

    /**
     * Prefix for the SSL communication properties
     */
    public static final String SSLPREFIX = PROPERTY_JMS_PREFIX + "javax.net.ssl.";
    /**
     * Specifies the keyStore.
     */
    public static final String PROPERTY_JMS_KEYSTORE = SSLPREFIX + "keyStore";
    /**
     * Specifies the keyStore password.
     */
    public static final String PROPERTY_JMS_KEYSTOREPASSWORD = SSLPREFIX + "keyStorePassword";
    /**
     * Specifies the keyStore Type.
     */
    public static final String PROPERTY_JMS_KEYSTORETYPE = SSLPREFIX + "keyStoreType";
    /**
     * Specifies the trustStore.
     */
    public static final String PROPERTY_JMS_TRUSTSTORE = SSLPREFIX + "trustStore";
    /**
     * Specifies the trustStore password.
     */
    public static final String PROPERTY_JMS_TRUSTSTOREPASSWORD = SSLPREFIX + "trustStorePassword";
    /**
     * Specifies the trustStore Type.
     */
    public static final String PROPERTY_JMS_TRUSTSTORETYPE = SSLPREFIX + "trustStoreType";

    /**
     * When this setting is true (default) nJAMS enables dataMasking.
     * When false, DataMasking is disabled for the regexes defined in the properties AND in the config.json.
     */
    public static final String PROPERTY_DATA_MASKING_ENABLED = "njams.sdk.datamasking.enabled";

    /**
     * You can define multiple datamasking regex key-value pairs. Always use the prefix
     * for the regexes that should be used for pattern matching to find data that you want to be masked.
     * <ul>
     *     <li>njams.sdk.datamasking.regex.NAME_FOR_REGEX_1=THE_REGEX_1
     *     <li>njams.sdk.datamasking.regex.NAME_FOR_REGEX_2=THE_REGEX_2
     * </ul>
     * <p>
     * Examples:
     * <pre>
     * "njams.sdk.datamasking.regex.maskAll = ."
     * would mask all data.
     *
     * "njams.sdk.datamasking.regex.maskPasswords = password: ."
     * would mask every occurrence of a string that looks like this:
     * "password: AnythingCanStandHere" and would result in a string that looks like this "*******************"
     * </pre>
     */
    public static final String PROPERTY_DATA_MASKING_REGEX_PREFIX = "njams.sdk.datamasking.regex.";

    /**
     * Prefix for defining process-exclusion regular expressions. Append an arbitrary name to the
     * prefix and set a Java regular expression as the value; multiple patterns may be defined using
     * different names:
     * <ul>
     *     <li>njams.sdk.process.exclude.regex.NAME_FOR_REGEX_1=THE_REGEX_1
     *     <li>njams.sdk.process.exclude.regex.NAME_FOR_REGEX_2=THE_REGEX_2
     * </ul>
     * <p>
     * A process is excluded from processing when its path matches any of these patterns. Matching is
     * a full match (the whole path must match) and is case-sensitive; prefix the expression with
     * {@code (?i)} for case-insensitive matching. The path is matched in its canonical form, e.g.
     * {@code >MyApp>MyProcess>}.
     * <p>
     * These patterns are applied <em>in addition</em> to the process filters configured via the
     * nJAMS server: a process is excluded if it is excluded by either mechanism. An explicit
     * <em>include</em> filter for a process takes precedence and re-includes it. Invalid expressions
     * are ignored (logged as a warning).
     * <p>
     * Example:
     * <pre>
     * "njams.sdk.process.exclude.regex.internal = &gt;MyApp&gt;internal&gt;.*"
     * excludes every process whose path starts with "&gt;MyApp&gt;internal&gt;".
     * </pre>
     */
    public static final String PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX = "njams.sdk.process.exclude.regex.";

    /**
     * Server version compatibility switch. When set to a server version string (e.g., {@code "6.1"}),
     * the SDK restores behavioral compatibility with that server version and earlier.
     * Leave unset (or blank) to use current SDK behavior.
     *
     * <p>Effects when set to {@code "6.1"}:
     * <ul>
     *   <li>Transitions created without an explicit name carry the transition ID as their name,
     *       matching the behavior prior to the SDK 6.0 fix for SDK-427. This is required when
     *       sending process models to nJAMS server versions that do not yet support nameless transitions.</li>
     * </ul>
     *
     * <p>Remove this setting once the deployed nJAMS server supports all current SDK behaviors.
     *
     * @deprecated Transitional bridge only. Will be removed from the SDK in a future release
     *             once support for older server versions is dropped.
     */
    @Deprecated
    public static final String PROPERTY_SERVER_COMPATIBILITY = "njams.sdk.server.compatibility";

}
