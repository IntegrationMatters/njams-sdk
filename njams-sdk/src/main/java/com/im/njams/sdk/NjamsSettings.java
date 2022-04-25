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
     * communication implementation will be used
     */
    public static final String PROPERTY_COMMUNICATION = "njams.sdk.communication";

    /**
     * Setting for enabling the logAllErrors feature.
     */
    public static final String PROPERTY_LOG_ALL_ERRORS = "njams.sdk.logAllErrors";

    /**
     * Setting for truncate limit (nJAMS strip-mode). Number of activities/events before messages are truncated.
     */
    public static final String PROPERTY_TRUNCATE_LIMIT = "njams.sdk.truncateActivitiesLimit";

    /**
     * Setting for truncating successful jobs, provided that they were processed as single message.
     */
    public static final String PROPERTY_TRUNCATE_ON_SUCCESS = "njams.sdk.truncateOnSuccess";


    //     _____ _ _            _      _____ _____  _  __
    //    / ____| (_)          | |    / ____|  __ \| |/ /
    //   | |    | |_  ___ _ __ | |_  | (___ | |  | | ' /
    //   | |    | | |/ _ \ '_ \| __|  \___ \| |  | |  <
    //   | |____| | |  __/ | | | |_   ____) | |__| | . \
    //    \_____|_|_|\___|_| |_|\__| |_____/|_____/|_|\_\
    /**
     * This property is a flush criteria with a default of 5mb.
     * If the flush size of the @{@link com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage}
     * exceeds this threshold, the message will be flushed
     */
    public static final String PROPERTY_FLUSH_SIZE = "njams.client.sdk.flushsize";

    /**
     * This property is a flush criteria with a default of 30s.
     * If no @{@link com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage}
     * has been sent in the last 30 seconds, it will be flushed
     */
    public static final String PROPERTY_FLUSH_INTERVAL = "njams.client.sdk.flush_interval";

    /**
     * This property's default is 1 sender thread as core thread
     * (that means it can't be closed even if its idle time has been exceeded)
     * that can send project and log messages to the server.
     */
    public static final String PROPERTY_MIN_SENDER_THREADS = "njams.client.sdk.minsenderthreads";

    /**
     * This property's default is 8 sender threads as maximum threads that can be used.
     * This means if there are more messages to handle than there are sender threads at the moment
     * and the threshold hasn't exceeded, a new thread will be started. If the thread isn't in use for
     * (look below njams.client.sdk.senderthreadidletime), the thread will be removed.
     */
    public static final String PROPERTY_MAX_SENDER_THREADS = "njams.client.sdk.maxsenderthreads";

    /**
     * This property's default is 8 messages that can be hold in the message Queue before the
     * messages will be discarded or client will stop processing until the queue has space again.
     */
    public static final String PROPERTY_MAX_QUEUE_LENGTH = "njams.client.sdk.maxqueuelength";

    /**
     * This property's default is 10000 (ms) that means that idle sender threads that haven't send any
     * message in the last 10 seconds and are not core threads will be removed.
     */
    public static final String PROPERTY_SENDER_THREAD_IDLE_TIME = "njams.client.sdk.senderthreadidletime";

    /**
     * This property decides what to do with a logmessage that couldn't be delivered (because of connection loss, full queue, etc.)
     * Possible values are: none|onconnectionloss|discard (Default is none)
     */
    public static final String PROPERTY_DISCARD_POLICY = "njams.client.sdk.discardpolicy";

    /**
     * If set to <code>true</code> communications (senders and receivers) will be shared accross multiple {@link Njams}
     * instances if supported by the configured implementations. By default (or if set to <code>false</code>) each
     * {@link Njams} instance uses a dedicated instance of sender and receiver pools.
     */
    public static final String PROPERTY_SHARED_COMMUNICATIONS = "njams.client.sdk.sharedcommunications";

    /**
     * New field subProcessPath has been added for Messageformat 4.1.0
     * <p>
     * This Property can be set to use deprecated format; this might be used when sending to a server not compatible
     * because he uses an older Messageformat version.
     */
    public static final String PROPERTY_USE_DEPRECATED_PATH_FIELD_FOR_SUBPROCESSES =
        "njams.client.sdk.deprecatedsubprocesspathfield";

    /**
     * If set to <code>true</code> secure XML processing feature will NOT be inititalzied:
     * <p>
     * factory.setAttribute(XMLConstants.FEATURE_SECURE_PROCESSING, false);
     * <p>
     * This can be helpful for an environment containing an old XML lib, which does not support this.
     */
    public static final String PROPERTY_DISABLE_SECURE_PROCESSING = "njams.client.sdk.disable.secure.processing";


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
    public static final String NJAMS_SUBAGENT_ENABLED = "njams.sdk.subagent.enabled";

    /**
     * Name of the property port where the nJAMS Agent runs and ArgosSender will send metrics
     */
    public static final String NJAMS_SUBAGENT_PORT = "njams.sdk.subagent.port";

    /**
     * Name of the property host where the nJAMS Agent runs and ArgosSender will send metrics
     */
    public static final String NJAMS_SUBAGENT_HOST = "njams.sdk.subagent.host";


    //    _    _ _______ _______ _____
    //   | |  | |__   __|__   __|  __ \
    //   | |__| |  | |     | |  | |__) |
    //   |  __  |  | |     | |  |  ___/
    //   | |  | |  | |     | |  | |
    //   |_|  |_|  |_|     |_|  |_|

    /**
     * The URL, where the nJAMS Server is running and reachable (eg. <a href="http://localhost:8080/njams/">http://localhost:8080/njams/</a>)
     */
    public static final String PROPERTY_HTTP_BASE_URL = "njams.sdk.communication.http.base.url";

    /**
     * This is the prefix of the Dataprovider you want to send your Events to.
     */
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
     * This is the only mandatory client property that is used to list bootstrap servers
     * (comma separated) for connecting to a Kafka cluster.
     */
    public static final String PROPERTY_KAFKA_BOOTSTRAP_SERVERS = "njams.sdk.communication.kafka.bootstrap.servers";

    /**
     * This is the prefix of the event, project, commands, and optional error topics.
     * The topics have to exist in the according Kafka cluster. Default is 'njams'.
     */
    public static final String PROPERTY_KAFKA_TOPIC_PREFIX = "njams.sdk.communication.kafka.topicPrefix";

    /**
     * The producer sending replies for processed commands will be closed if it has not been used for the specified time
     * in milliseconds. The default is 30000.
     */
    public static final String PROPERTY_KAFKA_REPLY_PRODUCER_IDLE_TIME = "njams.sdk.communication.kafka.replyProducerIdleTime";

    /**
     * Allows specifying a specific commands topic, overriding the default that is resolved from the topicPrefix setting.
     */
    public static final String PROPERTY_KAFKA_COMMANDS_TOPIC = "njams.sdk.communication.kafka.commandsTopic";

    /**
     * All properties with these prefixes are directly passed to the Kafka clients used by the SDK:
     * <p>
     * njams.sdk.communication.kafka.client.*
     * njams.sdk.communication.kafka.consumer.*
     * njams.sdk.communication.kafka.producer.*
     * njams.sdk.communication.kafka.admin.*
     * <p>
     * E.g., the only one mandatory setting is boostrap.servers (see above). pref.client.* is a shortcut for
     * properties that shall be used for all client types, i.e., it includes the consumer, producer, and admin prefix.
     * Properties using one of the other prefixes will only be used for the respective client type, e.g., any setting
     * starting with pref.consumer.* will only be used when creating a Kafka consumer, while settings starting with
     * pref.client.* will be used for all client types.
     * <p>
     * Refer to the official Kafka client documentation for supported properties.
     */
    public static final String PROPERTY_KAFKA_CLIENT_PREFIX = "njams.sdk.communication.kafka.client.";
    // Consumer properties
    public static final String PROPERTY_KAFKA_CONSUMER_PREFIX = "njams.sdk.communication.kafka.client.consumer.";
    // Producer properties
    public static final String PROPERTY_KAFKA_PRODUCER_PREFIX = "njams.sdk.communication.kafka.client.producer.";
    // Admin properties
    public static final String PROPERTY_KAFKA_ADMIN_PREFIX = "njams.sdk.communication.kafka.client.admin.";

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
    public static final String PROPERTY_JMS_INITIAL_CONTEXT_FACTORY = PROPERTY_JMS_PREFIX + Context.INITIAL_CONTEXT_FACTORY;

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

}
