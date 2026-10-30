# SDK-495 — Supporting `javax.jms` and `jakarta.jms` from one artifact

**Ticket:** SDK-495 · **Branch:** `6.1-dev` (6.1.0) · **Date:** 2026-10-28

This document records the design agreed with the user for SDK-495. It supersedes the handover proposal that was
attached to the ticket: the handover's core insight (one artifact, lazy linking, `ServiceLoaderSupport` already
tolerates unloadable providers) is kept, but its delivery mechanism — Eclipse Transformer generating a sibling
tree at build time — was rejected in favour of a hoist-and-hand-copy split, and its package layout was inverted.

Everything in "Verified findings" was read from `njams-sdk/src/main/java` on the `6.1-dev` working copy on
2026-10-28, not inferred.

---

## 1. Intent

The SDK's JMS transport compiles against `javax.jms` only. Client implementations on Jakarta-based frameworks
(Camel 4, Spring 6+, Quarkus 3+) cannot use it: those frameworks require `jakarta.jms`, and the two namespaces
share no common supertype, so no single compiled signature serves both. Worse, broker client libraries ship one
variant per namespace *under identical class names* (`activemq-client` and `activemq-client-jakarta` both contain
`org.apache.activemq.ActiveMQConnectionFactory`), so only one can be on a classpath — a `javax`-only SDK forces
every other library in the JVM onto `javax`.

The concrete blocker is the nJAMS Client for Apache Camel (NJAC-56): the showcase scenario cannot use JMS at all
while this stands.

The SDK must serve both namespaces from a single artifact, selecting at runtime from what is on the classpath,
with no change required of existing `javax` consumers.

## 2. Goals, non-goals, anti-goals

### Goals

- One artifact supports both namespaces, chosen at runtime.
- With only `javax.jms` + a `javax` broker client present, the JMS transport behaves exactly as today.
- With only `jakarta.jms` + a `jakarta` broker client present, send and receive work equivalently, including
  chunked messages.
- With neither present, the SDK starts, the JMS transport reports itself unavailable with a clear message, and no
  `NoClassDefFoundError` escapes.
- **With both APIs and both broker clients present, the SDK uses whichever namespace the configured connection
  factory actually belongs to.** This is a supported configuration, not a misconfiguration: different broker
  products do not collide (Artemis's jakarta client is
  `org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory`, AMQ Classic's is
  `org.apache.activemq.ActiveMQConnectionFactory`), so an application may legitimately run the SDK on jakarta
  Artemis while its own routes use javax AMQ Classic. Only `activemq-client` and `activemq-client-jakarta` — the
  same product built twice — cannot share a classpath.
- The Camel client runs with Camel 4 and the SDK sharing one Jakarta ActiveMQ client against one broker.
- The `jakarta` implementation is the primary, forward-looking one; `javax` is the legacy path.

### Non-goals

- Migrating existing clients off `javax`. They need not move.
- Any change to the nJAMS wire format or message protocol. The namespace is purely a Java-level API question.
- The other transports (HTTP, Kafka).
- A separate `-jakarta` build artifact, or a bytecode-transform variant.
- OSGi metadata. The build produces no bundle manifest today (`maven-jar-plugin` writes a plain manifest, there
  is no bnd/felix plugin), so there is nothing to mark `resolution:=optional`.

### Anti-goals — things that would make this a failure even if it technically worked

- A third-party `JmsFactory` implementation silently disappearing after the upgrade.
- A warning, error, or stack trace on a *healthy* single-namespace startup, caused by the other tree failing to
  load as designed.
- Either JMS API becoming a hard runtime requirement of the SDK.
- A class that is always loaded naming a type from either namespace in a signature **the SDK itself invokes** —
  that would drag the API in unconditionally and defeat lazy linking. The symptom appears far from the cause.
  Deprecated binary-compatibility overloads that the SDK never calls are exempt: a method descriptor is resolved
  only when the method is invoked, so an uncalled `javax.jms`-typed overload costs nothing on a `jakarta`-only
  classpath. The exemption is narrow — it does not extend to fields, superclasses, interfaces, or any signature
  on a code path the SDK can reach. Known residual risk: reflective enumeration of such a class
  (`getDeclaredMethods()` by framework or tooling classpath scanning) would throw `NoClassDefFoundError`.
  Accepted.
- A reflective hop on the log-message send path.
- One transport implementation being silently substituted for another because the first one's `init` happened to
  fail. Falling through to the next candidate must be opt-in and explicit (§5.4).

## 3. Constraints

- One artifact; both JMS APIs stay `provided`/optional; neither becomes transitive.
- Existing `javax` consumers upgrade with no changes: no new required dependency, no new configuration, unchanged
  behaviour.
- Java 11 target. ActiveMQ stays on the 5.x line for that reason (SDK-418); ActiveMQ 6.x, which has the
  Jakarta broker, requires Java 17 and is therefore unavailable.
- Scope covers sender, receiver, chunked-message assembly, and the `JmsFactory` SPI with its three existing
  implementations.
- No new third-party library without asking (project rule). This design adds none.

## 4. Verified findings this design rests on

1. **`ServiceLoaderSupport` already tolerates providers that cannot be loaded.** `SaveIterator`
   (`utils/ServiceLoaderSupport.java:71`) wraps the `ServiceLoader` iterator and catches `Throwable` around both
   `hasNext()` and `next()`, records the failure in an `unavailable` set, and **continues iterating**.
   `hasUnavailable()` and `describeAvailability(...)` report it.
2. **The failure genuinely fires at class load.** `JmsSender implements ExceptionListener` and
   `JmsReceiver implements MessageListener` — superinterfaces are resolved eagerly when a class is loaded, so a
   wrong-namespace provider dies with `NoClassDefFoundError` inside `ServiceLoader` and is skipped. This is the
   whole selection mechanism; nothing new is needed.
3. **Selection is first-match-wins.** `CommunicationFactory.findSenderImplementation` / `findReceiverType` and
   `JmsFactory.find` all go through `ServiceLoaderSupport.find(predicate)`, which returns the first match.
   Services-file order is therefore the tiebreak.
4. **`ClasspathValidator` is diagnostic only for this purpose.** By the time `validate()` runs
   (`CommunicationFactory.createReceiver` / `getSender`), the class is already loaded — loading has already
   decided the outcome. It is not the probe.
5. **`javax.naming` is JDK, not Jakarta EE.** JNDI did not move. `InitialContext`, `NamingException`,
   `NameNotFoundException` stay `javax.*` in **both** trees.
6. **`org.apache.activemq.ActiveMQSslConnectionFactory` exists under that exact name in
   `activemq-client-jakarta`**, implementing `jakarta.jms.ConnectionFactory`. Since `ActiveMqSslJmsFactory`
   addresses it through `ReflectionWrapper` by string, even the reflection string is unchanged between trees.
7. **`activemq-client-jakarta` is client-only.** No embedded broker, no `vm://` transport, and it cannot share a
   JVM with `activemq-client`. An external broker is mandatory for any Jakarta integration test.
8. **The pom already carries `jakarta.jms:jakarta.jms-api:2.0.3`**, which is Jakarta-*branded* but still ships the
   `javax.jms` package — the namespace flip happened at 3.0. The version was pinned deliberately in SDK-467 to
   match what `activemq-client` brings transitively. Both namespaces have the same `groupId:artifactId`, so two
   versions cannot sit on one Maven classpath.
9. **No FQCN of any JMS class is documented anywhere** — not in `wiki/`, `settings_full.properties`, `docs/`, or
   the sample modules. The documented way to select a factory is its `getName()`.
