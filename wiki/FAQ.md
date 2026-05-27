# How can I provide startup settings to njams? #
To set which settings provider to use, you have to create a Properties object **props**, with one property:
- njams.sdk.settings.provider=**NAME_OF_THE_SETTINGSPROVIDER**

After that you call the SettingsProviderFactory.getSettingsProvider(**props**) to get a provider object.

# Which settings providers are available? #
There are 4 types of settings providers already implemented (You can write your own as well, use the SettingsProvider interface to implement)

## FileSettingsProvider ##
**NAME_OF_THE_SETTINGSPROVIDER** = "file"

This provider reads .json files. For the provider to know where the file is, you either have to create a Properties object **fileProps**, with one property:
 - njams.sdk.settings.file=**PATH_OF_THE_JSON_FILE**
and call configure(**fileProps**), or you can just create the FileSettingsObject, it creates a File object for the name "config.json".

After that, you can call loadSettings() to get a settings object with the read properties.

## MemorySettingsProvider ##
**NAME_OF_THE_SETTINGSPROVIDER** = "memory"

This provider holds the properties just in memory. When you call configure(**fileProps**), they will be saved in the MemorySettingsProvider as settings. If you call loadSettings() afterwards, you will get them back as a Settings object. 
***
**Attention**
A client restart will result in loosing all settings.
***

## PropertiesFileSettingsProvider ##
**NAME_OF_THE_SETTINGSPROVIDER** = "propertiesFile"

This provider loads and saves settings to a specified file in common Properties format. It also allows recursively 
loading parent properties via key! (Explained in the "Which settings can I set" section)

You either have to create a Properties object **fileProps**, with one property:
 - njams.sdk.settings.properties.file=**PATH_OF_THE_PROPERTIES_FILE**

and call configure(**fileProps**), or you don't call configure at all, a File object with the name
"config.properties" has been created on initialisation, or you call setFile(File **YOUR_FILE**) to set your properties file to be read from.

When you call loadSettings() the properties file will be read, and all its parent files aswell. If the child has any properties that the ancestors have aswell, they will be overridden by the child's property (only in the Settings object, not in the real file!).

## SystemPropertiesSettingsProvider ##
**NAME_OF_THE_SETTINGSPROVIDER** = "systemProperties"

This provider uses Java properties for the settings.
If you call configure(**fileProps**), nothing happens.
If you call loadSettings(), the System.getProperties() properties will be used as properties for the Settings object.

# Which settings can I use? #

## For Communication: ##
- njams.sdk.communication=JMS|HTTP|CLOUD|KAFKA|YOUR-OWN-COMMUNICATION (HTTPS `deprecated since 5.0.0`)

This property must be set for choosing between the communication types. For each of the previous types you have to use matching set of more properties:

- njams.sdk.communication.maxMessageSize=BYTES `since 5.0.3`

Setting this property allows limiting the message size for each message being sent to nJAMS server. Use this option if your messaging middleware limits the maximum message size. Set to the number of bytes that are allowed by you middleware. Note that this setting is only applied to message bodies. Each message also comes with some headers/properties that need to be taken into account, according to the limits of your messaging middleware. Disabling this option (by setting to 0 or less) is beneficial if you are sure, that the messages sent by the client will never exceed your environments maximum. In case of Kafka, Kafka's producer client property `max.request.size` is also respected, i.e., the smaller value is used. Default is 0 (except for Kafka) and the minimum is 10,240 bytes. Requires nJAMS server 6.1.2 or later for transport other than Kafka.


## For JMS ##

- njams.sdk.communication.jms := **pref** is always the prefix of the properties key for JMS properties. This has to be prepended on each properties left-hand side if the properties should be handed over to an initial context factory.

- njams.sdk.communication=JMS

- **pref**.java.naming.factory.initial=

The initial context factory's class that is used for setting up the JNDI connection.

- **pref**.java.naming.security.principal=
This is the username for JNDI

