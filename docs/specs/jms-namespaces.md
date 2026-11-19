# JMS Namespaces (`javax.jms` and `jakarta.jms`)

One `njams-sdk` artifact ships the JMS transport twice, once per JMS API namespace, and picks the one that fits at
runtime, with no change required of existing `javax` consumers. Both APIs are `provided`, so a consumer may have
either, both, or neither on its classpath. A classpath with **both** is a supported configuration, not a
misconfiguration: different broker products do not collide (e.g. the SDK on jakarta Artemis while the
application's own routes use javax ActiveMQ Classic). Only the same product built twice — `activemq-client` and
`activemq-client-jakarta` — cannot share a classpath, because both contain `org.apache.activemq.*` under identical
names.

The load-bearing properties of the selection path (non-unique provider names, services-file order with the
receiver grouping, declining via `TransportNotApplicableException`, the immovable
`communication.jms.factory.JmsFactory`) are stated in `.claude/rules/communication-layer.md`, section "Two JMS
Namespace Trees"; the contract classification of the types involved is in `.claude/rules/public-api-design.md`.
This spec does not repeat them; it adds the design behind them.

## Package layout

```
communication/                      TransportNotApplicableException (SPI Contract)
communication/jms/                  AbstractJmsSender, AbstractJmsReceiver      namespace-free bases (Internal)
                                    JmsSender, JmsReceiver, SharedJmsReceiver    jakarta
                                    JmsFactory (SPI)                             jakarta
                                    JndiJmsFactory, ActiveMqSslJmsFactory,
                                    AzureServiceBusJmsFactory                    jakarta
                                    JmsFactoryProbe (package-private)            jakarta
communication/jms/factory/          JmsFactory (SPI)                             javax, deprecated, sole occupant
communication/jms/javax/            JmsSender, JmsReceiver, SharedJmsReceiver,
                                    JmsFactoryProbe                              javax, deprecated
communication/jms/javax/factory/    JndiJmsFactory, ActiveMqSslJmsFactory,
                                    AzureServiceBusJmsFactory                    javax, deprecated
communication/fragments/            JMSChunkAssembly                             one implementation for both
utils/                              JmsMessageAccess
```

The forward-looking jakarta tree occupies the common package; the deprecated javax tree sits in its own `javax`
subpackage so the legacy surface is visible at a glance. Because `…jms.factory.JmsFactory` cannot move, the jakarta
SPI interface sits directly in `communication/jms/` and its implementations beside it, not in a `factory/`
subpackage. `javax` is a legal package segment: `javax.jms.Connection` written inside `…jms.javax` still resolves to
the root package.

Each `JmsFactory` interface has its own services file (`META-INF/services/…communication.jms.JmsFactory` for the
three jakarta factories, `…communication.jms.factory.JmsFactory` for the three javax ones). The order of the
sender/receiver services files is governed by `communication-layer.md`; `ServicesFileOrderTest` pins it.

The javax tree is `@Deprecated(since = "6.1.0")` **without** `forRemoval`: it must keep working for every existing
deployment, and `forRemoval` would promise the opposite. Deprecation on its public overrides follows
`documentation.md`.

## Base classes and hand-copied subclasses

1. **Namespace-free logic lives once**, in `AbstractJmsSender` / `AbstractJmsReceiver`, which name no JMS type;
   the subclasses supply the JMS-typed parts through hooks.
2. **Hand-copied per tree** (see `communication-layer.md`): `JmsSender`, `JmsReceiver`, `SharedJmsReceiver`,
   `JmsFactoryProbe`, the `JmsFactory` interface and its three implementations. The copies differ only in the
   namespace (and, for Azure, in the reflected class names, see below); a change to one tree's copy is made to the
   other too.
3. **`javax.naming` stays `javax.naming` in both trees** — JNDI is a JDK API, not Jakarta EE.
4. Both trees' factories report the same `getName()` (`JNDI`, `ActiveMQSslConnectionFactory`,
   `AzureServiceBusPremium`). No collision follows: each tree's `JmsFactory.find(...)` loads only its own services
   file.
5. The Azure factories reflect on different packages: javax `com.microsoft.azure.servicebus.jms.*`
   (`com.microsoft.azure:azure-servicebus-jms` 1.x), jakarta `com.azure.servicebus.jms.*`
   (`com.azure:azure-servicebus-jms` 2.x). `ActiveMqSslJmsFactory` reflects on
   `org.apache.activemq.ActiveMQSslConnectionFactory` in both trees, since `activemq-client-jakarta` keeps that name.