10. **Both JMS APIs are `provided` and therefore not shaded.** `checkstyle.xml` carries no `IllegalImport` rule
    for the relocated packages (only a comment), and neither `javax.jms` nor `jakarta.jms` is in the shade
    plugin's `<relocations>`. Nothing to do here.
11. **The current `javax.jms` surface** is 64 references across 11 files: `JmsReceiver` (24), `JmsSender` (16),
    `JndiJmsFactory` (5), `JmsFactory` (5), `StringUtils` (3), `JMSChunkAssembly` (3),
    `AzureServiceBusJmsFactory` (2), `ActiveMqSslJmsFactory` (2), `SharedJmsReceiver` (2),
    `GenericChunkAssembly` (1), `NjamsSettings` (1). The last two are Javadoc `{@link}` imports only — they
    produce no bytecode reference and are harmless.

## 5. Design

### 5.1 Package layout

The **surviving** (`jakarta`) implementation occupies the common structure; the **deprecated** (`javax`)
implementation moves into its own package, so the legacy surface is visible at a glance.

```
communication/jms/                 shared namespace-free base classes
                                   JmsSender, JmsReceiver, SharedJmsReceiver     (jakarta)
                                   JmsFactory (SPI interface)                    (jakarta)
                                   JndiJmsFactory, ActiveMqSslJmsFactory,
                                   AzureServiceBusJmsFactory                     (jakarta)

communication/jms/factory/         JmsFactory  — javax, @Deprecated, FROZEN, sole occupant

communication/jms/javax/           JmsSender, JmsReceiver, SharedJmsReceiver     @Deprecated
communication/jms/javax/factory/   JndiJmsFactory, ActiveMqSslJmsFactory,
                                   AzureServiceBusJmsFactory                     @Deprecated

communication/fragments/           JMSChunkAssembly — one Object-based implementation, serves both trees
```

**`com.im.njams.sdk.communication.jms.factory.JmsFactory` must never move.** Third-party `javax`
implementations ship `META-INF/services/com.im.njams.sdk.communication.jms.factory.JmsFactory`. If the interface
relocated, `ServiceLoader` would look up the new name, never read their file, and their factory would **silently
disappear** — the SDK falling back to `JndiJmsFactory` with no error at all. That is a compile break *and* a
silent runtime break, and it violates the ticket's core constraint. The interface stays where it is, deprecated,
alone in its package; its Javadoc must say it cannot move and why.

Because that name is taken, the jakarta SPI interface sits directly in the common package as
`com.im.njams.sdk.communication.jms.JmsFactory`, with the three jakarta implementations beside it rather than in
a `factory/` subpackage.

`javax` is a legal package segment: Java never resolves a package name relative to the current package, so
`javax.jms.Connection` written inside `…jms.javax` still resolves to the root `javax`. It compiles; it reads
oddly, and that is accepted.

### 5.2 The base/subclass split

Namespace-free logic is hoisted into shared base classes in `communication/jms/`. Everything that touches a
`Message`, `Session`, `Connection`, `MessageProducer`, `MessageConsumer`, `Topic`, `Queue`, `ConnectionFactory`
or `JMSException` is hand-copied once per namespace.

**The bases stay plain — not generic over the JMS types.** Making `AbstractJmsSender<F,S,P,M>` generic would let
`sendMessage` and `reply` live in the base, but at the cost of roughly a dozen abstract accessor hooks for what
are one-line JMS calls. That is worse than the duplication it saves.

| Class | Hoisted to the shared base | Hand-copied per namespace |
|---|---|---|
| `JmsSender` (453 lines) | `init`, `COMMUNICATION_NAME`/`DEFAULT_DESTINATION`, `getName`, destination-prefix computation, `serialize`, the `sendMessage`/`sendChunks` control flow, `logError`, `isMessageRejected`, the `isCongestion` cause-chain walk (with a hook naming the congestion exception type), the delivery-mode string→int mapping | `connect`, `close`, producer/session creation, `buildMessage`, `setProperty`, `tryToSend`, `onException`, `librariesToCheck` |
| `JmsReceiver` (604 lines) | `init`, `createMessageSelector`, `ancestorPathStrings`, `provideNjamsInstances`, `updateFilters`, `stop`, `printExceptionsOnClose`, `getName`, the instruction-deserialization half of `getInstruction` | `connect`, `closeAll`, connection/session/consumer/topic creation, the `createMessageFilter` predicate body, `onMessage`, `reply`, `onException`, `librariesToCheck` |
| `SharedJmsReceiver` (184 lines) | — | all of it; every method takes a `Message` |
| `JmsFactory` + 3 implementations | — | all of it, but see below: the copies are near-identical |

This leaves roughly 55–60% of the JMS code duplicated. That is accepted: the duplicated part is connection
plumbing that changes rarely, and the `javax` half is deprecated and expected to be removed eventually.

The three factory implementations are cheap to copy because they are already reflection-based:

- `ActiveMqSslJmsFactory` and `AzureServiceBusJmsFactory` name their provider classes as **strings** through
  `ReflectionWrapper`, not as imports. The only namespace-bound parts are the `(ConnectionFactory)` cast on the
  result and the `JmsFactory` interface they implement. Neither adds a compile dependency.
- `JndiJmsFactory` is the same, plus finding 5: its JNDI machinery stays `javax.naming` in both trees. Only the
  `(ConnectionFactory)` cast and the `Session`/`Topic`/`Queue` parameters flip.

Both trees keep the same `getName()` values — `JNDI`, `ActiveMQSslConnectionFactory`, `AzureServiceBusPremium` —
consistent with 5.4.

**The base classes are Internal, not SPI.** They must be `public` (ServiceLoader needs public subclasses, and the
two trees are in different packages, so package-private is impossible). A `public abstract class AbstractJmsSender
extends AbstractSender` is visually indistinguishable from an extension point, so its Javadoc must state plainly
that it is internal SDK infrastructure and not an extension point. They are not added to the registered-SPI list.

### 5.3 Shared helpers via duck typing

Two pieces of JMS-touching code are deliberately **not** duplicated, because everything they need has JDK-only
signatures. They become `Object`-typed and reach the message reflectively.

#### `StringUtils.messageToString` / `headersToString`

The complete reflective surface is four members, none of which mentions a JMS type in a parameter or return:

| Member | Returns |
|---|---|
| `getPropertyNames()` | `java.util.Enumeration` |
| `getObjectProperty(String)` | `Object` |
| `getText()` | `String` (text messages only) |
| the `instanceof TextMessage` test | — |

- **Probe for the method, do not test the type name.** A check like
  `"Message".equals(obj.getClass().getSimpleName())` can never match: the runtime class is the provider's
  concrete type (`org.apache.activemq.command.ActiveMQTextMessage`, `org.apache.qpid.jms.message.JmsTextMessage`),
  never `Message`. Instead: `getPropertyNames()` present → treat it as a JMS message; absent → fall back to
  `String.valueOf(obj)`. `getText()` present → append the body; this replaces `instanceof TextMessage` exactly.
- The reflection itself, including the caching, is not implemented here — it is delegated to the shared accessor
  below.
- `abbreviate`, the `StringBuilder` assembly and the error formatting are already namespace-free and stay as they
  are.

**Binary compatibility.** The existing `messageToString(javax.jms.Message)` / `headersToString(javax.jms.Message)`
are `public static` on a `public` class. Changing the parameter to `Object` is source-compatible but
binary-incompatible — a pre-compiled external caller linking `messageToString(Ljavax/jms/Message;)` would get
`NoSuchMethodError`. So: **add the `Object` overload, keep the `Message` overload `@Deprecated` and delegating.**