- **pref**.java.naming.security.credentials=

This is the password for JNDI

- **pref**.java.naming.provider.url=

This is the URL to use for accessing the naming provider.

- **pref**.connectionFactory=

This is for determining which JMS ConnectionFactory to use. The value is used for looking up a ConnectionFactory in the configured JNDI context.
Starting with 5.0.3, the usage of this property has changed. This property is now used for a two-step lookup. In the first step, it's used for finding a SDK internal JmsFactory that implements creating required JMS functionality. Only when no such factory was found, the configured value is used for JNDI lookup as before. The JmsFactory lookup allows implementing connection factories without using JNDI.

- **pref**.jmsFactory `since 5.0.3`

This property serves the same purpose as connectionFactory but without falling back to a JNDI lookup. This property only needs to be used if the selected JmsFactory implementation also relies on the connectionFactory setting, i.e., when the selected JmsFactory is a JNDI variant. This property is usually not used!

- **pref**.username=foo

This is the username for JMS

- **pref**.password=bar

This is the password for JMS

- **pref**.destination=

This is the common prefix for the queues, and topics used.

**Optional**

- **pref**.destination.commands=

This is the name of the commands topic to use, if it is different from the one that is resolved by the common **pref**.destination setting.

- **pref**.supportsMessageSelector=true/false `since 5.0.3`

This property specifies whether the JMS implementation support message selectors (default) or not.
The default is true since this is actually JMS standard, and it's always preferable to use message selectors if available.
If set to false a workaround implementation is used that requires an additional queue with suffix `.project`.
    

## For HTTP/HTTPS: `since 4.2.0`

- njams.sdk.communication=HTTP|HTTPS Since `5.0.0` the HTTPS value is redundant and deprecated, respectively HTTP is used for both cases. Before that, HTTPS had to be used for SSL encrypted connections.

- njams.sdk.communication.http := **pref** is always the prefix of the properties key for http properties. This has to be prepended on each properties left-hand side if the properties should be handed over to an initial context factory.