The bases are deliberately **not** generic over the JMS types: that would let the send and reply logic live in the
base, but at the cost of roughly a dozen accessor hooks for one-line JMS calls, which is worse than the duplication
it saves. The duplication is accepted because the duplicated part is connection plumbing that changes rarely, and
the javax half is deprecated.

**Lazy-linking invariant.** A class that is loaded regardless of namespace (the bases, `StringUtils`,
`JMSChunkAssembly`, `CommunicationFactory`, …) must not name a `javax.jms`/`jakarta.jms` type in a field, supertype,
interface, or in any signature or code path the SDK can reach — that would make the API a hard runtime requirement
and the failure would surface far from its cause. The only exemption is a deprecated binary-compatibility overload
the SDK never calls, because a method descriptor is resolved only on invocation:
`StringUtils.messageToString(javax.jms.Message)`, `StringUtils.headersToString(javax.jms.Message)` and
`JMSChunkAssembly.setHeader(javax.jms.Message, String, String)` each delegate to an `Object` overload, which is the
only one the SDK calls. Accepted residual risk: reflective enumeration of such a class (`getDeclaredMethods()` by
tooling) throws `NoClassDefFoundError` on a jakarta-only classpath. A reflective hop on the log-message send path is
never acceptable.

## Namespace-free message access

`JMSChunkAssembly` (a `GenericChunkAssembly<Object>`), `StringUtils.messageToString`/`headersToString` and the
receiver base's instruction parsing reach a message only through `utils.JmsMessageAccess`, which probes four
JDK-typed JMS methods by reflection: `getPropertyNames()`, `getObjectProperty(String)`,
`setStringProperty(String, String)`, `getText()`. One accessor serves all callers so the reflective surface is not
duplicated.

1. **Probe for methods, never test type names**: the runtime class is the provider's concrete message class, never
   `Message`. No `getPropertyNames` → not a JMS message (callers fall back to `String.valueOf`); no `getText` → not
   a text message (body omitted / command ignored).
2. **All four accessors are resolved eagerly, once per class, and absence is cached.** A failed lookup constructs
   a `NoSuchMethodException`; re-probing would put that on the command-receive path for every non-text message.
3. **Resolve against public interfaces first**, then the class itself made accessible — a provider's message class
   may be non-public, and a method resolved on it would fail at invocation.
4. **Cache with `ClassValue`, never a static map keyed by `Class`**, so a provider classloader is not pinned across
   an application-server redeploy.
5. `JmsMessageAccess` is in `utils/`, not `communication/`: `StringUtils` must not depend on the communication
   layer. It is `public` only for cross-package access and Internal.

This reflection is not on the log-message send path: `StringUtils` callers are trace-guarded or on an error branch
of the command-receive path, and chunk assembly serves command receive/reply only — so it does not violate
`runtime-performance-hotpath.md`. The chunk format has exactly one implementation for both trees; its emitted
headers are byte-identical to the former javax-only one, so the Wire Contract is unchanged.

## Selection

### At load time

A provider of the wrong namespace fails while `ServiceLoader` loads it, because its JMS superinterface
(`ExceptionListener`, `MessageListener`) cannot be resolved. `ServiceLoaderSupport` catches the failure and
continues. That alone is the load-time selection.

The transport lookups in `CommunicationFactory` and both `JmsFactory.find(...)` give `ServiceLoaderSupport`
`TransportNotApplicableException.isNotApplicable` as a classifier. Classified load failures count as "not
applicable", everything else as "unavailable":

1. Only "unavailable" signals that something is wrong. Otherwise the other tree's providers would raise that signal
   on every healthy classpath, and `JmsFactory.find`'s warning (no factory matched the setting value and something
   was unavailable) would appear on every startup that configures a plain JNDI name.
2. The availability report lists both buckets, labelled. With neither JMS API present it is the only text that
   says which API is missing.
3. The classifier lives on `TransportNotApplicableException`, not in `utils/ServiceLoaderSupport`, because the
   latter serves every SPI type; a JMS test there would hide genuinely broken providers of unrelated services.
   Without a classifier, every load failure counts as unavailable.

