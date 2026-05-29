# nJAMS SDK FAQs

## How to provide startup settings to nJAMS

nJAMS is configured at startup through a `ClientSettings` instance that you pass to the `Njams` constructor. `ClientSettings` is a key/value view over your configuration (transport selection, flush tuning, data masking, Argos, …); the available keys are listed in the tables further down.

### Obtaining a ClientSettings instance from a single source

`ClientSettings` provides static factory methods — pick the one matching where your configuration comes from:

| Factory method | Use when your configuration is… |
|---|---|
| `ClientSettings.from(Map<String, String>)` | held in an in-memory map |
| `ClientSettings.from(Properties)` | a `java.util.Properties` object (e.g. one you loaded from a file or resource) |
| `ClientSettings.fromSystemProperties(Predicate<String>)` | taken from JVM system properties; the predicate selects which keys are visible (pass `null` to accept all) |

All three return a live view: changes to the backing map/properties remain visible through the returned `ClientSettings`.

### Combining multiple sources (HierarchicalSettings)

When configuration comes from several places — a config file, system properties, environment variables, built-in defaults — use `HierarchicalSettings`. It is a `ClientSettings` that consults an ordered list of named layers and, for each key, returns the value from the **first** layer that defines it. Writes are applied only to the base (first) layer.

Build it with the fluent builder. List layers from highest priority to lowest, with defaults last so they only apply when no other layer provides the key:

```java
ClientSettings settings = HierarchicalSettings.from(fileProperties).withName("config.properties")
    .andThenSystemProperties().withPrefixFilter("njams.")
    .andThenEnvironmentVariables().withPrefixFilter("NJAMS_")
    .andThen(defaultProperties).withName("defaults")
    .build();
```

- Start the chain with `from(ClientSettings)`, `from(Map)`, `from(Properties)`, or `fromEmpty()` for an in-memory base. The base layer is the only one that receives writes.
- Add further layers with `andThen(...)` (a `ClientSettings`, `Map`, or `Properties`), `andThenSystemProperties()`, or `andThenEnvironmentVariables()`.
- System-property and environment layers can be narrowed with `withPrefixFilter(...)` and/or `withRegexFilter(...)` (combined with AND); filters match the actual storage key (the system-property or environment-variable name).
- Earlier layers win, so list your most specific overrides first and the defaults layer last as a fallback.

**Environment variables.** The environment layer resolves the SDK's dotted property keys from their upper-snake-case environment equivalents — e.g. reading `njams.sdk.communication` looks up the environment variable `NJAMS_SDK_COMMUNICATION` — so you keep using the same `njams.sdk.*` keys throughout your code regardless of source.

### Starting nJAMS with it

```java
// 1. Assemble the configuration as key/value pairs.
Properties props = new Properties();
props.setProperty(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
// … further transport and SDK settings (see the tables below) …

// 2. Wrap them in a ClientSettings instance.
ClientSettings settings = ClientSettings.from(props);

// 3. Pass the settings to the Njams constructor and start the client.
Njams njams = new Njams(new Path("Domain", "Deployment", "MyClient"), "1.0.0", "BW6", settings);
njams.start();
```

### Migrating from the deprecated provider/factory mechanism

> <kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 6.0.0</kbd> The settings *provider/factory* mechanism is deprecated and will be removed. This includes `SettingsProviderFactory`, the `SettingsProvider` interface and its built-in implementations (`file`, `propertiesFile`, `memory`, `systemProperties`), and the `njams.sdk.settings.*` provider properties (see the **Settings Providers** table below).

Previously you selected a provider via the `njams.sdk.settings.provider` property and called `SettingsProviderFactory.getSettingsProvider(props)`, then `loadSettings()`. Replace that with a `ClientSettings` obtained from the factory methods above:

