# How can I provide startup settings to njams?

To set which settings provider to use, create a `Properties` object with one property:

```
njams.sdk.settings.provider=NAME_OF_THE_SETTINGS_PROVIDER
```

Then call `SettingsProviderFactory.getSettingsProvider(props)` to obtain a provider object.

> **Deprecated (for removal in 6.0.0):** The settings provider/factory mechanism — `SettingsProviderFactory`, the `SettingsProvider` interface and its implementations, and the `njams.sdk.settings.*` provider properties listed below — is deprecated. Obtain a `ClientSettings` directly via its factory methods instead, e.g. `ClientSettings.from(Map)`, `ClientSettings.from(Properties)`, or `ClientSettings.fromSystemProperties(Predicate)`.

# Which settings providers are available?

There are four built-in settings providers. You can also implement your own by using the `SettingsProvider` interface.

## FileSettingsProvider

**Provider name:** `file`

Reads a JSON file. Configure the file path either by setting `njams.sdk.settings.file` or by calling `configure(props)` with that property. If neither is provided, the file defaults to `config.json` in the working directory.

## MemorySettingsProvider

**Provider name:** `memory`

Holds properties in memory only. Settings are passed via `configure(props)` and returned by `loadSettings()`.

> **Note:** A client restart will lose all settings stored by this provider.

## PropertiesFileSettingsProvider

**Provider name:** `propertiesFile`

Loads and saves settings in standard Java Properties format. Supports recursive loading of parent property files via a parent key. Configure the file path with `njams.sdk.settings.properties.file`, or call `setFile(File)`, or let it default to `config.properties`. When `loadSettings()` is called, the file and all its parent files are read; child properties override ancestor properties in the resulting `Settings` object (the files themselves are not modified).

## SystemPropertiesSettingsProvider

**Provider name:** `systemProperties`

Uses `System.getProperties()` as the settings source. `configure(props)` has no effect.

# Which settings can I use?

## Settings Providers

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.sdk.settings.provider` | — | Selects the settings provider implementation. Values: `file`, `propertiesFile`, `memory`, `systemProperties` | `deprecated` |
| `njams.sdk.settings.file` | `config.json` | Path to the JSON settings file used by the `file` provider. | `deprecated` |
| `njams.sdk.settings.properties.file` | `config.properties` | Path to the properties file used by the `propertiesFile` provider. | `deprecated` |
| `njams.sdk.settings.properties.parent` | — | Path to the parent properties file for the `propertiesFile` provider. Properties in the child file override those in the parent. | `deprecated` |
| `njams.sdk.settings.properties.parentKey` | `njams.sdk.settings.properties.parent` | Overrides the key used to look up the parent file path. When set to `MY_KEY`, the provider reads the parent path from the property `MY_KEY` instead of `njams.sdk.settings.properties.parent`. | `deprecated` |

## Configuration

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.sdk.configuration.provider` | `file` | Selects the configuration provider used for persisting runtime settings such as tracepoints and data-masking rules. Values: `file`, `memory` | |