Decided not to change: the report names the missing JMS type but not the skipped provider class, and providers
failing on the same type collapse into one line. Recovering the class name would mean `ServiceLoaderSupport`
reading the services files itself; not worth it, because the only case where the report stands alone (neither API
present) is still actionable from the missing API's name.

### Classification

`TransportNotApplicableException.isNotApplicable(Throwable)` examines the whole cause chain and returns `true` only
for:

1. a `NoClassDefFoundError`/`ClassNotFoundException` whose message **starts with** a JMS API type name
   (`javax.jms.`, `jakarta.jms.`, or the slash forms);
2. a `ClassCastException` whose message **contains** a JMS API type name (it names source and target).

It must stay this narrow: a missing broker client library (the reflective broker-class lookup raises
`ClassNotFoundException` for `org.apache.activemq.*`) is a deployment error, and a cast failure in an HTTP or Kafka
provider is a defect — both must be reported as such, not silently skipped. The whole chain is examined because
`ActiveMqSslJmsFactory` and `AzureServiceBusJmsFactory` wrap the discriminating throwable into a `JMSException`.

### At init time

Both trees may load (mixed classpath), so the decision is made by **attempting the configured connection factory**,
not by checking which API is loadable. As the first step of `init`, each tree's sender and receiver resolve and
initialise the configured `JmsFactory` through their tree's `JmsFactoryProbe`; the `(ConnectionFactory)` cast
inside the factory's init is the namespace test. No separate probe entry point exists on the SPI.

1. The probe initialises the factory itself and closes it on success and failure. Resolving through
   `JmsFactory.find`'s initialising variant was rejected: it would leak a `JndiJmsFactory`'s `InitialContext` on
   the failing path and log an error with stack trace for every declining candidate on a healthy mixed classpath.
2. A failure classified by `isNotApplicable` → `TransportNotApplicableException`. Other unchecked throwables
   propagate unchanged; a checked one is wrapped into the same `IllegalStateException` factory initialisation
   produced before, so a genuine configuration error reads as it did.
3. **Resolve and discard.** `connect()` still resolves its own factory, so the factory lifecycle on the primary
   production transport is unchanged. The cost is one extra factory resolution (for JNDI: an `InitialContext`
   plus lookup) per `init`, i.e. per pooled sender and per reconnect.

Both trees' `JndiJmsFactory.init` throw `NamingException` naming `njams.sdk.communication.jms.connectionFactory`
when that setting is blank: JMS always needs a connection factory, so the missing setting is reported at init
rather than as a later, unrelated failure.

### Candidate loop

`CommunicationFactory` tries every candidate with the configured name in order (see `communication-layer.md` for
the order and for "only all failing is an error"):

1. **Any failure skips to the next candidate**, not only `TransportNotApplicableException`. Limiting fall-through
   to the marker type was withdrawn: a `JmsFactory` registered in only one namespace's services file makes the
   other tree fall back to its default `JndiJmsFactory` and fail with an unrelated error, and aborting there would
   block the namespace that works. This is safe because candidates are filtered by `getName()`, so they never
   include a different transport. The marker type still keeps the reporting honest. The all-failed error names
   every declined candidate with its cause, plus the availability report.
2. The winner is logged once at INFO by fully qualified class name, because both trees share the simple names.
   Declined candidates log at DEBUG.
3. **Sender memo:** the first successful sender class is memoized on the `CommunicationFactory` instance, so the
   pooled senders and the startup/reconnect path (`SenderPool`, `SenderConnector`) do not re-run selection.
4. **Receiver: no memo.** `Njams` creates a fresh `CommunicationFactory` for each receiver lookup, so sender and
   receiver resolve independently from the same settings. Accepted; there is no cross-wiring between the two
   decisions, and there is no JVM-global namespace switch — two clients with different settings in one JVM resolve
   to different trees (proved by `njams-sdk-jms-mixed-test`).

No setting selects the namespace, and the SPI gained no method for it.

## Public-API facts that constrain changes

1. `AbstractJmsSender`, `AbstractJmsReceiver`, `JmsMessageAccess` and the concrete senders/receivers are
   **Internal** despite being `public` (ServiceLoader needs public classes in different packages); their Javadoc
   says so and they are not on the registered-SPI list. That the sender base's JSON serialization is an
   overridable `protected` hook adjacent to the Wire Contract is accepted for that reason.
2. `isCongestion(Throwable)` stays `protected` in every override: `AbstractSender` declares it as a protected SPI
   template method, and widening it can never be undone.
