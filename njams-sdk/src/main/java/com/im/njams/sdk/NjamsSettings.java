/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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

import javax.naming.Context;

/**
 * This class is a list and documentation for all settings, which can be used in SDK.
 */
public class NjamsSettings {

    //     _____ _____  _  __
    //    / ____|  __ \| |/ /
    //   | (___ | |  | | ' /
    //    \___ \| |  | |  <
    //    ____) | |__| | . \
    //   |_____/|_____/|_|\_\
    //
    /**
     * Property key for communication properties which specifies which
     * communication implementation will be used.
     * <p>
     * Possible values:
     * <ul>
     * <li>JMS
     * <li>HTTP
     * <li>HTTPS
     * <li>KAFKA
     * <li>YOUR-OWN-COMMUNICATION
     * </ul>
     */
    public static final String PROPERTY_COMMUNICATION = "njams.sdk.communication";

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
     * This setting is true (which is the default), the SDK will generate a unique client id, which is used to identify
     * the correct client in a multi node/replica/pod container environment.
     * <p>
     * An additional feature flag (named containerMode) will be activated and sent to server with ProjectMessage.
     * With this information the server knows that the client is running in a multi node container environment, and
     * has to use the clientId for node selection.
     * <p>
     * Some commands will only be sent to one of the multi container nodes (eg. replay and all get commands). The rest
     * will be sent to all nodes because for example a change to Logmode has to be processed by all nodes.
     */
    public static final String PROPERTY_CONTAINER_MODE = "njams.sdk.containerMode";

    //     _____ _ _            _      _____ _____  _  __
    //    / ____| (_)          | |    / ____|  __ \| |/ /
    //   | |    | |_  ___ _ __ | |_  | (___ | |  | | ' /
    //   | |    | | |/ _ \ '_ \| __|  \___ \| |  | |  <
    //   | |____| | |  __/ | | | |_   ____) | |__| | . \
    //    \_____|_|_|\___|_| |_|\__| |_____/|_____/|_|\_\
    /**
     * This property is a flush criteria with a default of 5mb.
     * <p>
     * If the flush size of the @{@link com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage}
     * exceeds this threshold, the message will be flushed
     */
    public static final String PROPERTY_FLUSH_SIZE = "njams.sdk.flushsize";
    /**
     * @deprecated Just for downward compatibility.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String OLD_FLUSH_SIZE = "njams.client.sdk.flushsize";

    /**
     * This property is a flush criteria with a default of 30s.
     * <p>
     * If no @{@link com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage}
     * has been sent in the last 30 seconds, it will be flushed
     */
    public static final String PROPERTY_FLUSH_INTERVAL = "njams.sdk.flush_interval";
    /**
     * @deprecated Just for downward compatibility.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String OLD_FLUSH_INTERVAL = "njams.client.sdk.flush_interval";

    /**
     * This property's default is 1 sender thread as core thread
     * (that means it can't be closed even if its idle time has been exceeded)
     * that can send project and log messages to the server.
     */
    public static final String PROPERTY_MIN_SENDER_THREADS = "njams.sdk.minsenderthreads";
    /**
     * @deprecated Just for downward compatibility.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String OLD_MIN_SENDER_THREADS = "njams.client.sdk.minsenderthreads";

    /**
     * This property's default is 8 sender threads as maximum threads that can be used.
     * This means if there are more messages to handle than there are sender threads at the moment
     * and the threshold hasn't exceeded, a new thread will be started. If the thread isn't in use for
     * (look below njams.client.sdk.senderthreadidletime), the thread will be removed.
     */
    public static final String PROPERTY_MAX_SENDER_THREADS = "njams.sdk.maxsenderthreads";
    /**
     * @deprecated Just for downward compatibility.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String OLD_MAX_SENDER_THREADS = "njams.client.sdk.maxsenderthreads";

    /**
     * This property's default is 8 messages that can be hold in the message Queue before the
     * messages will be discarded or client will stop processing until the queue has space again.
     */
    public static final String PROPERTY_MAX_QUEUE_LENGTH = "njams.sdk.maxqueuelength";
    /**
     * @deprecated Just for downward compatibility.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String OLD_MAX_QUEUE_LENGTH = "njams.client.sdk.maxqueuelength";

    /**
     * This property's default is 10000 (ms) that means that idle sender threads that haven't send any
     * message in the last 10 seconds and are not core threads will be removed.
     */
    public static final String PROPERTY_SENDER_THREAD_IDLE_TIME = "njams.sdk.senderthreadidletime";
    /**
     * @deprecated Just for downward compatibility.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String OLD_SENDER_THREAD_IDLE_TIME = "njams.client.sdk.senderthreadidletime";

    /**
     * This property decides what to do with a logmessage that couldn't be delivered (because of connection loss, full queue, etc.)
     * Possible values are:
     * <ul>
     * <li>none (Default)
     * <li>onconnectionloss
     * <li>discard
     * </ul>
     */
    public static final String PROPERTY_DISCARD_POLICY = "njams.sdk.discardpolicy";
    /**
     * @deprecated Just for downward compatibility.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String OLD_DISCARD_POLICY = "njams.client.sdk.discardpolicy";

    /**
     * If set to <code>true</code> communications (senders and receivers) will be shared across multiple {@link Njams}
     * instances if supported by the configured implementations. By default (or if set to <code>false</code>) each
     * {@link Njams} instance uses a dedicated instance of sender and receiver pools.
     */
    public static final String PROPERTY_SHARED_COMMUNICATIONS = "njams.sdk.sharedcommunications";
    /**
     * @deprecated Just for downward compatibility.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String OLD_SHARED_COMMUNICATIONS = "njams.client.sdk.sharedcommunications";

    /**
     * New field subProcessPath has been added for Messageformat 4.1.0
     * <p>
     * This property can be set to use deprecated format; this might be used when sending to a server not compatible
     * because he uses an older Messageformat version.
     */
    public static final String PROPERTY_USE_DEPRECATED_PATH_FIELD_FOR_SUBPROCESSES =
            "njams.sdk.deprecatedsubprocesspathfield";
    /**
     * @deprecated Just for downward compatibility.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String OLD_USE_DEPRECATED_PATH_FIELD_FOR_SUBPROCESSES =
            "njams.client.sdk.deprecatedsubprocesspathfield";

    /**
     * If set to <code>true</code> secure XML processing feature will NOT be initialized:
     * <p>
     * factory.setAttribute(XMLConstants.FEATURE_SECURE_PROCESSING, false);
     * <p>
     * This can be helpful for an environment containing an old XML lib, which does not support this.
     */
    public static final String PROPERTY_DISABLE_SECURE_PROCESSING = "njams.sdk.disable.secure.processing";
    /**
     * @deprecated Just for downward compatibility.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String OLD_DISABLE_SECURE_PROCESSING = "njams.client.sdk.disable.secure.processing";

    /**
     * If set to <code>true</code> collecting job start-data is disabled. Please note that this also disables replay
     * functionality.
     * @deprecated This gets in the way with the new possibility for configuring recording in nJAMS server 5.0.
     * Use {@link #PROPERTY_BOOSTRAP_RECORDING} as default setting and configure via nJAMS server.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String PROPERTY_DISABLE_STARTDATA = "njams.sdk.disable.startdata";
    /**
     * @deprecated Just for downward compatibility.
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String OLD_DISABLE_STARTDATA = "njams.client.sdk.disable.startdata";

    /**
     * Since 5.0.0.
     * This setting is used only once when creating a fresh configuration for an {@link Njams} instance.
     * It is used in that case as default setting for the client- (engine-) wide recording flag. 
     * Default is <code>true</code>.
     */
    public static final String PROPERTY_BOOSTRAP_RECORDING = "njams.sdk.bootstrap.recording";