## General SDK Settings

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.client.sdk.discardpolicy` | `none` | Replaced by `njams.sdk.discardpolicy`. | <kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.client.sdk.disable.secure.processing` | `false` | Replaced by `njams.sdk.disable.secure.processing`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.0</kbd><br><kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.client.sdk.disable.startdata` | `false` | Replaced by `njams.sdk.disable.startdata`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.3</kbd><br><kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.client.sdk.flush_interval` | `30` | Replaced by `njams.sdk.flush_interval`. | <kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.client.sdk.flushsize` | `5242880` | Replaced by `njams.sdk.flushsize`. | <kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.client.sdk.maxqueuelength` | `8` | Replaced by `njams.sdk.maxqueuelength`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.0</kbd><br><kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.client.sdk.maxsenderthreads` | `8` | Replaced by `njams.sdk.maxsenderthreads`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.0</kbd><br><kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.client.sdk.minsenderthreads` | `1` | Replaced by `njams.sdk.minsenderthreads`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.0</kbd><br><kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.client.sdk.senderthreadidletime` | `10000` | Replaced by `njams.sdk.senderthreadidletime`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.0.4</kbd><br><kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.sdk.bootstrap.recording` | `true` | Initial default for client-wide start-data recording. Applied only once when a fresh client configuration is created. Set to `false` to start with recording disabled. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.disable.secure.processing` | `false` | When `true`, disables the XML secure-processing feature (`XMLConstants.FEATURE_SECURE_PROCESSING`). Useful when the environment contains an old XML library that does not support this feature. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.disable.startdata` | `false` | When `true`, globally disables collecting job start-data. Also disables replay functionality. Use `njams.sdk.bootstrap.recording` instead. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd><br><kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.sdk.discardpolicy` | `none` | What to do with a log message that cannot be delivered (connection loss, full queue, etc.). Values: `none`, `onconnectionloss`, `discard` | |
| `njams.sdk.flush_interval` | `30` | Flush interval in seconds. The current log message is flushed if no message has been sent within this interval. | |
| `njams.sdk.flushsize` | `5242880` | Maximum log message body size in bytes. The message is flushed when this threshold is exceeded. | |
| `njams.sdk.logAllErrors` | `false` | When `true`, error events are created for both unhandled and handled errors. By default, error events are only created for errors not handled by the execution engine. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.0.12</kbd> |
| `njams.sdk.logLevel.default` | `INFO` | Default log level applied to processes until a process-specific configuration overrides it. Values: `INFO`, `SUCCESS`, `WARNING`, `ERROR` | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.logMode.default` | `COMPLETE` | Default log mode applied to the client until a configuration-specific value is set. Values: `COMPLETE`, `EXCLUSIVE`, `NONE` | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.maxqueuelength` | `8` | Maximum number of messages held in the internal send queue before processing blocks or messages are discarded (according to `njams.sdk.discardpolicy`). | |
| `njams.sdk.maxsenderthreads` | `8` | Maximum number of sender threads. New threads are started when the queue has pending messages and the current thread count is below this limit. | |
| `njams.sdk.minsenderthreads` | `1` | Minimum (core) number of sender threads. Core threads are not removed even when idle. | |
| `njams.sdk.payload.limit.mode` | `none` | Limits the size of large payload fields (trace input/output, event payload, event stack trace, attribute values). Values: `truncate` (cut at limit), `discard` (remove entirely), `none` (no limit). Requires `njams.sdk.payload.limit.size` when set to `truncate` or `discard`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.payload.limit.size` | — | Maximum payload size in characters. Required when `njams.sdk.payload.limit.mode` is `truncate` or `discard`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.senderthreadidletime` | `10000` | Time in milliseconds after which an idle non-core sender thread is removed. | |
| `njams.sdk.truncateActivitiesLimit` | `0` | When set to a positive number, the client stops sending detailed activity and transition data once this many activities have been recorded in a job. Job status and event data continue to be sent. The smaller of client and server settings takes effect. Requires nJAMS server 5.1 or later. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.0</kbd> |
| `njams.sdk.truncateOnSuccess` | `false` | When `true`, detailed activity and transition data is omitted for jobs that complete successfully. Job status and event data are still sent. Only affects the last message of a multi-message job. Requires nJAMS server 5.1 or later. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.0</kbd> |
| `njams.client.sdk.deprecatedsubprocesspathfield` | `false` | Must be `true` when sending data to nJAMS server older than 5.1.0. Remove or set to `false` when using server 5.1.0 or later. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.0</kbd><br><kbd style="background-color:#24292f;color:#fff;border-color:#24292f">removed 5.0.0</kbd> |
| `njams.client.sdk.minqueuelength` | `1` | Replaced by `njams.client.sdk.minsenderthreads`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.0.4</kbd><br><kbd style="background-color:#24292f;color:#fff;border-color:#24292f">removed 4.1.0</kbd> |

## Communication

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.client.sdk.sharedcommunications` | `false` | Replaced by `njams.sdk.communication.shared`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.3</kbd><br><kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.sdk.communication` | — | Selects the communication transport. Values: `HTTP`, `JMS`, `KAFKA`, or a custom implementation name. | |
| `njams.sdk.communication.containerMode` | `true` | Enables container/cluster mode. When `true`, the SDK generates a unique client ID per instance so that targeted commands (e.g. replay) are routed to the correct node in a load-balanced setup. Disable only in confirmed single-node deployments. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.maxMessageSize` | `0` | Maximum message body size in bytes. Messages exceeding this size are split into chunks before sending. A value of `0` or less disables splitting. The minimum allowed value is 10240 bytes. For Kafka, the smaller of this value and the Kafka producer's `max.request.size` is used. Requires nJAMS server 6.1.2 or later for transports other than Kafka. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.3</kbd> |
| `njams.sdk.communication.shared` | `false` | When `true`, a single sender/receiver pool is shared across all `Njams` instances in the same JVM. By default each instance has its own dedicated pool. Has no effect when only one `Njams` instance is used. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |

## HTTP / HTTPS

Set `njams.sdk.communication=HTTP`. HTTPS is used automatically when the base URL uses the `https` scheme; the separate `HTTPS` communication value is deprecated since 5.0.0.

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.sdk.communication.http.base.url` | — | Base URL of the nJAMS server (e.g. `http://localhost:8080/njams/`). | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.0</kbd> |
| `njams.sdk.communication.http.compression.enabled` | `false` | When `true`, HTTP request bodies are compressed with GZIP. Requires nJAMS server support. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.3</kbd> |
| `njams.sdk.communication.http.connection.test` | — | Set to `legacy` to skip the initial connection test and use the legacy fallback immediately. Only needed for nJAMS server versions older than 5.3.4. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd><br><kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.sdk.communication.http.dataprovider.suffix` | — | Path suffix of the nJAMS dataprovider to send events to (e.g. `dp1`). | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.password` | — | Password for nJAMS server authentication. The account must have the `ingest-service` system privilege. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.proxy.host` | — | Proxy host name or IP address. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.proxy.password` | — | Password for proxy authentication. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.proxy.port` | `80` | Proxy port. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.proxy.user` | — | User name for proxy authentication. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.ssl.certificate.file` | — | Full path to a PEM certificate file for SSL. | |
| `njams.sdk.communication.http.ssl.keyStore` | — | Full path to a keystore file for SSL. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.ssl.keyStorePassword` | — | Password for the keystore. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.ssl.keyStoreType` | `jks` | Keystore format. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.ssl.trustStore` | — | Full path to a truststore file for SSL certificate verification. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.ssl.trustStorePassword` | — | Password for the truststore. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.ssl.trustStoreType` | `jks` | Truststore format. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.ssl.unsafe.disableHostnameVerification` | `false` | When `true`, host name verification is skipped during SSL handshake. **Reduces security — use only in controlled environments.** | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.ssl.unsafe.trustAllCertificates` | `false` | When `true`, all SSL certificates are accepted without validation. **Reduces security — use only in controlled environments.** | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.user` | — | User name for nJAMS server authentication. The account must have the `ingest-service` system privilege. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.http.dataprovider.prefix` | — | Replaced by `njams.sdk.communication.http.dataprovider.suffix`. | <kbd style="background-color:#24292f;color:#fff;border-color:#24292f">removed 5.0.0</kbd> |

## JMS

Set `njams.sdk.communication=JMS`.

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.sdk.communication.jms.connectionFactory` | — | Name used to look up the JMS `ConnectionFactory`. As of 5.0.3, also used for a two-step `JmsFactory` SPI lookup before falling back to JNDI. | |
| `njams.sdk.communication.jms.delivery.mode` | `PERSISTENT` | JMS delivery mode for outbound messages. Values: `PERSISTENT`, `NON_PERSISTENT` / `NONPERSISTENT`, `RELIABLE` (Tibco EMS only, since 5.0.3). **Note:** Non-persistent mode can lead to message loss and inconsistent data in nJAMS. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.0</kbd> |
| `njams.sdk.communication.jms.destination` | — | Common prefix for the event, command, and error queues / topics (e.g. `njams`). | |
| `njams.sdk.communication.jms.destination.commands` | — | Overrides the commands topic name when it does not follow the default convention (`<destination>.commands`). | |
| `njams.sdk.communication.jms.java.naming.factory.initial` | — | JNDI initial context factory class (e.g. `com.tibco.tibjms.naming.TibjmsInitialContextFactory`). | |
| `njams.sdk.communication.jms.java.naming.provider.url` | — | JNDI provider URL (e.g. `tibjmsnaming://localhost:7222`). | |
| `njams.sdk.communication.jms.java.naming.security.credentials` | — | JNDI password. | |
| `njams.sdk.communication.jms.java.naming.security.principal` | — | JNDI user name. | |
| `njams.sdk.communication.jms.javax.net.ssl.keyStore` | — | Path to the JMS SSL keystore. | |
| `njams.sdk.communication.jms.javax.net.ssl.keyStorePassword` | — | Password for the JMS SSL keystore. | |
| `njams.sdk.communication.jms.javax.net.ssl.keyStoreType` | — | Type of the JMS SSL keystore (e.g. `jks`). | |
| `njams.sdk.communication.jms.javax.net.ssl.trustStore` | — | Path to the JMS SSL truststore. | |
| `njams.sdk.communication.jms.javax.net.ssl.trustStorePassword` | — | Password for the JMS SSL truststore. | |
| `njams.sdk.communication.jms.javax.net.ssl.trustStoreType` | — | Type of the JMS SSL truststore (e.g. `jks`). | |
| `njams.sdk.communication.jms.jmsFactory` | — | Explicitly selects a `JmsFactory` SPI implementation by name. Only needed when the desired `JmsFactory` also uses the `connectionFactory` key internally (e.g. a custom JNDI variant), making disambiguation necessary. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.3</kbd> |
| `njams.sdk.communication.jms.password` | — | JMS connection password. | |
| `njams.sdk.communication.jms.supportsMessageSelector` | `true` | When `false`, the JMS implementation does not support message selectors. The SDK then uses a workaround that requires an additional queue with the `.project` suffix. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.3</kbd> |
| `njams.sdk.communication.jms.username` | — | JMS connection user name. | |