Overload resolution then does the right thing with no casts: the `javax` tree binds to the deprecated overload,
the `jakarta` tree to `Object`. Because the whole `javax` tree is itself `@Deprecated`, `javac` suppresses the
deprecation warning at those use sites.

#### `JMSChunkAssembly`

Its three accessors (`getHeaders`, `getText`, `setHeader`) likewise have JDK-only signatures, so a single
`GenericChunkAssembly<Object>`-based implementation serves both trees with the same reflective approach. This
keeps one source of truth for the chunk format rather than two.

`GenericChunkAssembly<T>` itself needs no change — its only `javax.jms` reference is a Javadoc `{@link}` import.

`getHeaders` and `getText` are `private static` and change freely. **`setHeader(javax.jms.Message, String, String)`
is `public static`** and has the same binary-compatibility profile as the `StringUtils` helpers, so it gets the
same treatment: add the `Object` overload, keep the `Message`-typed one `@Deprecated` and delegating. It is
covered by the anti-goal exemption in §2 — the SDK calls only the `Object` form, so on a `jakarta`-only classpath
the `javax`-typed descriptor is never resolved.

Consequences to carry forward:

- `JmsReceiver.getInstruction`'s `message instanceof TextMessage` guard becomes the same `getText()` probe, and
  the `(TextMessage)` cast disappears.
- `reply`'s `JMSChunkAssembly.setHeader(responseMessage, k, v)` call site becomes `Object`-typed.
- Changing `JMSChunkAssembly`'s supertype from `GenericChunkAssembly<TextMessage>` to `GenericChunkAssembly<Object>`
  is a public signature change — see §5.8.

#### One shared accessor, one cache

The two callers need almost the same reflective surface, and both wrap every call in the same
`JMSException`-swallow-and-log pattern:

| | `getPropertyNames()` | `getObjectProperty(String)` | `getText()` | `setStringProperty(String, String)` |
|---|---|---|---|---|
| `StringUtils` | ✓ | ✓ | ✓ | |
| `JMSChunkAssembly` | ✓ | ✓ | ✓ | ✓ |

Implementing that twice would reintroduce, reflectively, the duplication this section exists to remove. So the
duck typing lives **once**, in a single internal accessor that owns the method probing, the cache, and the
error handling; `StringUtils` and `JMSChunkAssembly` become thin callers of it.

- **Placement: `com.im.njams.sdk.utils`.** It cannot live under `communication/` — `utils/StringUtils` would
  then depend on the communication layer, which is the wrong direction (`code-quality-general.md`). `utils/`
  is neutral and reachable from `communication/fragments/`. It must be `public` for that reason, and is
  **Internal**: its Javadoc says so, same as the base classes in §5.2.