| Deprecated provider | Replacement |
|---|---|
| `memory` | `ClientSettings.from(map)` or `ClientSettings.from(properties)` |
| `systemProperties` | `ClientSettings.fromSystemProperties(filter)`, or an `andThenSystemProperties()` layer in `HierarchicalSettings` |
| `file` / `propertiesFile` | load the file yourself (e.g. `Properties.load(...)`) and wrap it with `ClientSettings.from(properties)`; combine with other sources via `HierarchicalSettings` if needed |

The legacy `Settings` and `SettingsProvider` types remain only as deprecated aliases so existing code keeps compiling; new code should use `ClientSettings` directly.

## Which settings can I use

The tables below cover all settings recognized by the nJAMS SDK itself. Settings are passed to the SDK through a `ClientSettings` instance (see the section above) as plain key/value pairs. Each key is defined as a constant in `NjamsSettings`.

> **Note:** nJAMS client implementations built on top of this SDK may define additional settings of their own. Consult the documentation of the specific client for those.

### Settings Providers <kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 6.0.0</kbd>

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.sdk.settings.provider` | — | Selects the settings provider implementation. Values: `file`, `propertiesFile`, `memory`, `systemProperties` | <kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 6.0.0</kbd> |
| `njams.sdk.settings.file` | `config.json` | Path to the JSON settings file used by the `file` provider. | <kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 6.0.0</kbd> |
| `njams.sdk.settings.properties.file` | `config.properties` | Path to the properties file used by the `propertiesFile` provider. | <kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 6.0.0</kbd> |
| `njams.sdk.settings.properties.parent` | — | Path to the parent properties file for the `propertiesFile` provider. Properties in the child file override those in the parent. | <kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 6.0.0</kbd> |
| `njams.sdk.settings.properties.parentKey` | `njams.sdk.settings.properties.parent` | Overrides the key used to look up the parent file path. When set to `MY_KEY`, the provider reads the parent path from the property `MY_KEY` instead of `njams.sdk.settings.properties.parent`. | <kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 6.0.0</kbd> |

### Configuration

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.sdk.configuration.provider` | `file` | Selects the configuration provider used for persisting runtime settings such as tracepoints and data-masking rules. Values: `file`, `memory` | |

### General SDK Settings

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
| `njams.sdk.discardpolicy` | `discard` | What to do with a log message that cannot be delivered (connection loss, full queue, etc.). Values: `none`, `onconnectionloss`, `discard`. **⚠ Default changed from `none` to `discard` in 6.0.0.** Clients upgrading from earlier versions will now discard undeliverable messages instead of blocking; set to `none` explicitly to restore the previous blocking behavior. | <kbd style="background-color:#e36209;color:#fff;border-color:#e36209">changed 6.0.0</kbd> |
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

### Communication

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.client.sdk.sharedcommunications` | `false` | Replaced by `njams.sdk.communication.shared`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.3</kbd><br><kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 5.0.0</kbd> |
| `njams.sdk.communication` | — | Selects the communication transport. Values: `HTTP`, `JMS`, `KAFKA`, or a custom implementation name. | |
| `njams.sdk.communication.containerMode` | `true` | Enables container/cluster mode. When `true`, the SDK generates a unique client ID per instance so that targeted commands (e.g. replay) are routed to the correct node in a load-balanced setup. Disable only in confirmed single-node deployments. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |
| `njams.sdk.communication.maxMessageSize` | `0` | Maximum message body size in bytes. Messages exceeding this size are split into chunks before sending. A value of `0` or less disables splitting. The minimum allowed value is 10240 bytes. For Kafka, the smaller of this value and the Kafka producer's `max.request.size` is used. Requires nJAMS server 6.1.2 or later for transports other than Kafka. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.3</kbd> |
| `njams.sdk.communication.shared` | `false` | When `true`, a single sender/receiver pool is shared across all `Njams` instances in the same JVM. By default each instance has its own dedicated pool. Has no effect when only one `Njams` instance is used. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 5.0.0</kbd> |

### HTTP / HTTPS

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

### JMS

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

### Kafka

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

### Argos / Metrics

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.sdk.subagent.enabled` | `true` | Enables the Argos subagent component. Has no effect if the client does not implement the Argos feature. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.0</kbd> |
| `njams.sdk.subagent.host` | `localhost` | Host name or IP address of the nJAMS Agent instance to send Argos metrics to. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.0</kbd> |
| `njams.sdk.subagent.port` | `6450` | Port of the nJAMS Agent instance. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.1.0</kbd> |