## Kafka

Set `njams.sdk.communication=KAFKA`.

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.sdk.communication.kafka.admin.*` | — | Properties passed directly to the Kafka admin client. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.0</kbd> |
| `njams.sdk.communication.kafka.client.*` | — | Properties passed to all Kafka client types (consumer, producer, and admin). Acts as a shortcut for setting a property on all three at once. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.0</kbd> |
| `njams.sdk.communication.kafka.client.bootstrap.servers` | — | **Required.** Comma-separated list of Kafka bootstrap servers (e.g. `host1:9092,host2:9092`). | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.0</kbd> |
| `njams.sdk.communication.kafka.consumer.*` | — | Properties passed directly to the Kafka consumer client. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.0</kbd> |
| `njams.sdk.communication.kafka.producer.*` | — | Properties passed directly to the Kafka producer client. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.0</kbd> |
| `njams.sdk.communication.kafka.replyProducerIdleTime` | `30000` | Time in milliseconds after which an idle reply producer is closed. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.0</kbd> |
| `njams.sdk.communication.kafka.topicPrefix` | `njams` | Prefix for the event, project, commands, and optional error topics. The topics must exist in the Kafka cluster. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.0</kbd> |
| `njams.sdk.communication.kafka.commandsTopic` | — | Overrode the commands topic name. Replaced by the standard topic naming convention based on `topicPrefix`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.0</kbd><br><kbd style="background-color:#24292f;color:#fff;border-color:#24292f">removed 5.0.0</kbd> |
| `njams.sdk.communication.kafka.largeMessageMode` | — | Controlled how messages larger than the Kafka limit were handled (`split` or `discard`). Splitting is now the standard behavior; `discard` was a compatibility option for nJAMS server 5.2.0 and 5.2.1. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.2.2</kbd><br><kbd style="background-color:#24292f;color:#fff;border-color:#24292f">removed 5.0.0</kbd> |

## Argos / Metrics

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.sdk.subagent.enabled` | `true` | Enables the Argos subagent component. Has no effect if the client does not implement the Argos feature. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.0</kbd> |
| `njams.sdk.subagent.host` | `localhost` | Host name or IP address of the nJAMS Agent instance to send Argos metrics to. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.0</kbd> |
| `njams.sdk.subagent.port` | `6450` | Port of the nJAMS Agent instance. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.0</kbd> |