- **Cache with `ClassValue`, not a static `ConcurrentHashMap<Class<?>, …>`.** A static map keyed by `Class`
  holds strong references to those classes and can pin a classloader across an application-server redeploy —
  a real concern for an SDK that explicitly deals with external and thread-context classpaths (§6, and
  `JmsFactory`'s own Javadoc). `ClassValue` is built for exactly this association and does not block class
  unloading.
- In practice the cache holds one or two entries — the provider's concrete message classes — for the life of
  the JVM, so the steady-state cost is a `ClassValue` lookup plus `Method.invoke`.
- The probe result must distinguish "not a JMS message at all" (no `getPropertyNames`) from "a JMS message that
  is not a text message" (no `getText`), because the callers treat those differently: the former falls back to
  `String.valueOf(obj)`, the latter simply omits the body.
- **Absence is cached, not re-probed.** Resolve all four accessors **eagerly, once per class**, and record an
  absent one explicitly rather than leaving a gap that triggers another lookup. This is what makes the
  negative cases cheap, and they are the ones that would otherwise hurt: a failed `getMethod(...)` constructs
  and fills in a `NoSuchMethodException`, so re-probing per message would put exception construction on the
  command-receive path every time a non-text message arrives — exactly the `instanceof TextMessage` case that
  `JmsReceiver.getInstruction` evaluates for every inbound command, and that `StringUtils.messageToString`
  evaluates for every traced one. Resolving the whole set up front removes the retry path entirely, for
  both the "not a JMS message" and the "not a text message" outcomes.
- **Resolve against the interface, not the concrete class, where possible.** A provider's message
  implementation may be a non-public class implementing a public interface; a `Method` resolved on such a
  class fails at `invoke` with `IllegalAccessException`. Walking the type's interfaces for the JMS message
  interface — or, failing that, `setAccessible(true)` — avoids the classic trap. Cover it with a test using a
  package-private implementation class.

**Performance.** `runtime-performance-hotpath.md` bans reflection in hot paths and names `communication/` as one.
This reflection is **not** on the log-message send path. `StringUtils`' four call sites are on the command-receive
path, three of them inside `LOG.isTraceEnabled()` and the fourth on a filter-failure error branch. The chunk
assembly reflection is on the command-receive and command-reply paths, which carry command traffic only, not log
messages. This reasoning is recorded here so it is not re-litigated in review.

### 5.4 Runtime selection

No new setting, and no new SPI method. The decision is made by **attempting the configured connection factory**,
not by inspecting which API happens to be loadable — because both can be loadable legitimately (§2).

Both trees' senders and receivers report `getName()` → `"JMS"`, so a name match may now yield more than one
candidate. `CommunicationFactory` tries them in order:

```
for each candidate matching the configured communication type, in services-file order:
    1. resolve the candidate's own JmsFactory from the settings
       └─ fails with a namespace mismatch?  → discard this candidate, try the next
    2. init() the candidate
       └─ fails?                            → HARD ERROR, do not try the next
    success → this is the implementation
no candidate left → error naming every cause
```

1. **Step 1 is the discriminator, and it already exists.** `JndiJmsFactory.initFactory` does
   `factory = (ConnectionFactory) context.lookup(connectFactory)`; `ActiveMqSslJmsFactory` and
   `AzureServiceBusJmsFactory` cast the same way in their own `initFactory`/`init`. That cast is the test. It runs
   as the **first action of the candidate's `init(ClientSettings)`**, so no separate probe entry point is needed.
2. **Resolve and discard.** The probed factory is closed again, and `connect()` keeps doing
   `try (JmsFactory f = JmsFactory.find(settings))` exactly as today. Factory lifecycle on the primary production
   transport is unchanged. The cost is one duplicate `InitialContext` + JNDI lookup per resolution.
   **§5.4.1 memoizes the chosen *implementation class*, not the probe: `init` still runs per sender instance
   (up to `maxSenderThreads`) and per reconnect attempt, so that lookup is per-`init`, not once per process**
   (implementation finding, Task 8).
   The probe calls `JmsFactory.find(settings, false)` and then `init(settings)` itself inside the
   try-with-resources, rather than `find(settings, true)`. Bypassing the static `JmsFactory.initFactory` is what
   closes the factory on the failing path too (`JndiJmsFactory.init` assigns its `InitialContext` before the
   failing lookup, so the instance would otherwise leak), and it avoids that method's `LOG.error(..., e)`, which
   would otherwise print a stack trace for every declining candidate on a healthy mixed-classpath startup. The
   probe re-wraps a *checked* failure into the same `IllegalStateException("Could not initialize: " + factory)`
   shape `initFactory` produces, so a genuine configuration error reads as before; an unchecked failure now
   propagates untouched instead of being wrapped, which is what keeps `init(null)` an `NullPointerException`.
   *(Holding the resolved factory for the sender's lifetime would remove a JNDI lookup per connect and per
   reconnect, but it changes that lifecycle and is therefore out of scope here — see §9.)*
3. **Mismatch is signalled by a dedicated marker exception**, thrown only by the SDK's own JMS `init` when step 1
   fails with `ClassCastException`, `NoClassDefFoundError` or `ClassNotFoundException`.
   **Two refinements from implementation (Task 8). First, the whole cause chain is examined, not just the top
   frame: `ActiveMqSslJmsFactory` and `AzureServiceBusJmsFactory` both catch and rethrow wrapped into a
   `JMSException` of their own, so the discriminating throwable sits a level below what the factory hands out.
   Second, a `NoClassDefFoundError`/`ClassNotFoundException` counts only when the missing type's own name is a
   JMS API type (`javax.jms.`/`jakarta.jms.`, in either the dotted or the slash spelling); a `ClassCastException`
   always counts. Type alone is too coarse — `ReflectionWrapper` raises a `ClassNotFoundException` for a broker
   class when a client library is simply not deployed, and classifying that as a namespace mismatch would make
   the SDK silently skip to another transport over a deployment error, which is this document's own §2
   anti-goal. The name test is exactly the one §5.5 already needs, so the two share one implementation:
   `ServiceLoaderSupport.isNotApplicable(Throwable)`.** `CommunicationFactory`
   falls through on **that type only**. This matters: falling through on any `NoClassDefFoundError` would let a
   third-party sender whose `init` fails for an unrelated reason be silently replaced by a different transport
   implementation — a failure mode that is nearly invisible in a log. Fall-through stays opt-in.
   The same type has a second use at class-load time, in `ServiceLoaderSupport` — see §5.5.
4. **Step 2 is a hard error, deliberately.** Once the factory has resolved, the namespace is already proven, and
   everything `init` does afterwards (`super.init`, `useProjectQueue`, `SplitSupport`, topic name, mapper) is
   namespace-free and would fail identically in the other tree. Falling through would report a plain
   configuration mistake against the wrong namespace.
5. **Candidate order** is services-file order. **List the jakarta entries first** in
   `META-INF/services/com.im.njams.sdk.communication.AbstractSender` and `…AbstractReceiver`, so jakarta is the
   forward-looking default when both are viable. An FQCN in `njams.sdk.communication.jms.jmsfactory` remains the
   explicit override. The `AbstractSender`/`AbstractReceiver` Javadoc must document that provider names need not
   be unique and how the tie is broken.
6. **All candidates exhausted** → one error naming *every* cause, not just the last.
   `ServiceLoaderSupport.describeAvailability` already has the right shape.
7. **Neither API present:** no candidate loads at all, `SaveIterator` skips both trees (finding 2), and
   `CommunicationFactory.getSender`/`getReceiver` throw `IllegalStateException` after logging
   `describeAvailability(...)`. Verify the text actually identifies the missing JMS API.

#### 5.4.1 Memoization

`SenderPool.create()` calls `factory.getSender()` once per pooled sender — up to `maxSenderThreads`, default 8 —
and `SenderConnector.createSender()` uses the **same** `CommunicationFactory` instance for its startup/reconnect
path, with the senders it produces joining the same pool and the same bound. Without memoization the resolution
would run on every one of them.

**Memoize the resolved candidate on the `CommunicationFactory` instance.** That covers both creation paths with
one resolution.

The receiver resolves **independently**: `Njams` constructs its own `CommunicationFactory` for it
(`Njams.java:334`/`371`). Same settings, same outcome, so two resolutions per client in total. That is accepted —
no cross-wiring between the sender's and the receiver's decision.

#### 5.4.2 Shared code this touches

- `ServiceLoaderSupport` — two changes. `CommunicationFactory` needs *all* name matches rather than the first;
  `stream()`/`getAll()` already exist, so `findSenderImplementation`/`findReceiverType` become filtered streams
  instead of `find`. And `SaveIterator` splits its single `unavailable` set into "not applicable" and "genuinely
  unavailable" (§5.5). Internal, free to change.
- `CommunicationFactory` — the candidate loop, the marker-exception handling, and the memo field. Internal, free
  to change.
- The new marker exception type itself — SDK-internal, in `communication/`.

No change to `AbstractSender`/`AbstractReceiver` beyond Javadoc. **The SPI gains no new method.**

### 5.5 `hasUnavailable()` must stop meaning "something is wrong"

`ServiceLoaderSupport.SaveIterator` records **every** provider that fails to load into its `unavailable` set.
After this change the other tree's providers always fail to load, by design, so
`ServiceLoaderSupport.hasUnavailable()` would be **true on every healthy single-namespace classpath** — while
today that signal means misconfiguration.

Exactly one caller reads it: `JmsFactory.find(String settingKey, String toFind)` — currently
`JmsFactory.java:121` — which warns when no factory matched *and* something was unavailable:

```
LOG.warn("No JMS factory implementation found for '{}' (setting {}). This is expected if that value is not the
         name of an implementation, e.g., a JNDI name. Otherwise, the intended implementation could not be
         loaded.{}{}", ...)
```

In the most common configuration — `njams.sdk.communication.jms.connection.factory` holding a plain JNDI name,
which matches no SPI provider — that condition would become permanently true, so **every healthy JMS startup
would emit a spurious warning**.

**Fix it in `ServiceLoaderSupport`, using the §5.4 marker type.** That type is therefore not only an `init`
signal; it is the SDK's general "this implementation is not applicable on this classpath" classification, used
at two different points:

1. **Thrown** by the SDK's JMS `init` when factory resolution fails on a namespace mismatch (§5.4 bullet 3).
2. **Assigned** by `SaveIterator` when a provider fails to *load* and the root cause is a missing
   `javax.jms.*` or `jakarta.jms.*` type. The missing type's own name is the evidence, and
   `addUnavailable(...)` already walks to the root cause, so the information is in hand.

Both points therefore call the same public helper, `ServiceLoaderSupport.isNotApplicable(Throwable)`, which
walks the cause chain (bounded, with a self-reference guard) and applies the JMS-API name test described in §5.4
bullet 3. `ServiceLoaderSupport` then keeps two buckets instead of one:

- **not applicable** — classified as above; expected, not a problem;
- **genuinely unavailable** — everything else: a broken provider, a `ServiceConfigurationError`, a missing
  dependency that has nothing to do with the JMS namespaces.

`hasUnavailable()` reports only the second bucket, so `JmsFactory.find`'s warning needs no change and fires only
when something is really wrong. Fixing it centrally is also why this does **not** have to be duplicated in both
trees' `find(...)`.

`describeAvailability(...)` must keep reporting **both** buckets, clearly labelled. This matters for §5.4
bullet 7: when neither JMS API is present, every entry is "not applicable", `hasUnavailable()` is false, and that
text is the only thing that tells the user which API is missing.

Scope note: the classification covers the two JMS namespaces, which is what the SDK ships. It is not a general
extension point, and third-party providers failing for their own reasons stay in the "genuinely unavailable"
bucket, which is correct.

Unaffected: `CommunicationFactory:100` / `:283` and `ConfigurationProviderFactory:96` call `describeAvailability`
only on already-failing paths; `ServiceLoaderSupport.debugLog()` is debug-level.

### 5.6 SPI impact

| Item | Change |
|---|---|
| `communication.jms.JmsFactory` (jakarta) | **New registered SPI type** — a fifth entry in the list in `public-api-design.md`, with its own `META-INF/services` file. That rule file must be updated as part of this ticket. |
| `communication.jms.factory.JmsFactory` (javax) | `@Deprecated(since = "6.1.0")`, **no `forRemoval`** — `javax` must keep working, and `forRemoval` would promise the opposite. `@deprecated` tag points at the jakarta sibling. Frozen in place. |
| Existing third-party javax `JmsFactory` implementations | **Unaffected.** Not broken, not recompiled. |
| `AbstractSender` / `AbstractReceiver` | **No new method, no signature change.** Javadoc gains the contract that provider names need not be unique, that candidates are tried in services-file order, and that an implementation may decline by throwing the marker exception from `init` (§5.4). |
| `ShareableReceiver<T>` / `SharedReceiverSupport<R,T>` | No change. Generic and erased; the jakarta copy implements `ShareableReceiver<jakarta.jms.Message>` and `CommunicationFactory`'s `instanceof ShareableReceiver` check still works. |
| `ClasspathValidator` | No change. The jakarta tree lists jakarta class names in `librariesToCheck()`. |
| `ConfigurationProvider` | No change. |
| Settings | No new key. |

The deprecate-and-delegate trap from `public-api-design.md` (`AbstractSender.doReconnect`) **does not apply**.
That trap is about the SDK silently ceasing to call a deprecated override. The javax `JmsFactory` keeps being
called in full whenever the javax sender is selected — it is a deprecated path that still runs, not a dead one.

### 5.7 Deprecation set

`@Deprecated(since = "6.1.0")`, no `forRemoval`, each with a `@deprecated` tag pointing at the jakarta equivalent:

- `communication.jms.factory.JmsFactory`
- `communication.jms.javax.JmsSender`, `JmsReceiver`, `SharedJmsReceiver`
- `communication.jms.javax.factory.JndiJmsFactory`, `ActiveMqSslJmsFactory`, `AzureServiceBusJmsFactory`
- `StringUtils.messageToString(javax.jms.Message)`, `StringUtils.headersToString(javax.jms.Message)`
- `JMSChunkAssembly.setHeader(javax.jms.Message, String, String)`

Per `public-api-design.md`, deprecation is never inherited: every public override in the SDK needs its own
`@Deprecated` annotation plus a short pointer tag. Check the generated pages rather than the source.

### 5.8 Public API movements, for the `breaking-change` decision

Not decided here — `njams-ticket-finish` decides against the real diff — but these are the candidates:

- `JmsSender`, `JmsReceiver`, `SharedJmsReceiver` and the three factory implementations change FQCN. Only the
  SDK's own services files reference them, plus `JmsSender.COMMUNICATION_NAME`, a `public static final` constant
  whose FQCN changes with it. Settings that name a factory by `getName()` or simple class name are unaffected;
  only a setting naming one by **fully-qualified** class name would break, and no such FQCN is documented
  anywhere (finding 9).
- `JMSChunkAssembly`'s supertype changes from `GenericChunkAssembly<TextMessage>` to `GenericChunkAssembly<Object>`.
- `JmsReceiver.createMessageFilter()` and the `messageFilter` field are retyped `Predicate<Message>` →
  `Predicate<Object>` (implementation finding, Task 7). This is the one *source-breaking* change in the
  inventory; the rest below are additive.
- Hoisting onto the `AbstractJmsSender`/`AbstractJmsReceiver` bases widened these from `private` to
  `protected`, i.e. they are new permanent members of a public class's protected surface (Tasks 6–7):
  `closeAll()`, `printExceptionsOnClose(List)`, `topicName`, `splitSupport`, `ancestorPathStrings(Path)`,
  and the sender's `isUseProjectQueue()`. `NJAMS_RECEIVER_PROPERTY` moved to the receiver base but keeps
  resolving through inheritance, so it is not a removal.
- `AbstractJmsSender`/`AbstractJmsReceiver` declare `protected abstract void resolveFactory(ClientSettings)`
  (Tasks 6–8) — a new member of a public class's protected surface, and an abstract one, so any out-of-tree
  subclass of these bases must implement it. Both bases are themselves new in this ticket and documented as
  internal infrastructure, so nothing existing is broken.
- `ServiceLoaderSupport.isNotApplicable(Throwable)` is a **new `public static` method** on an existing public
  class (Task 8) — additive, and the shared classifier §5.4 bullet 3 and §5.5 both use.
- `com.im.njams.sdk.communication.jms.JmsFactory` is a **new registered SPI type** (Task 10, §5.6), shipped with
  its own `META-INF/services/com.im.njams.sdk.communication.jms.JmsFactory` file registering the jakarta
  `JndiJmsFactory`, `ActiveMqSslJmsFactory` and `AzureServiceBusJmsFactory`. Purely additive: it is a distinct
  type from the javax `…jms.factory.JmsFactory`, resolved through a separate services file, so existing
  third-party javax implementations are neither recompiled nor re-resolved. The three jakarta implementations
  are **not** deprecated and reuse the javax `getName()` values (`JNDI`, `ActiveMQSslConnectionFactory`,
  `AzureServiceBusPremium`) — no collision follows, because each tree's `find(...)` only ever loads its own
  services file.
- `JmsReceiver.JMS_TIMESTAMP_PROPERTY` (`protected static final String`) is **removed** — it had no reference
  anywhere in the repo. Removal rather than deprecation was the user's explicit instruction; it is in any case
  weaker than the FQCN re-pointing this section already accepts for the same class.
- Relocating the javax tree (Task 9) forced two visibility widenings on `AbstractJmsSender`, because the
  tests that exercise those members can no longer sit in the base class's package once `JmsSender` lives in
  `…jms.javax`. Both are additive — new members of a public class's protected/public surface, on a base class
  that is itself new in this ticket and documented as internal infrastructure:
  - `serialize(CommonMessage)` widened package-private → `protected`. `JmsSenderTest`'s three anonymous
    `JmsSender` subclasses override it with `@Override`; a package-private method declared in `…jms` is not
    inherited across the package boundary, so the overrides would not compile. Decided by the repository owner.
  - `isCongestion(Throwable)` was briefly widened `protected` → `public` for the same reason and has been
    **reverted** (Task 9 review). `AbstractSender:412` declares it `protected abstract`, so a public override
    widens an SPI Contract template method, can never be narrowed, diverges from `HttpSender`/`KafkaSender`,
    and would force every out-of-tree subclass to declare its own override public. `JmsSenderTest` reaches it
    through a named subclass instead, which may call an inherited protected member from any package (JLS
    6.6.2). **No API change.**

  Consequence worth stating for `serialize`: JSON serialization of a `CommonMessage` is now an overridable
  hook on a public class, adjacent to the Wire Contract. An out-of-tree subclass could change what goes on the
  wire. The base is documented internal infrastructure and this is accepted, but it is a surface that did not
  exist before.

- `communication.jms.JmsFactory` (jakarta) implicitly publishes `public static final org.slf4j.Logger LOG`,
  because interface fields are implicitly public. This is exact parity with the javax interface and breaks no
  rule (slf4j is not relocated), but a brand-new SPI type is the only moment it could have been avoided —
  Java forbids private interface *fields*, so avoiding it would need a package-private holder class and would
  create a javax/jakarta asymmetry against §5.2's near-identical-copy invariant. **Left as-is deliberately;
  flagged here so the `breaking-change` decision sees it rather than discovering it later.**

Current expectation: **yes**, the label applies.

**The three sender/receiver FQCNs are not merely vacated — they are re-pointed.** `communication.jms.JmsSender`,
`JmsReceiver` and `SharedJmsReceiver` keep existing as names, now denoting the `jakarta` classes. Code compiled
against today's `javax` versions still resolves the name and then fails at class load, because the new class
requires `jakarta.jms`. That is a loud failure, not a silent one, but it is a different failure than a clean
"class not found".

This was weighed and accepted. `JmsReceiver` does expose a broad `protected` surface (`session`, `consumer`,
`topic`, `messageFilter`, `createConsumer(Session, Topic)`, `getInstruction(Message)`,
`reply(Destination, …)`), so an external subclass is technically possible — but `communication-layer.md` places
everything in this package outside `AbstractSender`/`AbstractReceiver`/`JmsFactory` in the **Internal** bucket,
so subclassing `JmsReceiver` was never a supported extension point. The deliberate asymmetry with
`…jms.factory.JmsFactory` (§5.1) is that the factory interface *is* SPI Contract and its breakage would be
silent; this one is Internal and loud.

The one genuinely silent degradation: a settings value naming a relocated factory by **fully-qualified** class
name no longer matches (`JmsFactory.find` compares `getClass().getName()`), so the lookup falls through to
`JndiJmsFactory` with only a warning. Matching by `getName()` or simple class name still works, and no FQCN is
documented anywhere (finding 9). Call this out in the FAQ entry.

### 5.9 Wire Contract gate

`communication/fragments/` is Wire Contract under `message-format-changes.md`, and this design changes
`JMSChunkAssembly`. The emitted headers and chunking behaviour are **byte-identical** — only the Java type of the
parameter changes — and the result is one implementation of the chunk format instead of two. The user has signed
off that this is in scope **without** an `SER` ticket. Recorded here so the gate is not re-opened in review.

### 5.10 Build

- No new dependency, no new build plugin.
- **Both namespaces come from two dependencies with different coordinates, on one classpath.** The jakarta tree
  is hand-written, so `javac` needs the real `jakarta.jms` 3.x API; the javax tree needs the `javax.jms` one.
  Today both would come from `jakarta.jms:jakarta.jms-api` (2.0.3 ships `javax.jms`, 3.x ships `jakarta.jms` —
  finding 8), and Maven resolves one version per coordinate. The way out is simply not to use that coordinate
  twice:

  | Dependency | Package it provides | Scope |
  |---|---|---|
  | `javax.jms:javax.jms-api` | `javax.jms` | `provided` |
  | `jakarta.jms:jakarta.jms-api` 3.x | `jakarta.jms` | `provided` |

  No coordinate collision, both packages visible, and `javac` compiles both trees in **one pass on one
  classpath**. No separate source roots, no extra module, no profile-driven swap — which also means §8.1's
  checkstyle / javadoc / sources-jar gates are satisfied without special arrangements, since there is only ever
  one classpath.

  This works for the main compile because `njams-sdk` has **no ActiveMQ on it**: `activemq-client` and
  `activemq-broker` are `test` scope (`njams-sdk/pom.xml:460-469`), so the only JMS API on the compile classpath
  is the explicit `provided` one.
- **On the test classpath, exclude ActiveMQ's transitive JMS API.** ActiveMQ is genuinely needed there and is
  not vestigial: `activemq-broker` backs `EmbeddedActiveMqBroker`'s in-process `vm://` broker, and
  `activemq-client` backs `EmbeddedActiveMqJmsFactory`, `ConnectionFactorySupport` and
  `NjamsConnectionFactoryTest` (the last covering `ActiveMqSslJmsFactory`'s reflection path). `activemq-client`
  pulls `jakarta.jms:jakarta.jms-api:2.0.3`, which would collide with the 3.x jakarta API, so exclude it and
  let `javax.jms:javax.jms-api` satisfy ActiveMQ's `javax.jms` references — the same manoeuvre §6.2.1 specifies
  for the mixed module. ActiveMQ 5.19 compiles against JMS 2.0 and `javax.jms:javax.jms-api:2.0.1` is that same
  API, so the substitution is API-equivalent. The parent pom's 2.0.3 pin exists because SDK-467 aligned the
  provided API with what `activemq-client` brings transitively; read SDK-467's actual symptom once while making
  the change, but it is not expected to block (the user assessed it as cosmetic).
- Both APIs stay `provided` and never become transitive, so consumers inherit neither.
- The root pom's **default** `<modules>` list gains one entry, the `njams-sdk-jms-namespace-tests` aggregator
  (§6.2); its three children need no broker and no Docker, so they run on every build. They are not added to
  the `docker-it` profile, and `njams-sdk-communication-it` stays where it is.

## 6. Testing

### 6.1 Unit tests

- Existing JMS unit tests follow the `javax` classes into `…jms.javax` **as-is, with identical assertions** —
  that parity is the proof the move preserved behaviour (`testing-conventions.md`).
- New unit tests for the shared bases, exercising the hoisted logic once rather than twice.
- New unit tests for the shared accessor and its two callers (§5.3): the fallback when `getPropertyNames` is
  absent, the body-omitted path when `getText` is absent, that **both negative outcomes are cached** rather than
  re-probed, and that a package-private implementation class is handled without `IllegalAccessException`.
- New unit tests for the §5.4 selection algorithm: the candidate loop, fall-through on the marker exception only,
  a **hard error** when step 2 fails, the combined error when no candidate works, and the memoization in §5.4.1.
  These need no JMS API at all — stubbed candidates and factories suffice — so they live in `njams-sdk`.
- New unit tests for `ServiceLoaderSupport`'s two buckets (§5.5): a provider failing to load on a missing
  `javax.jms`/`jakarta.jms` type lands in "not applicable" and leaves `hasUnavailable()` **false**; a provider
  failing for any other reason lands in "genuinely unavailable" and makes it **true**; and
  `describeAvailability` reports both buckets, labelled, so the neither-API-present message still names the
  missing API.
- **No jakarta mirrors of the JMS unit tests.** The suite is not duplicated — it migrates wholesale in §6.5, so
  mirroring now would produce tests that are thrown away. Until then the jakarta subclasses have only the
  classloading-and-selection coverage of §6.2/§6.2.1, which is the gap §6.3 records. Confirmed with the user on
  2026-10-28.
- Existing test cases must not be modified without explicit user permission.

### 6.2 Three namespace modules — no broker, no Docker, default build

The purpose of these modules is **classloading and selection**, nothing else: on a given classpath, does the SDK
hand back a working sender and receiver, and are they the right implementation type? Two cover a single
namespace each; the third (§6.2.1) covers a genuinely mixed classpath.

They live under one aggregator, so the root pom gains a single `<module>` entry and the three share their build
setup in one place:

```
njams-sdk-jms-namespace-tests/            aggregator, packaging=pom, parent = root pom
├── njams-sdk-jms-javax-test/
├── njams-sdk-jms-jakarta-test/
└── njams-sdk-jms-mixed-test/
```

Directory names match artifact IDs, as everywhere else in this repo. The aggregator carries what all three share
— the `njams-sdk` dependency, the JUnit/Mockito test stack, surefire configuration — leaving each child to
declare only its own JMS API and broker client, which is the one thing that must differ. `dependencyManagement`
still reaches the children through the parent chain.

**None of these modules is published.** They are test harnesses, so they set `maven.deploy.skip=true` the way the
sample modules already do.

| Module | Classpath | Asserts |
|---|---|---|
| `njams-sdk-jms-javax-test` | javax-namespace JMS API + `activemq-client` | `CommunicationFactory` returns a sender **and** a receiver; each is the expected concrete class (`…communication.jms.javax.JmsSender` / `JmsReceiver`, asserted by FQCN since both trees share the simple names); the jakarta tree was skipped rather than fatal, and `describeAvailability` names it |
| `njams-sdk-jms-jakarta-test` | `jakarta.jms` 3.x API + `activemq-client-jakarta` | the same, mirrored against `…communication.jms.JmsSender` / `JmsReceiver` |

**No broker is required, and no part of the test may connect.** Three facts make that work:

1. `CommunicationFactory.getSender()` instantiates, calls `validate()`, and calls `init(settings)`. `connect()`
   is separate and is driven later by `SenderPool`/`SenderConnector`. The receiver is the same — `init` computes
   the topic name and mapper, while `tryToConnect` lives in `connect()`.
2. The §5.4 discriminator is `InitialContext` + `lookup(...)` + the `(ConnectionFactory)` cast. With ActiveMQ's
   client-side JNDI provider (`ActiveMQInitialContextFactory`) the lookup synthesizes a `ConnectionFactory` from
   properties and returns it; a `ConnectionFactory` is inert until `createConnection()` is called. No socket is
   opened.
3. Therefore the tests drive `CommunicationFactory` directly. They must **not** call `Njams.start()`, which does
   connect.

Consequences:

- These are plain surefire modules in the default `<modules>` list, not failsafe ITs in the `docker-it` profile.
  They run on every build, which is materially better regression protection than a manual profile.
- Each still needs the **real** broker client on its classpath, because the cast needs a genuine
  `ConnectionFactory` of the right namespace. A hand-written stub would also work; the real client is more
  faithful and costs nothing.
- Giving each module its own classpath sidesteps the `jakarta.jms:jakarta.jms-api` 2.0.3-vs-3.x same-`groupId:artifactId`
  clash on the **test** side. The compile-side question in §8 remains.

**The neither-API-present case** belongs here too: the SDK starts, the JMS transport reports itself unavailable
with a message that names the missing API, and nothing escapes. It needs no broker and no client, so it can live
in `njams-sdk`'s own suite if a classpath without either API can be arranged there; otherwise it is a third tiny
module. Builder's call.

### 6.2.1 A third module for the mixed classpath

The §6.1 unit tests cover the candidate loop's *branches* with stubs. They do not prove that selection works on a
real mixed classpath, which is the configuration §2 promises to support. A third module does:

**`njams-sdk-jms-mixed-test`** — both APIs and **two different broker products** present at once:

| On the classpath | Namespace |
|---|---|
| `javax.jms:javax.jms-api` + `org.apache.activemq:activemq-client` (AMQ Classic) | javax |
| `jakarta.jms:jakarta.jms-api` 3.x + `org.apache.activemq:artemis-jakarta-client` (Artemis) | jakarta |

This classpath is only buildable because the two products' classes do not collide — unlike `activemq-client` and
`activemq-client-jakarta`, which do. Two assembly details matter:

- The javax API must come from `javax.jms:javax.jms-api`, **not** `jakarta.jms:jakarta.jms-api:2.0.3`. The latter
  shares `groupId:artifactId` with the 3.x jakarta API and so cannot coexist with it (finding 8).
- `activemq-client` pulls `jakarta.jms:jakarta.jms-api:2.0.3` transitively, so that transitive dependency must be
  excluded.

Each product also has its own JNDI initial-context factory —
`org.apache.activemq.jndi.ActiveMQInitialContextFactory` for Classic and
`org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory` for Artemis — so the settings alone decide which
`ConnectionFactory` type the lookup yields, and therefore which tree's cast succeeds.

Assertions, all broker-less and without connecting:

1. Settings pointing at the Classic context factory select `…communication.jms.javax.JmsSender` / `JmsReceiver`.
2. Settings pointing at the Artemis jakarta context factory select `…communication.jms.JmsSender` / `JmsReceiver`.
3. **Both at once in one JVM** — two `CommunicationFactory` instances with different settings — each resolve to
   their own tree. This is the configuration from §2 (SDK on jakarta Artemis, routes on javax AMQ Classic) and it
   is the direct proof of §5.4.1's per-settings resolution; a JVM-global switch could not pass this test.
4. Settings that resolve in **neither** tree produce one hard error naming every cause, not a fall-through.

### 6.3 What is deliberately not covered, and what carries it instead

The ticket's acceptance criterion *"with only `jakarta.jms` and a jakarta broker client present, send and receive
work equivalently, including chunked messages"* has **no automated coverage in this repository**. That is an
accepted, deliberate gap, not an oversight. What stands in for it:

- The jakarta subclasses are faithful hand-copies of javax code that keeps its full coverage, and all
  namespace-free logic is shared, not copied (§5.2). That coverage is more than unit tests: `njams-sdk`'s own
  suite runs real end-to-end JMS tests against an **in-process** ActiveMQ broker — `EmbeddedActiveMqBroker`
  starts a non-persistent broker on `vm://` (with stop/start to simulate outages), driving
  `JmsSenderBaselineIT`, `JmsClientEndToEndBaselineIT` and `HarnessSmokeIT`. These run in the normal build:
  failsafe is declared in the parent pom's `<build><plugins>`, so it is active for every module.
  `njams-sdk-communication-it` adds fault-injection scenarios on top of that.
- The Camel showcase (NJAC-56) exercises the jakarta path end to end against a real broker.
- §6.5 closes the gap properly, later.

### 6.4 Existing suites stay on javax, for now

`njams-sdk`'s own JMS unit tests and `njams-sdk-communication-it`'s resilience suite continue to run against the
`javax` tree — which this change makes the deprecated one. Most of what they exercise now lives in the shared
bases, so the common path stays covered either way; only the jakarta subclasses' own connect/close plumbing is
untested by them.

One mechanical fix is required:
`njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/JndiJmsFactorySmokeIT.java` imports
`…communication.jms.factory.JndiJmsFactory`, which relocates to `…communication.jms.javax.factory`. The module's
classpath and scenario catalog are otherwise unchanged — `communication-it-module.md` makes that catalog stable
by default.

### 6.5 Postponed: migrate the test suites from javax to jakarta

Once this ticket ships, the SDK's test suites will be exercising the **deprecated** tree while the primary one
gets only classloading-level coverage. The correct end state is the reverse: `njams-sdk`'s JMS unit tests and
`njams-sdk-communication-it`'s resilience suite move to `jakarta`, testing the current and future variant. That
also closes §6.3's gap, since the resilience suite already covers send, receive and chunked messages.

This is deliberately **not** part of the implementation described here, but it **is** part of SDK-495.

> **Scope note.** Under `jira-workflow.md`'s test — "would this still need fixing if the current ticket did not
> exist?", and the answer is no, since it exists only because this ticket deprecates the javax tree — the
> migration is reframed sub-scope of SDK-495, not a separate ticket. It therefore gets its own spec and plan
> under `docs/superpowers/`, keeps referencing `SDK-495` on its commits, and **SDK-495 is not complete, and must
> not be resolved, until it is done.** Confirmed with the user on 2026-10-28.

**It will not be mechanical, and the obstacle is known now.** `njams-sdk`'s JMS end-to-end tests run against
`EmbeddedActiveMqBroker`, which uses `activemq-broker`'s `BrokerService` on the `vm://` transport — and
`activemq-broker` is javax-only. `activemq-client-jakarta` is client-only: no embedded broker, no `vm://`
(finding 7), and ActiveMQ's jakarta broker is 6.x, which needs Java 17. So those tests cannot simply be pointed
at the jakarta tree. The migration will need either a jakarta-capable embedded broker that runs on the project's
JDK — Artemis embedded (`artemis-server`) is the candidate, and its Java baseline is the same open question as
§8's Artemis item — or a move to Docker, which would also pull `njams-sdk`'s JMS end-to-end tests out of the
default build. Settle that when the migration is specced, not before.

When that phase is planned, note its other consequence: migrating the suite *instead of* duplicating it leaves the
`javax` tree without unit or resilience coverage, while it remains the configuration every current production
deployment runs. What continues to guard it is §6.2's javax namespace module and the fact that the `javax` tree
is a frozen hand-copy nobody is expected to change. Decide then whether that is enough.

Moving `njams-sdk-communication-it` to jakarta additionally requires updating
`docs/superpowers/specs/2026-09-28-sdk-483-communication-it-module-design.md` first, per
`communication-it-module.md`.

### 6.6 The regression that matters most

The javax path, behaving exactly as today, is the one that must not regress, and the one most likely to be
under-tested because it is "the existing behaviour". JMS is the primary production transport
(`communication-layer.md`), so this change carries the highest scrutiny tier in the project.

## 7. Documentation

- `wiki/FAQ.md`: a new entry explaining that the SDK serves both namespaces from one artifact, which one is
  chosen and how, that `javax` is the legacy path, and what a client on Camel 4 / Spring 6 / Quarkus 3 needs on
  its classpath. No setting changed, so `settings_full.properties` needs no update.
- `.claude/rules/public-api-design.md`: add the jakarta `JmsFactory` to the registered-SPI list.
- `.claude/rules/communication-layer.md`: note the two-tree arrangement and the non-unique provider names.
- `CLAUDE.md`'s project structure section: the `njams-sdk-jms-namespace-tests` aggregator and its three children
  (§6.2, §6.2.1).
- Javadoc: the base classes' "not an extension point" note, the frozen `JmsFactory`'s "must never move" note, the
  `AbstractSender`/`AbstractReceiver` uniqueness contract, and the whole deprecation set.

## 8. Left to the builder

- The exact member split between each base class and its two subclasses, and the names of the bases and of their
  abstract hooks.
- **Confirm the dependency swap in §5.10 behaves**, as the first build task, before any tree work: replace the
  single `jakarta.jms:jakarta.jms-api:2.0.3` with `javax.jms:javax.jms-api` plus `jakarta.jms:jakarta.jms-api`
  3.x, exclude ActiveMQ's transitive JMS API on the test classpath, and verify that the existing suite,
  `mvn validate -Pcheckstyle -pl njams-sdk`, an error-free `mvn javadoc:javadoc -pl njams-sdk`, and the
  `createSourcesJar` output are all still fine. All four are expected to pass unchanged — the point of the
  one-classpath approach is that nothing special is needed — so if any of them does **not**, stop and raise it
  rather than improvising a separate source root or profile.
- **The Azure jakarta class FQCN.** The jakarta variant is a different artifact —
  `com.azure:azure-servicebus-jms:2.x` versus `com.microsoft.azure:azure-servicebus-jms:1.0.x`. Microsoft states
  the `ServiceBusJmsConnectionFactory` API and configuration are unchanged, but it has **not** been confirmed
  that the class still sits at `com.microsoft.azure.servicebus.jms.ServiceBusJmsConnectionFactory` under the new
  groupId. Since the SDK addresses it reflectively by string, that string is the only thing at stake — a one-line
  difference in the jakarta copy. Verify against the actual artifact before coding it.
- The shared accessor's name and exact API shape (§5.3) — the placement, the `ClassValue` cache and the probe
  semantics are specified; the rest is yours.
- How `SaveIterator` recognises a missing `javax.jms`/`jakarta.jms` type in a load failure's root cause, and the
  shape of `ServiceLoaderSupport`'s two buckets and its labelled `describeAvailability` output (§5.5).
- Whether the neither-API-present test is a third module or fits in `njams-sdk`'s suite (§6.2).
- The exact type and placement of the §5.4 marker exception, and the memo field's shape on `CommunicationFactory`.
- **Two facts about `artemis-jakarta-client` to confirm before building §6.2.1**, neither of which could be
  settled from the Artemis documentation: that it really exposes
  `org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory` under the `jakarta.jms` namespace (the docs
  show that class only in a JMS 2.0 context), and what the chosen version's Java baseline is. See §8.1 — the
  answer decides which of two paths the build takes.
- How `ServiceLoaderSupport` best exposes "all matches in order" for `findSenderImplementation`/`findReceiverType`
  — `stream()` and `getAll()` already exist, so this is a choice of shape, not of mechanism.

### 8.1 The build JDK — stay on 11 if at all possible

The SDK's **compile target stays Java 11 under every option below.** That is not negotiable; what follows is
only about the JDK the build *runs on*.

Jenkins currently builds the whole reactor on JDK 11 (`Jenkinsfile:14` pins `jdk 'openJDK-11.0.20'`, and the
build stage is `mvn clean install -Pjenkins-cli`). So any module in the default `<modules>` list that needs a
newer JDK fails CI — not because the SDK requires Java 11, but because the build host runs it.

**Preferred: keep everything on Java 11.** Choose an `artemis-jakarta-client` version whose baseline is 11, or
substitute any other jakarta-namespace client whose classes do not collide with AMQ Classic's — §6.2.1 needs
*two different products*, not Artemis specifically. `activemq-client-jakarta` 5.19 is on a Java 11 line, so the
single-namespace jakarta module (§6.2) is expected to be fine either way.

**Fallback, only if no such pairing exists:** `openJDK-25` is available on Jenkins and may be used for the
build, with the compile target still 11. Three conditions come with it, and they are part of the work, not
afterthoughts:

1. **Switch `<source>`/`<target>` to `<release>`.** `pom.xml:107-108` currently sets `<source>${javaSourceLevel}</source>`
   and `<target>…</target>`. Compiling on JDK 25 with those links against JDK 25's `java.*` APIs, so code can
   compile cleanly and then fail at runtime on Java 11 — the guarantee becomes nominal. `<release>11</release>`
   is what actually enforces it.
2. **Update the `Jenkinsfile`** and say so in `CLAUDE.md`'s requirements line, which currently reads "Java 11+".
3. **Do the JDK move as its own first task**, verifying the existing suite still passes before any jakarta work
   starts, so unrelated JDK-25 breakage is visible on its own rather than tangled with the namespace change.
   The risk looks low — Mockito 5.23.0, JUnit 4.13.2 and Failsafe 3.5.6 are all current — but it is a
   whole-build change and should not be discovered mid-ticket.

Confirmed with the user on 2026-10-28: prefer the Java 11 pairing; take the JDK 25 fallback only if none exists.

## 9. Out of scope

- Kafka and HTTP transports.
- Any wire-format or protocol change.
- Migrating existing javax clients.
- Producing an OSGi bundle.
- Removing the javax tree. It is deprecated, not removed, and `forRemoval` is deliberately not set.
- **Holding the resolved `JmsFactory` for the sender's lifetime.** `JmsSender.connect()` currently resolves and
  closes a factory on every connect *and* every reconnect; holding one instead would remove a JNDI lookup per
  reconnect. It is a real improvement and the namespace resolution would get it for free — but it changes factory
  lifecycle on the primary production transport, so it belongs in its own `njams-safe-modification` with its own
  justification, not folded in here. §5.4 deliberately specifies resolve-and-discard.

## 10. Origin

Raised from NJAC-56 (nJAMS Client for Apache Camel, second showcase evolution). With SDK-495 done, the Camel
client and the SDK share one Jakarta ActiveMQ client against one broker, and the blocker disappears.