    //     _____      _   _   _
    //    / ____|    | | | | (_)
    //   | (___   ___| |_| |_ _ _ __   __ _ ___
    //    \___ \ / _ \ __| __| | '_ \ / _` / __|
    //    ____) |  __/ |_| |_| | | | | (_| \__ \
    //   |_____/ \___|\__|\__|_|_| |_|\__, |___/
    //                                 __/ |
    //                                |___/
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
     */
    public static final String PROPERTY_SETTINGS_PROVIDER = "njams.sdk.settings.provider";

    /**
     * Specifies the path to the settings file for "file" settings provider.
     */
    public static final String PROPERTY_FILE_SETTINGS_FILE = "njams.sdk.settings.file";

    /**
     * Property key for the propertiesFile provider, specifying the path to the properties file to be used.
     */
    public static final String PROPERTY_PROPERTIES_FILE_SETTINGS_FILE = "njams.sdk.settings.properties.file";
    /**
     * Default property key for loading parent (default) configuration file for the propertiesFile provider.
     * See {@link #PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_KEY} for using an alternative key.
     */
    public static final String PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_FILE = "njams.sdk.settings.properties.parent";
    /**
     * Allows to override the default parent file key for the propertiesFile provider.
     * ({@value #PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_FILE}).
     */
    public static final String PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_KEY = "njams.sdk.settings.properties.parentKey";

    //
    //       /\
    //      /  \   _ __ __ _  ___  ___
    //     / /\ \ | '__/ _` |/ _ \/ __|
    //    / ____ \| | | (_| | (_) \__ \
    //   /_/    \_\_|  \__, |\___/|___/
    //                  __/ |
    //                 |___/
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