- **pref**.base.url=NJAMS-RECEIVER-URL (eg. http://localhost:8080/njams/)

This is the URL where nJAMS server is running

- **pref**.dataprovider.suffix=YOUR_DP_SUFFIX (eg. dp1) `since 5.0.0`
- **pref**.dataprovider.prefix=YOUR_DP_SUFFIX (eg. dp1) `deprecated`

This is the path-suffix configured in the dataprovider you want to send your events

- **pref**.connection.test=legacy `since 5.0.0; deprecated`

The http sender uses a connection test for testing whether or not the expected http dataprovider
is available. This test is supported since nJAMS 5.3.4. For older versions the test is tried once,
and, if fails, it switches to a less reliable legacy fallback. However, the failing first attempt
leads to an exception in nJAMS server logs which can be avoided by setting this property
to `legacy`. This is a compatibility setting for old versions of nJAMS server and will be removed
in future. The default uses the try-and-fallback mechanism as explained above.

- **pref**.user=NJAMS_USER `since 5.0.0`

User name if nJAMS server requires authentication for the ingest service. The used account must 
have the _ingest-service_ system privilege.

- **pref**.password=NJAMS_USER_PWD `since 5.0.0`

The password for authenticating the nJAMS user.

- **pref**.proxy.host=PROXY_ADDRESS `since 5.0.0`

If using a proxy, specify its host name or IP address

- **pref**.proxy.port=PROXY_PORT `since 5.0.0`

If using a proxy, specify its port.

- **pref**.proxy.user=PROXY_USER `since 5.0.0`

If using a proxy that requires authentication, specify the according user name.

- **pref**.proxy.password=PROXY_USER_PWD `since 5.0.0`

If using a proxy that requires authentication, specify the according user's password.

- **pref**.ssl.certificate.file=CERTSIFICATE-FILE

This is the full path to your certificate file to use for SSL

- **pref**.ssl.trustStore=TRUST_STORE_FILE `since 5.0.0`

When using a truststore for SSL certificate, specify its full path.

- **pref**.ssl.trustStoreType=TYPE `since 5.0.0`

When using a truststore for SSL certificate, specify its format. Default is `jks`.

- **pref**.ssl.trustStorePassword=TRUST_STORE_PWD `since 5.0.0`

When using a truststore that requires a password for SSL certificate, specify with this property.

- **pref**.ssl.keyStore=KEY_STORE_FILE `since 5.0.0`

When using a keystore for SSL certificate, specify its full path.

- **pref**.ssl.keyStoreType=TYPE `since 5.0.0`

When using a keystore for SSL certificate, specify its format. Default is `jks`.

- **pref**.ssl.keyStorePassword=KEY_STORE_PWD `since 5.0.0`

When using a keystore that requires a password for SSL certificate, specify with this property.

- **pref**.ssl.unsafe.disableHostnameVerification=true/false `since 5.0.0`

When this property is set to `true`, host name verification is disabled when verifying the given certificate.
Disabling host name verification is an **unsafe operation** since it reduces security. Default is `false`.
 
- **pref**.ssl.unsafe.trustAllCertificates=true/false `since 5.0.0`

When this property is set to `true`, certificate are not verified for validity.
Trusting all certificates is an **unsafe operation** since it reduces security. Default is `false`.

- **pref**.compression.enabled=true/false `since 5.0.3`

Setting this property to true enables ZIP compression for http messages bodies. The default is false since this feature requires additional resources (CPU) when active.

## For Kafka:  `since 4.2.0`

- njams.sdk.communication=KAFKA

- njams.sdk.communication.kafka := **pref** is always the prefix of the properties key for Kafka properties. This has to be prepended on each property's left-hand side.

- **pref**.client.bootstrap.servers=HOST1:PORT1[,HOST2:PORT2[,...]]

This is the only mandatory client property that is used to list bootstrap servers (comma separated) for connecting to a Kafka cluster.

- **pref**.topicPrefix=PREFIX

This is the prefix of the event, project, commands, and optional error topics. The topics have to exists in the according Kafka cluster. Default is 'njams'.

- **pref**.replyProducerIdleTime=IDLE_TIME_MS

The producer sending replies for processed commands will be closed if it has not been used for the specified time in milliseconds. The default is 30000.

- **pref**.commandsTopic=COMMANDS_TOPIC `removed in 5.0.0`

Allows specifying a specific commands topic, overriding the default that is resolved from the `topicPrefix` setting. This option will be removed in a future release.

- **pref**.largeMessageMode=discard|split `since 4.2.2` `removed in 5.0.0`

`removed`: This option is only for supporting nJAMS server 5.2.0 and 5.2.1 and has been removed in release 5.0.0.
How to treat messages larger than the maximum size supported by Kafka. With the default value `split` large messages are split into chunks before sending to Kafka. This option requires nJAMS server 5.2.2 or later. The `discard` option is only for using nJAMS server 5.2.0 or 5.2.1 that do not support split Kafka messages. With this option, the SDK will discard messages that are too large for being transported via Kafka.

- **pref**.client.*
- **pref**.consumer.*
- **pref**.producer.*
- **pref**.admin.*

All properties with theses prefixes are directly passed to the Kafka clients used by the SDK. E.g., the only one mandatory setting is `boostrap.servers` (see above). 
**pref**.client.* is a shortcut for properties that shall be used for all client types, i.e., it includes the consumer, producer, and admin prefix. Properties using one of the other prefixes will only be used for the respective client type, e.g., any setting starting with **pref**.consumer.* will only be used when creating a Kafka consumer, while settings starting with **pref**.client.* will be used for all client types.

Refer to the official Kafka client documentation for supported properties.

## For configuration: ##

- njams.sdk.configuration.provider=file|memory (Default is file)

This property is for choosing between saving tracepoints, extract, etc. in memory or in a file.

## For settings: ##

- njams.sdk.settings.properties.parent=**PATH-TO-YOUR-PARENT**

This property holds the path to the parent properties file.

- njams.sdk.settings.properties.parentKey=**YOUR-KEY** (default is njams.sdk.settings.properties.parent)

This property overrides njams.sdk.settings.properties.parent with **YOUR-KEY**. That means that the property:

- **YOUR-KEY=PATH-TO-YOUR-ACTUAL-PARENT** 

would be used instead of **PATH-TO-YOUR-PARENT**.


## For optional parameters: ##

- njams.client.sdk.flushsize=YOUR-FLUSHSIZE (Default is 5242880 (in bytes)) `deprecated since 5.0.0`
- njams.sdk.flushsize=YOUR-FLUSHSIZE (Default is 5242880 (in bytes)) `since 5.0.0`

This property is a flush criterium with a default of 5mb. If the flush size of the logmessage exceedes this threshold, the message will be flushed.

- njams.client.sdk.flush_interval=YOUR-FLUSH-INTERVAL(Default is 30 (in seconds)) `deprecated since 5.0.0`
- njams.sdk.flush_interval=YOUR-FLUSH-INTERVAL(Default is 30 (in seconds)) `since 5.0.0`

This property is a flush criterium with a default of 30s. If no logmessage has been sent in the last 30 seconds, the logmessage will be flushed now.

- njams.client.sdk.discardpolicy=none|onconnectionloss|discard (Default is none) `deprecated since 5.0.0`
- njams.sdk.discardpolicy=none|onconnectionloss|discard (Default is none) `since 5.0.0`

This property decides what to do with a logmessage that couldn't be delivered (because of connection loss, full queue, etc.) 

- njams.client.sdk.maxqueuelength=YOUR-MAX-QUEUE-LENGTH(Default is 8) (Changed behaviour since SDK 4.1.0! Look there for njams.client.sdk.maxqueuelength and njams.client.sdk.maxsenderthreads)

This property's default is 8 sender threads as maximum threads that can be used. This means if there are more messages to handle than there are sender threads at the moment and the threshold hasn't exceeded, a new thread will be started. If the thread isn't in use for (look below njams.client.sdk.senderthreadidletime), the thread will be removed.

- njams.client.sdk.minqueuelength=YOUR-MINIMUM-QUEUELENGTH (Default is 1) `since 4.0.4` `removed in 4.1.0` (Use njams.client.sdk.minsenderthreads instead)

This property's default is 1 sender thread as core thread (that means it can't be closed even if its idle time has been exceeded) that can send project and log messages to the server.