### Data Masking

| Property | Default | Description | Tags |
|---|---|---|---|
| `njams.sdk.datamasking.enabled` | `true` | Enables data masking. When `false`, masking is disabled for all regex rules defined in both settings and `configuration.json`. | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.0.16</kbd> |
| `njams.sdk.datamasking.regex.<name>` | — | Defines a data-masking regex rule. `<name>` is an arbitrary label; the value is a Java regex pattern (`java.util.regex.Pattern`). Multiple rules can be defined with different names. Example: `njams.sdk.datamasking.regex.maskPasswords=password:\s*\S+` | <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.0.16</kbd> |

## How to modify the process model view (SVG)

nJAMS renders each process as an SVG diagram shown in the server UI. Producing that diagram is split into two independent steps:

1. **Layout** — a `ProcessModelLayouter` assigns x/y coordinates and dimensions to every `ActivityModel`.
2. **Rendering** — a `ProcessDiagramFactory` converts the laid-out `ProcessModel` into SVG markup.

Both can be customized independently on the `Njams` instance.

### Layout

The default layouter since 6.0 is `CommonModelLayouter`. It performs a two-pass algorithm — a bottom-up sizing pass that computes group dimensions from their contents, followed by a top-down placement pass using per-column widths — and correctly handles parallel branches, convergence nodes, and nested groups.

`SimpleProcessModelLayouter`, the previous default, is deprecated. It does not correctly handle parallel branches, 
multiple start activities, or groups with more than one start activity. If you relied on the default, no code change 
is needed — `CommonModelLayouter` is now used automatically. However, your model may look different due to the improved layout.

**Custom layouter** — the standard way to control layout is to implement `com.im.njams.sdk.model.layout.ProcessModelLayouter`. The interface has a single method:

```java
public class MyLayouter implements ProcessModelLayouter {
    @Override
    public void layout(ProcessModel processModel) {
        // set x, y, width, height on each ActivityModel and GroupModel
    }
}
```

Register it on the `Njams` instance before starting:

```java
njams.setProcessModelLayouter(new MyLayouter());
```

If the `ActivityModel` elements in your process model are already populated with coordinates and dimensions — for example, when coordinates are imported from an external source — use `NoopLayouter` instead to skip the layout step entirely:

```java
njams.setProcessModelLayouter(new NoopLayouter());
```

### Modifying the SVG output

The built-in `NjamsProcessDiagramFactory` generates SVG that meets the structural and formatting requirements of the nJAMS server. **Replacing it with a completely custom `ProcessDiagramFactory` implementation is not recommended** — the server relies on specific SVG conventions that `NjamsProcessDiagramFactory` encodes. Instead, extend or configure the existing factory.

**XSLT post-processing** — supply an XSLT stylesheet to be applied to the SVG DOM just before it is serialized to a string. Use this to restyle, reorder, or augment the generated SVG without touching the drawing logic:

```java
NjamsProcessDiagramFactory factory = new NjamsProcessDiagramFactory(njams)
    .withXslt(MyClass.class.getResourceAsStream("/my-svg-transform.xsl"));
njams.setProcessDiagramFactory(factory);
```

Three `withXslt()` overloads are available: `withXslt(Source)`, `withXslt(String)`, and `withXslt(InputStream)`.

