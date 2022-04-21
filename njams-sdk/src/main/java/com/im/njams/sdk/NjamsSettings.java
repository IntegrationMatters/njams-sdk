package com.im.njams.sdk;

/**
 * This class is a list and documentation for all settings, which can be used in SDK.
 */
public class NjamsSettings {

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
    
}