- njams.client.sdk.senderthreadidletime=YOUR-SENDER-THREAD-IDLE-TIME (in ms) (Default is 10000) `since 4.0.4` `deprecated since 5.0.0`
- njams.sdk.senderthreadidletime=YOUR-SENDER-THREAD-IDLE-TIME (in ms) (Default is 10000) `since 5.0.0`

This property's default is 10000 (ms) that means that idle sender threads that haven't send any message in the last 10 seconds and are not core threads will be removed.


- njams.sdk.logAllErrors=true/false (Default is false) `since 4.0.12`

When this settings is false (default) nJAMS creates error events only for error situations that are not handled by the execution engine. If set to true, nJAMS will also create error events for errors that are handled.

- njams.sdk.datamasking.enabled=true/false (Default is true) `since 4.0.16`

When this setting is true (default) nJAMS enables dataMasking. When false, DataMasking is disabled for the regexes defined in the properties AND in the config.json.


- njams.sdk.datamasking.regex.YOUR-NAME-FOR-A-REGEX=YOUR-REGEX `since 4.0.16`

You can define multiple datamasking regex key-value pairs. Always use the prefix "njams.sdk.datamasking.regex." for the regexes that should be used for pattern matching to find data that you want to be masked.

E.g. "njams.sdk.datamasking.regex.maskAll = .*" would mask all data that is described in "How can I use Datamasking feature and which values are masked by that?", or "njams.sdk.datamasking.regex.maskPasswords = password: .*" would mask every occurrence of a string that looks like this: "password: <AnythingCanStandHere" and would result in a string that looks like this "*******************", if njams.sdk.datamasking.enabled=true.