**Extra elements hook** — subclass `NjamsProcessDiagramFactory` and override the empty `drawExtraElements(NjamsProcessDiagramContext)` method to append additional SVG elements after all activities, transitions, and groups have been drawn:

```java
public class MyDiagramFactory extends NjamsProcessDiagramFactory {
    public MyDiagramFactory(Njams njams) { super(njams); }

    @Override
    protected void drawExtraElements(NjamsProcessDiagramContext context) {
        // append custom nodes to context.getDoc()
    }
}
njams.setProcessDiagramFactory(new MyDiagramFactory(njams));
```

**Fine-grained drawing overrides** — `NjamsProcessDiagramFactory` also exposes `drawActivity()`, `drawGroup()`, and `drawTransition()` as `protected` methods. Override these in a subclass to alter how individual element types are rendered.

## How can I use password encoding for configuration files <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.0.1</kbd>

1. Run: `java -jar njams-sdk-4.x.x.jar YOUR_NOT_ENCODED_VALUE`
2. The output will show something like:
   ```
   This is the encoded argument:
       '??0190029006d0048004c0055000c0017002d00090005006a004d007100b500bd' (without '') for 'foo'.
   ```
   If encoding fails, you will see `The argument: 'foo' could not be encoded correctly.`

   > **Note:** Do not prepend `??` to the value you want to encode — the `??` prefix is the marker the SDK uses to detect encoded values.

3. Replace the corresponding value in your configuration file with the encoded value. The SDK decodes it automatically when needed.

## Does the client path need to be unique

Yes. The client path uniquely identifies a `Njams` instance in the nJAMS infrastructure. The server and the SDK's communication layer use it as an address to route commands back to the correct client. Every `Njams` instance must be initialized with a path that is unique across all connected clients.

## How to use data masking

Data masking applies Java regex patterns (`java.util.regex.Pattern`) to activity payload fields before they are sent to the nJAMS server. Any substring matching a rule is replaced with asterisks.

Masking rules can come from two sources:

- **Settings properties** — rules defined via `njams.sdk.datamasking.regex.<name>` (see [Data Masking](#data-masking) above) are static and active for the lifetime of the process.
- **Dynamic configuration** — rules stored in `configuration.json` can be updated at runtime through the nJAMS server UI without restarting the client.

Rules from settings take precedence over rules from the dynamic configuration.

### Configuring rules via settings properties

Add one property per rule. The name after the last `.` is a free-form label used to identify the rule:

```properties
njams.sdk.datamasking.regex.maskPasswords=password\s*=\s*\S+
njams.sdk.datamasking.regex.maskTokens=token\s*=\s*\S+
```

Any activity field value containing a substring like `password=secret123` will have that part replaced with asterisks.

### Configuring rules via configuration.json

1. Stop the client.
2. Open the JSON file used for dynamic configuration (tracepoints, log modes, etc.). By default this is `configuration.json`. The path can be changed via `njams.sdk.configuration.file.file`.
3. Add your masking patterns in the `dataMasking` array:
   ```json
   "dataMasking": [
     "<requesturi>(\\p{Alpha}|/|\\p{Digit})*</requesturi>",
     "<requesturl>(\\p{Alpha}|/|\\p{Digit}|:|-)*</requesturl>"
   ]
   ```

> **Note:** If the client has not been started yet or no server-side configuration changes have been made, the file may not exist. Create it manually with this minimum content:
> ```json
> {
>   "logMode": "COMPLETE",
>   "processes": {},
>   "dataMasking": [],
>   "recording": true
> }
> ```

### Which fields are masked

The following activity fields are subject to data masking:

- `TraceInput`, `TraceOutput`
- `EventMessage`, `EventCode`, `EventPayload`, `EventStackTrace`
- `StartData`
- `CorrelationLogId`, `ExternalLogId`, `ParentLogId`
- `BusinessObject`, `BusinessService`
- All other attributes

## How to use the Argos feature <kbd style="background-color:#cf222e;color:#fff;border-color:#cf222e">deprecated 6.0.0</kbd> <kbd style="background-color:#2da44e;color:#fff;border-color:#2da44e">since 4.11.0</kbd>

The Argos feature allows the SDK to send JVM and custom application metrics to an nJAMS Agent. After `Njams.start()`, the Argos sender starts automatically and transmits collected metrics every 10 seconds. Configure the agent connection via the settings properties (see [Argos / Metrics](#argos--metrics) above).

By default, the Argos sender is enabled but no collectors are registered. The SDK ships with a built-in `JVMCollector` for standard JVM metrics. Register it before or after starting the `Njams` instance:

```java
JVMCollector jvmCollector = new JVMCollector("myId", "JVM Metrics", "jvm");
njams.addArgosCollector(jvmCollector);
```

To send custom metrics, implement two classes:

- A metric class extending `ArgosMetric` — holds your metric values as a POJO.
- A collector class extending `ArgosCollector` — implements the `create()` method to produce a metric instance on each collection cycle.

Register your collector via `njams.addArgosCollector(yourCollector)`. Use the built-in `JVMCollector` in package `com.im.njams.sdk.argos` as a reference implementation.

## How to set the activity mapping

`ActivityModel` has a `mapping` attribute that is displayed in the nJAMS UI when you click on an activity. The content can take three forms; the UI selects the appropriate viewer automatically:

| Content | UI viewer |
|---|---|
| JSON object with `name` and `entries` at the root | Interactive tree viewer |
| Any other valid JSON | JSON viewer |
| Plain text | Text editor |

### Tree viewer format

To trigger the tree viewer, the root object must have a `name` (the root node label) and an `entries` array. Each entry is either a **leaf** — carrying a `name` and a `value` — or a **branch** — carrying a `name` and its own nested `entries` array:

```json
{
  "name": "root",
  "entries": [
    { "name": "leafNode", "value": "someValue" },
    {
      "name": "branchNode",
      "entries": [
        { "name": "child", "value": "childValue" }
      ]
    }
  ]
}
```

The following example maps the structure of an XSLT stylesheet, where the `entries` hierarchy mirrors the XML element nesting:

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

## How to set the activity configuration

`ActivityModel` has a `config` attribute displayed in the nJAMS UI when you click on an activity, in the lower **Config** tab.

The attribute accepts either plain text or JSON. JSON content is rendered as an interactive tree in the UI; plain text is shown in a text editor.

### Global variable references

Values can contain global variable references in the form `%%VariableName%%`. When a user clicks **Resolve global variables** in the UI, these placeholders are replaced with the corresponding values. Global variables are defined as a `String`-to-`String` map on the `Njams` instance and accessed via `njams.getGlobalVariables()`.

### Example

The following example shows a typical JMS activity configuration with session attributes, configurable headers, and a global variable reference for the destination queue:

```json
{
  "PermittedMessageType": {
    "PermittedMessageType": "XML Text"
  },
  "SessionAttributes": {
    "SessionAttributes": {
      "transacted": { "transacted": "false" },
      "acknowledgeMode": { "acknowledgeMode": "1" },
      "maxSessions": { "maxSessions": "1" },
      "destination": { "destination": "%%Connections/OrderService_C1/Queue_OrderEntry_C1%%" }
    }
  },
  "ConfigurableHeaders": {
    "ConfigurableHeaders": {
      "JMSDeliveryMode": { "JMSDeliveryMode": "PERSISTENT" },
      "JMSExpiration": { "JMSExpiration": "0" },
      "JMSPriority": { "JMSPriority": "4" }
    }
  },
  "ConnectionReference": { "ConnectionReference": "/Connections/JMS Connection.sharedjmscon" },
  "OutDataxsdString": {
    "OutDataxsdString": {
      "ref": { "ref": "pfx3:order" }
    }
  }
}
```