    //    _    _ _______ _______ _____
    //   | |  | |__   __|__   __|  __ \
    //   | |__| |  | |     | |  | |__) |
    //   |  __  |  | |     | |  |  ___/
    //   | |  | |  | |     | |  | |
    //   |_|  |_|  |_|     |_|  |_|

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
     * @deprecated Replaced by {@link #PROPERTY_HTTP_DATAPROVIDER_SUFFIX}
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String PROPERTY_HTTP_DATAPROVIDER_PREFIX = "njams.sdk.communication.http.dataprovider.prefix";

    /**
     * This is the full path to your certificate file to use for SSL
     */
    public static final String PROPERTY_HTTP_SSL_CERTIFICATE_FILE = "njams.sdk.communication.http.ssl.certificate.file";

    //    _  __          ______ _  __
    //   | |/ /    /\   |  ____| |/ /    /\
    //   | ' /    /  \  | |__  | ' /    /  \
    //   |  <    / /\ \ |  __| |  <    / /\ \
    //   | . \  / ____ \| |    | . \  / ____ \
    //   |_|\_\/_/    \_\_|    |_|\_\/_/    \_\

    /**
     * This is just the prefix for all Kafka Properties
     */
    public static final String PROPERTY_KAFKA_PREFIX = "njams.sdk.communication.kafka.";

    /**
     * How to treat messages larger than the maximum size supported by Kafka.<br>
     * <code>discard</code> - discard large messages; this option is deprecated and only for compatibility. Use
     * this option only with nJAMS server 5.2.0 and 5.2.1 which does not support split messages.<br>
     * <code>split</code> - split large messages into smaller chunks; this the default and will be the only option
     * in future<br>
     *
     * @deprecated This is just for compatibility with nJAMS server 5.2.0 and 5.2.1 and will be removed in future.
     */
    @Deprecated
    public static final String PROPERTY_KAFKA_LARGE_MESSAGE_MODE = "njams.sdk.communication.kafka.largeMessageMode";

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
     * For testing only.
     * Allows specifying a specific commands topic, overriding the default that is resolved from the topicPrefix setting.
     *
     * @deprecated This property is being removed in a future release.
     */
    @Deprecated
    public static final String PROPERTY_KAFKA_COMMANDS_TOPIC = "njams.sdk.communication.kafka.commandsTopic";

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

    //         _ __  __  _____
    //        | |  \/  |/ ____|
    //        | | \  / | (___
    //    _   | | |\/| |\___ \
    //   | |__| | |  | |____) |
    //    \____/|_|  |_|_____/

    /**
     * Prefix for the jms communication properties
     */
    public static final String PROPERTY_JMS_PREFIX = "njams.sdk.communication.jms.";

    /**
     * Delivery mode for JMS Sender. Attention: NonPersistent might lead to data loss.
     */
    public static final String PROPERTY_JMS_DELIVERY_MODE = "njams.sdk.communication.jms.delivery.mode";

    /**
     * This is for determining which ConnectionFactory to use.
     */
    public static final String PROPERTY_JMS_CONNECTION_FACTORY = "njams.sdk.communication.jms.connectionFactory";

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
     * This is the prefix of the commands topic, if it is different to the one that is set in destination.
     */
    public static final String PROPERTY_JMS_COMMANDS_DESTINATION = "njams.sdk.communication.jms.destination.commands";

    /**
     * Specifies the jndi initial context factory.
     */
    public static final String PROPERTY_JMS_INITIAL_CONTEXT_FACTORY = PROPERTY_JMS_PREFIX
            + Context.INITIAL_CONTEXT_FACTORY;

    /**
     * Specifies the jndi security principal.
     */
    public static final String PROPERTY_JMS_SECURITY_PRINCIPAL = PROPERTY_JMS_PREFIX + Context.SECURITY_PRINCIPAL;

    /**
     * Specifies the jndi security credentials.
     */
    public static final String PROPERTY_JMS_SECURITY_CREDENTIALS = PROPERTY_JMS_PREFIX + Context.SECURITY_CREDENTIALS;

    /**
     * Specifies the jndi provider url.
     */
    public static final String PROPERTY_JMS_PROVIDER_URL = PROPERTY_JMS_PREFIX + Context.PROVIDER_URL;

    /**
     * Prefix for the ssl communication properties
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

    //    _____        _                            _    _
    //   |  __ \      | |                          | |  (_)
    //   | |  | | __ _| |_ __ _ _ __ ___   __ _ ___| | ___ _ __   __ _
    //   | |  | |/ _` | __/ _` | '_ ` _ \ / _` / __| |/ / | '_ \ / _` |
    //   | |__| | (_| | || (_| | | | | | | (_| \__ \   <| | | | | (_| |
    //   |_____/ \__,_|\__\__,_|_| |_| |_|\__,_|___/_|\_\_|_| |_|\__, |
    //                                                            __/ |
    //                                                           |___/
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

}