- njams.client.sdk.deprecatedsubprocesspathfield=true/false (default is false) `since 4.1.0` `removed in 5.0.0`

This settings must to be set to true when the client sends data to an nJAMS server with version less than 5.1.0. It should be removed (or set to false) when server 5.1.0 or later is used. It is a compatibility setting for changed message format that is supported since nJAMS server 5.1.


- njams.sdk.subagent.enabled=true/false (default is true) `since 4.1.0`

Enables the Argos subagent component provided that the actual client implements that feature. If not implemented, this setting has no effect.


- njams.sdk.subagent.host=AGENT-HOST-NAME-OR-IP (default is localhost) `since 4.1.0`

Specify the host name or IP address of an nJAMS agent instance to that the Argos subagent in the SDK should send its collected metrics. This setting has no effect if the Argos subagent is disabled.


- njams.sdk.subagent.port=AGENT-PORT (default is 6450) `since 4.1.0`

Specify the port number of an nJAMS agent instance to that the Argos subagent in the SDK should send its collected metrics. This setting has no effect if the Argos subagent is disabled.


- njams.sdk.truncateActivitiesLimit=NUMBER-Of-ACTIVITIES `since 4.1.0`

When setting this option to a positive number, the nJAMS client will stop sending detailed activity- and transition information to the server once that number of activities has been reached. The client will however continue sending job status- and event information. In nJAMS server you will not see transitions between activities for such job instances.
This option goes along with the according setting in nJAMS server. But when using different settings here and in the server, the smaller value (greater than 0) will take effect.

Requires nJAMS server 5.1 or later to work properly.

This option is disabled by default.


- njams.sdk.truncateOnSuccess=true/false (default is false) `since 4.1.0`

When this setting is enabled (true), the nJAMS client will not send detailed activity- and transition information for job instances that completed successfully. The client will however send job status- and event information. Job instances completing with any other status than success are not affected by this setting. In nJAMS server you will not see transitions between activities for such job instances. This setting has only limited effect on job instances that send more than a single message because it only affects the last message being sent.

This option goes along with the according setting in nJAMS server (compress successful transactions). It will be effective if it is either set here in the client, or in the server.

Requires nJAMS server 5.1 or later to work properly.

This option is disabled by default (false).

- njams.client.sdk.minsenderthreads=YOUR-MINIMUM-SENDERTHREADS (Default is 1) `since 4.1.0` `deprecated since 5.0.0`
- njams.sdk.minsenderthreads=YOUR-MINIMUM-SENDERTHREADS (Default is 1) `since 5.0.0`

This property's default is 1 sender thread as core thread (that means it can't be closed even if its idle time has been exceeded) that can send project and log messages to the server.


- njams.client.sdk.maxsenderthreads=YOUR-MAXIMUM-SENDERTHREADS (Default is 8) `since 4.1.0` `deprecated since 5.0.0`
- njams.sdk.maxsenderthreads=YOUR-MAXIMUM-SENDERTHREADS (Default is 8) `since 5.0.0`

This property's default is 8 sender threads as maximum threads that can be used. This means if there are more messages to handle than there are current sender threads and there are less than YOUR-MAXIMUM-SENDERTHREADS, a new thread will be started. If the thread isn't in use for (look at njams.client.sdk.senderthreadidletime), the thread will be removed.


- njams.client.sdk.maxqueuelength=YOUR-MAX-QUEUE-LENGTH (Default is 8) `since 4.1.0` `deprecated since 5.0.0`
- njams.sdk.maxqueuelength=YOUR-MAX-QUEUE-LENGTH (Default is 8) `since 5.0.0`