3. The concrete senders and receivers are Internal and may move or be renamed: code depending on them
   fails loudly at class load. An SPI interface never moves, because its relocation fails silently.
4. The jakarta `JmsFactory` interface exposes `public static final Logger LOG` (interface fields are implicitly
   public), in exact parity with the javax interface. Kept deliberately: avoiding it would need a holder class
   and break the near-identical copies.

## Build and classpath

1. `njams-sdk` compiles both trees in one pass on one classpath from two **different coordinates**, both
   `provided`: `javax.jms:javax.jms-api` and `jakarta.jms:jakarta.jms-api` 3.x. Using `jakarta.jms:jakarta.jms-api`
   for both is impossible: 2.0.x ships the `javax.jms` package, 3.x `jakarta.jms`, and Maven resolves one version
   per coordinate.
2. `njams-sdk` has no ActiveMQ on its compile classpath. On its test classpath `activemq-client` and
   `activemq-broker` exclude their transitive `jakarta.jms:jakarta.jms-api` 2.0.x, which would collide with 3.x;
   `javax.jms:javax.jms-api` satisfies ActiveMQ's references.
3. ActiveMQ stays on 5.x (Java 11 target; 6.x and its jakarta broker need Java 17). `activemq-client-jakarta` is
   client-only: no embedded broker, no `vm://`.
4. Neither JMS API is shaded or relocated.

## Tests

1. **Mocked unit tests exist once per tree** with identical test names and assertions: jakarta under
   `njams-sdk/src/test/.../communication/jms/`, javax under `.../jms/javax/` and `.../jms/factory/`, each with its
   own namespace-bound helpers. Duplicated tests are preferred over leaving either tree uncovered, deprecated or
   not. A change to one tree's tests is mirrored in the other.
2. Exceptions to the mirror: `ActiveMqSslJmsFactoryTest.initCreatesActiveMqSslConnectionFactory` cannot run for
   jakarta inside `njams-sdk` (only the javax `activemq-client` can be on that classpath, so the jakarta factory
   correctly declines); its jakarta counterpart is `JakartaActiveMqSslJmsFactoryTest` in
   `njams-sdk-jms-jakarta-test`. The two `AzureServiceBusJmsFactoryTest`s pin each tree's reflected Azure class
   names by reading the compiled class's constant pool, since no Azure client is on the classpath and a plain
   mirror would stay green against a wrong Azure package.
3. **`njams-sdk-jms-namespace-tests`** — four broker-less, Docker-less surefire modules in the default build, run
   on every build, never deployed:

   | Module | Proves |
   |---|---|
   | `njams-sdk-jms-javax-test` | javax sender/receiver selected; jakarta tree skipped, not fatal, named in the report |
   | `njams-sdk-jms-jakarta-test` | the same, mirrored |
   | `njams-sdk-jms-absent-test` | handleable `IllegalStateException`; both trees not applicable; report names both missing APIs |
   | `njams-sdk-jms-mixed-test` | settings (the JNDI initial-context factory) pick the tree; two clients in one JVM resolve to different trees; settings fitting neither give one error naming every cause |

   Rules: tests drive `CommunicationFactory` directly and never call `Njams.start()`, which connects — `init` plus
   a client-side JNDI lookup yields an inert `ConnectionFactory` without opening a socket. The aggregator depends
   on the `njams-sdk` artifact, **not** its test-jar. The mixed module needs two different broker products,
   because one product's javax and jakarta clients cannot share a classpath; its Artemis client stays on a release
   that runs on Java 11. The absent case cannot live in `njams-sdk`, which compiles against both APIs.
4. **Broker-backed tests stay javax-only**: `njams-sdk`'s `EmbeddedActiveMqBroker` ITs and
   `njams-sdk-communication-it`. They exercise the communication lifecycle, which lives in the shared bases that
   both trees extend. They move to jakarta when the javax tree is removed.

## Related

1. `.claude/rules/communication-layer.md` — "Two JMS Namespace Trees", transport relevance (JMS gets the highest
   scrutiny; the javax path behaving as before is the regression that matters most).
2. `.claude/rules/public-api-design.md` — SPI Contract, registered SPI types, the immovable javax `JmsFactory`.
3. `.claude/rules/message-format-changes.md` — Wire Contract gate covering `communication/fragments/`.
4. `docs/specs/communication-it-module.md` — resilience suite (javax-only).