## Data Masking

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.sdk.datamasking.enabled` | `true` | Enables data masking. When `false`, masking is disabled for all regex rules defined in both settings and `configuration.json`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.0.16</kbd> |
| `njams.sdk.datamasking.regex.<name>` | — | Defines a data-masking regex rule. `<name>` is an arbitrary label; the value is a Java regex pattern (`java.util.regex.Pattern`). Multiple rules can be defined with different names. Example: `njams.sdk.datamasking.regex.maskPasswords=password:\s*\S+` | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.0.16</kbd> |

# How can I use a custom ProcessModelLayouter?

If you don't want to use the default `ProcessModelLayouter` (`SimpleProcessModelLayouter`), you have two options:

**Use the built-in NoopLayouter** (`since njams4-sdk-4.0.3`): The `NoopLayouter` does nothing — you set the x/y coordinates and width/height of every `ActivityModel` yourself. Create a `NoopLayouter` instance and call `setProcessModelLayouter(noopLayouter)` on your `Njams` object.

**Implement your own layouter** (`since njams4-sdk-4.0.0`): Implement the interface `com.im.njams.sdk.model.layout.ProcessModelLayouter`. Override the `layout(ProcessModel processModel)` method. Create an instance of your layouter and call `setProcessModelLayouter(yourLayouter)` on your `Njams` object.

# How can I use password encoding for configuration files?

1. Run: `java -jar njams-sdk-4.x.x.jar YOUR_NOT_ENCODED_VALUE`
2. The output will show something like:
   ```
   This is the encoded argument:
       '??0190029006d0048004c0055000c0017002d00090005006a004d007100b500bd' (without '') for 'foo'.
   ```
   If encoding fails, you will see `The argument: 'foo' could not be encoded correctly.`

   > **Note:** Do not prepend `??` to the value you want to encode — the `??` prefix is the marker the SDK uses to detect encoded values.

3. Replace the corresponding value in your configuration file with the encoded value. The SDK decodes it automatically when needed.

`since njams4-sdk-4.0.1`

# Does the ClientPath need to be unique?

Yes. Multiple processes in the SDK and the server rely on the uniqueness of the ClientPath for each individual `Njams` instance.

# How can I use Datamasking feature and which values are masked by that?

Data masking is configured via settings properties (see [Data Masking](#data-masking) above) and/or via `configuration.json`. Regex rules defined in settings take precedence over rules defined in `configuration.json`.

`since njams4-sdk-4.0.16`

To configure data masking via `configuration.json`:

1. Stop the client.
2. Open the JSON file where the client stores its dynamic configuration (e.g. tracepoints). By default this file is named `configuration.json`. You can change the path and name by setting the property `njams.sdk.configuration.file.file` to `YOURPATH/YOURFILENAME.json`.
3. Add your masking patterns as Java regex strings (`java.util.regex.Pattern`) in the `dataMasking` array:
   ```json
   "dataMasking": [ "<requesturi>(\\p{Alpha}|/|\\p{Digit})*</requesturi>", "<requesturl>(\\p{Alpha}|/|\\p{Digit}|:|-)*</requesturl>" ]
   ```

> **Note:** If the client has not been started yet or no server-side configuration changes have been made, the configuration file may not exist. Create it manually with this content:
> ```json
> {
>   "logMode": "COMPLETE",
>   "processes": {},
>   "dataMasking": [],
>   "recording": true
> }
> ```

## Which values are masked?

`since njams4-sdk-4.0.0`:
* TraceInput
* TraceOutput

`since njams4-sdk-4.0.4`:
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

# Howto use Argos Feature to send metrics from SDK

After `Njams.start()` the Argos sender is active and sends collected metrics to an nJAMS Agent every 10 seconds. Configure the agent connection via the settings properties (see [Argos / Metrics](#argos--metrics) above).

By default the Argos sender is enabled but no collectors are active. The SDK ships with a `JVMCollector`. Register it in your client code:

```java
JVMCollector jvmCollector = new JVMCollector("testId", "testName", "testType");
njams.addArgosCollector(jvmCollector);
```

To create custom metrics, implement two classes:
- A metric class extending `ArgosMetric` that holds your metric attributes as a POJO.
- A collector class extending `ArgosCollector` that implements the `create()` method to produce your metric.

Register the collector via `njams.addArgosCollector(yourCollector)`. See the built-in `JVMCollector` as an example. The implementation is in package `com.im.njams.sdk.argos`.

`since njams4-sdk-4.11.0`

# How to fill Input Mapping field

`ActivityModel` has an attribute named `mapping`. It is displayed in nJAMS UI when you click on an activity.
The attribute is a String and can contain three different types of content:

1. A special JSON structure that opens a tree viewer in the UI
2. Custom JSON that is displayed in a JSON viewer
3. A plain string shown in a text editor

The JSON structure for the tree must contain an `entries` array with `name` and `value` fields. Entries can be nested:

```json
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

# How to fill configuration field

`ActivityModel` has an attribute named `config`. It is displayed in nJAMS UI when you click on an activity in the lower **Config** tab.

It is a String attribute and should contain JSON or plain text. If it contains JSON, the UI shows a tree structure. Additionally, properties surrounded by double percent signs like `%%MyGlobalVariable%%` can be resolved by clicking the **resolve global variables** button in the UI. The available global variables are stored as a `String`-to-`String` map in the `Njams` class via `getGlobalVariables()`.

Sample JSON:

```json
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