This property's default is 8 messages that will be stored in the internal queue before processing will be blocked or messages will be discarded, regarding to the set discard policy.


- njams.client.sdk.sharedcommunications=true/false (default is false) `since 4.1.3` `deprecated since 5.0.0`
- njams.sdk.communication.shared=true/false (default is false) `since 5.0.0`

Before release 4.1.3 each `Njams` instance in the SDK had its own dedicated connection pool (senders and receivers) for communicating with the server. Since 4.1.3 this settings controls whether `Njams` instances continue to have their own pool (default, i.e., false), or if a single, global pool shall be shared across all `Njams` instances (true). 

Depending on this setting, the above mentioned settings for min/max sender-threads and queue length either apply to each dedicated sender pool, or the single shared sender pool when using shared communications. This setting therefore has significant effect on the number of connections created.

All `Sender` implementations implicitly support shared connections, but `Receiver`s must explicitly implement sharing support. Currently both `JMS`and `CLOUD` implementations support shared receivers. If shared communications is selected but the receiver does not support sharing, senders are shared but for each `Njams` instance a dedicated receiver instance is created though.

This setting has no meaning for client implementations that create just a single `Njams` instance.

This option is disabled (false) by default, i.e., a dedicated communication pool is created for each `Njams` instance.


- njams.client.sdk.disable.secure.processing=true/false (default is false) `since 4.2.0` `deprecated since 5.0.0`
- njams.sdk.disable.secure.processing=true/false (default is false) `since 5.0.0`

This disables the request for XML lib to do only secure processing. Setting this to true could be helpful if there is an old XML Lib in the project, which does not support the secure processing feature and throws an error when requesting to do so.


- njams.sdk.communication.jms.delivery.mode=PERSISTENT/NON_PERSISTENT (default is PERSISTENT) `since 4.2.0`

Setting the mode to NON_PERSISTENT does not use persistence in JMS provider. 
**Attention:** This can lead to data loss on JMS provider and create inconstistent data in nJAMS when for example a process is sent in 2 parts and one part is lost because of the non persistent flag. Use this feature only when you can accept this.


- njams.client.sdk.disable.startdata=true/false (default is false) `since 4.2.3` `deprecated since 5.0.0`
- njams.sdk.disable.startdata=true/false (default is false) `since 5.0.0`

If set to true, this option globally disables collecting job start-data. Note that collecting start-data is required when using nJAMS replay functionality.
This option is deprecated since it cancels controlling start-data recording in nJAMS server 6 and later.
Find option `njams.sdk.bootstrap.recording` as replacement.


- njams.sdk.bootstrap.recording=true/false (default is true) `since 5.0.0`

This is a one-time setting that is only used when bootstrapping a new nJAMS client instance. It is used for setting the initial default value for client- (engine-) wide recording of start-data. Once a configuration for a client has been created, this setting has no more effect.
Set this option to false to initially disable recording of start-data.

- njams.sdk.communication.containerMode=true/false (default is true) `since 5.0.0`

Since version 5.0 nJAMS SDK supports container setups where multiple identical nodes (with nJAMS clients) run in a load-balancing setup. In such environments some client commands like for example replaying a process instance must only be executed by a single node. Such commands require a previous handshake before the actual command can executed by a client, which induces some overhead. However, this overhead only applies to certain commands which are usually not very frequent. With this setting, you can disable the container mode (by setting to false), provided that the client runs in a common single-node scenario.


- njams.sdk.payload.limit.mode=truncate|discard|none (default is none) `since 5.0.0`
- njams.sdk.payload.limit.size=<max. size in chars>

These two parameters are used for limiting payload size for fields that allow large data like the trace-input/output or event-payload fields. If set
to `truncate`, payloads exceeding the limit are truncated at the specified limit size. If set to `discard`, payloads are discarded completely when 
exceeding the limit.  Any other value, in particular `none` disables limiting payloads, which is also the default behavior.
When using mode truncate or discard, the size parameter must also be set for specifying the maximum payload size limit in characters.


# How can I use a custom ProcessModelLayouter? #
If you don't want to use the default ProcessModelLayouter (SimpleProcessModelLayouter), you can do this:

Use the NoopLayouter that does nothing. You can set the x and y coordinates and the width and height of every ActivityModel by your own. Create a NoopLayouter object ::= noopLayouter and call "setProcessModelLayouter(noopLayouter)" on your Njams object. 

`Since njams4-sdk-4.0.3`

Write your own layouter by implementing the interface com.im.njams.sdk.model.layout.ProcessModelLayouter. The method "layout(ProcessModel processModel)" needs to be overriden. Create a YourLayouter object ::= yourLayouter and call "setProcessModelLayouter(yourLayouter)" on your Njams object. 

`Since njams4-sdk-4.0.0`

# How can I use password encoding for configuration files? #
1. Call ``` java -jar njams-sdk-4.x.x.jar YOUR_NOT_ENCODED_VALUE ```
2. Something like 
    ``` This is the encoded argument: 
        '??0190029006d0048004c0055000c0017002d00090005006a004d007100b500bd' (without '') for 'foo'. 
    ```   
    will appear if the encoding was successful, otherwise  
    ``` 
        The argument: 'foo' could not be encoded correctly. 
    ```  
    will appear.
***
**Attention**
Don't prepend '??' on the value you want to encode! This is used to determine which property is encoded. 
***
3. Go to the config file and replace the corresponding value with the encoded value. The SDK will decode it when needed.

`Since njams4-sdk-4.0.1`

# Does the ClientPath need to be unique? #
Yes, because multiple processes in the SDK (and the Server) rely on the uniqueness of the ClientPath for each individual Njams instance.

# How can I use Datamasking feature and which values are masked by that? #

Datamasking is included in SDK and is configured via njams.properties (or other settings) but can still be used like in njams4-sdk-4.0.0. 
The regexes defined in the njams.properties will have a higher priority that the ones defined in the configuration.json , they will match later than the ones in njams.properties. 
To define them, look at "Which settings can I set? -> For optional parameters

`Since njams4-sdk-4.0.16`

1. Datamasking is included in SDK and is configured via config file.
2. Stop the client where you want to configure datamasking.
3. Open the json file where the client stores its dynamic configuration (eg. Tracepoints). By default the file's name is "configuration.json". You can change the default name and destination of the configuration.json by handing over a property to the client with key: "njams.sdk.configuration.file.file" and value: `YOURPATH/YOURFILENAME.json`.
4. Add the maskings that you want to use as regex (regex as defined in java.util.regex.Pattern (SE 8)) in the "dataMasking" tag as a String array:  
``` "dataMasking" : [ "<requesturi>(\\p{Alpha}|/|\\p{Digit})*</requesturi>", "<requesturl>(\\p{Alpha}|/|\\p{Digit}|:|-)*</requesturl>" ]```

***
**Attention**
If the client hasn't been started yet or there were no dynamic changes from the server side like switching on tracing, there is no configuration file yet. You can create one with the following content:
```
    {  
      "logMode" : "COMPLETE",  
      "processes" : { },  
      "dataMasking" : [ ],  
      "recording" : true  
    }   
```
***

## Which values are masked? ##

The Datamasking works for the following values:
* TraceInput
* TraceOutput

`Since njams4-sdk-4.0.0`

The Datamasking works for the following values:
* TraceInput
* TraceOutput
* EventMessage
* EventCode
* EventPayload
* EventStackTrace
* StartData
* CorrelationLogId
* ExternalLogId
* BusinessObject
* ParentLogId
* BusinessService
* All other attributes

`Since njams4-sdk-4.0.4`

# Howto use Argos Feature to send metrics from SDK

After Njams.start() the new ArgosSender is active and will send every 10 seconds all metrics to a nJAMS Agent. Configuration is done via Settings File with the following properties:
* njams.sdk.subagent.host
* njams.sdk.subagent.port
* njams.sdk.subagent.enabled

Per default the ArgosSender is enabled, but has no collectors enabled. The SDK ships with a JVMCollector, which an SDK based client must instantiate and add using this Java code example:

```
// add JVMCollector
JVMCollector jvmCollector = new JVMCollector("testId", "testName", "testType");
njams.addArgosCollector(jvmCollector);
```

The implementation is located in package com.im.njams.sdk.argos 

To create your own metrics you have to implement two classes. Your metric class which extends from ArgosMetric and contains your attributes you want to send in a POJO style and Collector class which extends from ArgosCollector and implement the create method which cares about creation of your Metric. 

Your collector has to be registered at the ArgosSender via the addArgosCollector method.

See the build in JVM metric as an example.

This feature is available `since njams4-sdk-4.11.0`

# How to fill Input Mapping field

ActivityModel has an attribute named: "mapping". This will be displayed in nJAMS UI if you click on an Activty.
The attribute is a String and can contain three different types of Strings:

1. A special JSON structure which will open a tree viewer in UI
2. Custom JSON which will be displayed in JSON viewer
3. A plain string which will be shown in a normal text editor.

The JSON structure for the tree has to look like this:

```
{
  "name": "stylesheet",
  "entries": [
    {
      "name": "version",
      "value": "2.0"
    },
    {
      "name": "param",
      "entries": [
        {
          "name": "name",
          "value": "Start"
        }
      ]
    },
    {
      "name": "template",
      "entries": [
        {
          "name": "match",
          "value": "/"
        },
        {
          "name": "name",
          "value": "Log-input"
        },
        {
          "name": "ActivityInput",
          "entries": [
            {
              "name": "message",
              "entries": [
                {
                  "name": "value-of",
                  "entries": [
                    {
                      "name": "select",
                      "value": "concat(\"Other: \", $Start/tns2:movieName, \" Genre: \", $Start/tns2:genre, \" Year: \", string($Start/tns2:year))"
                    }
                  ]
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```
It must contain an entries array and names and values. The entries can be nested and will create the typical tree structure.

# How to fill configuration field 

ActivityModel has an attribute named: "config". This will be displayed in nJAMS UI if you click on an Activity in lower TAB named Config. 
It is a String attribute and should contain JSON or plain text.

If it contains JSON the UI will show a tree structure of that JSON. Additionally if there are properties which are surrounded 
by double % like: %%MyGlobalVariable%% you can click on resolve global variables button in UI and the placeholder will be 
replaced. The available global variables are located as a String to String Map in the Njams Class via getGlobalVariables.

Sample JSON:
```
{
  "PermittedMessageType ": {
    "PermittedMessageType ": "XML Text "
  },
  "SessionAttributes ": {
    "SessionAttributes ": {
      "transacted ": {
        "transacted ": "false "
      },
      "acknowledgeMode ": {
        "acknowledgeMode ": "1 "
      },
      "maxSessions ": {
        "maxSessions ": "1 "
      },
      "destination ": {
        "destination ": "%%Connections/OrderService_C1/Queue_OrderEntry_C1%% "
      }
    }
  },
  "ConfigurableHeaders ": {
    "ConfigurableHeaders ": {
      "JMSDeliveryMode ": {
        "JMSDeliveryMode ": "PERSISTENT "
      },
      "JMSExpiration ": {
        "JMSExpiration ": "0 "
      },
      "JMSPriority ": {
        "JMSPriority ": "4 "
      }
    }
  },
  "ConnectionReference ": {
    "ConnectionReference ": "/Connections/JMS Connection.sharedjmscon "
  },
  "OutDataxsdString ": {
    "OutDataxsdString ": {
      "ref ": {
        "ref ": "pfx3:order "
      }
    }
  }
}
```

