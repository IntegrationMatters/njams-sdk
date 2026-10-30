# SDK-495 jakarta.jms Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship one `njams-sdk` artifact whose JMS transport works on `javax.jms` or `jakarta.jms`, chosen at runtime from the configured connection factory, with no change required of existing `javax` consumers.

**Architecture:** Namespace-free logic is hoisted into shared base classes in `communication/jms/`; everything touching a JMS type is hand-copied once per namespace. The `jakarta` copy takes the common package and the `javax` copy moves to `communication/jms/javax/` and is deprecated. Both register as `"JMS"`; `CommunicationFactory` tries candidates in services-file order and each candidate's `init` resolves its own `JmsFactory`, declining via a marker exception when the namespace does not match. Two JMS-touching helpers (`StringUtils` debug dumpers, `JMSChunkAssembly`) are not duplicated — they become `Object`-typed over one shared reflective accessor.

**Tech Stack:** Java 11, Maven, JUnit 4, Mockito, SLF4J, ActiveMQ 5.19 (test only).

**Spec:** `docs/superpowers/specs/2026-10-28-sdk-495-jakarta-jms-support-design.md` — read it alongside this plan; every task argues from it.

## Global Constraints

- **Compile target stays Java 11** (`javaSourceLevel=11`). Never raise it.
- **Both JMS APIs stay `provided`** and must never become transitive for consumers.
- **No new third-party library and no new build plugin.** If one seems necessary, stop and ask.
- **`com.im.njams.sdk.communication.jms.factory.JmsFactory` must never move or change its signatures.** Third-party `javax` implementations ship a `META-INF/services` file naming it; relocating it makes their factory silently disappear.
- **Every new production source file carries the copyright header** from `.claude/rules/code-quality-general.md`. Not required in test code or the new test modules.
- **All `public`/`protected` members need Javadoc.** `mvn validate -Pcheckstyle -pl njams-sdk` and an error-free `mvn javadoc:javadoc -pl njams-sdk` are commit gates.
- **Deprecations use `@Deprecated(since = "6.1.0")` with no `forRemoval`**, plus a `@deprecated` tag naming the jakarta equivalent. Deprecation is not inherited — every public override needs its own annotation and a short pointer tag.
- **Existing test cases must not be modified** without explicit user permission. Moving a test file with identical assertions is not a modification.
- **Commit messages:** `SDK-495 <one sentence>`. No `#comment` token on these intermediate commits. End each commit message with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- **Do not run `mvn install`** for intermediate checks; use `compile`, `test-compile`, `test`, `validate`, `javadoc`.

## Review Focus

Five failure modes the spec implies that no task would otherwise exercise. Each has a test pinned to the task that owns the code.

1. **A provider's `getPropertyNames()`/`getText()` throwing `JMSException` now arrives wrapped in `InvocationTargetException`.** The old direct calls caught `JMSException`; reflection changes the exception's shape, so an unaware `catch (JMSException)` no longer fires and the throwable escapes into the command-receive path. Pinned to Task 2.
2. **`CommunicationFactory`'s memo is written from several threads.** `SenderPool.create()` is called by up to `maxSenderThreads` workers and `SenderConnector` adds its startup/reconnect path on the same instance, so an unsynchronised memo field can be torn or resolved twice. Pinned to Task 5.
3. ~~**A third-party sender whose `init` fails for an unrelated reason must not be silently replaced.** Falling through on anything other than the marker type substitutes a different transport implementation, which is nearly invisible in a log. Pinned to Task 5.~~ **Reversed 2026-10-30, on the user's direction.** `findSenderCandidates`/`findReceiverCandidates` filter by `getName()`, so the candidate list never holds a different transport — it holds one implementation per JMS namespace. Aborting on the first failure meant a configuration resolvable in only one namespace never reached the one that works. `selectApplicable` now skips any failing candidate; only all of them failing is an error, and that error still names every cause. `CommunicationFactoryCandidateTest.aCandidateFailingForAnyOtherReason_isAHardError_andTheNextIsNotTried` was rewritten as `...isSkipped_andTheNextIsTried`.
4. **A provider message class that is package-private but implements a public interface** makes a `Method` resolved on the concrete class fail at `invoke` with `IllegalAccessException`. Pinned to Task 2.
5. **A healthy single-namespace startup must log no warning.** After this change the other tree always fails to load, so `hasUnavailable()` would be permanently true and `JmsFactory.find` would warn on every normal JMS start. Pinned to Task 4.

---

## File Map

**New — shared infrastructure**
- `njams-sdk/src/main/java/com/im/njams/sdk/utils/JmsMessageAccess.java` — the one reflective accessor: probes, caches, unwraps, logs.
- `njams-sdk/src/main/java/com/im/njams/sdk/communication/TransportNotApplicableException.java` — the marker: "this implementation is not applicable on this classpath".
- `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/AbstractJmsSender.java` — namespace-free sender logic.
- `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/AbstractJmsReceiver.java` — namespace-free receiver logic.

**New — jakarta tree** (`njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/`)
- `JmsFactory.java` (SPI), `JndiJmsFactory.java`, `ActiveMqSslJmsFactory.java`, `AzureServiceBusJmsFactory.java`, `JmsSender.java`, `JmsReceiver.java`, `SharedJmsReceiver.java`.

**Relocated — javax tree**
- `communication/jms/{JmsSender,JmsReceiver,SharedJmsReceiver}.java` → `communication/jms/javax/`.
- `communication/jms/factory/{JndiJmsFactory,ActiveMqSslJmsFactory,AzureServiceBusJmsFactory}.java` → `communication/jms/javax/factory/`.
- `communication/jms/factory/JmsFactory.java` — **stays**, deprecated, sole occupant of its package.

**Modified**
- `utils/StringUtils.java`, `communication/fragments/JMSChunkAssembly.java`, `utils/ServiceLoaderSupport.java`, `communication/CommunicationFactory.java`, `communication/AbstractSender.java` + `AbstractReceiver.java` (Javadoc only), `NjamsSettings.java` (Javadoc links), `pom.xml`, `njams-sdk/pom.xml`.
- `njams-sdk/src/main/resources/META-INF/services/{…AbstractSender,…AbstractReceiver}` and a new `…communication.jms.JmsFactory`.

**Test tree — mirrored per namespace** (Task 12; `njams-sdk/src/test/java/com/im/njams/sdk/`)
- Moved into `communication/jms/javax/`: `WorkingJmsFactory`, `NoopJmsFactory`, `FailingJmsFactory`, `JmsFactoryResolutionTest`.
- New jakarta twins in `communication/jms/`: `JmsSenderTest`, `JmsReceiverTest`, `SharedJmsReceiverTest`, `JmsReceiverMock`, `JmsFactoryTest`, `JmsFactoryUnavailableLoggingTest`, `JndiJmsFactoryTest`, `ActiveMqSslJmsFactoryTest`, `AzureServiceBusJmsFactoryTest`, `JmsFactoryResolutionTest`, `WorkingJmsFactory`, `NoopJmsFactory`, `FailingJmsFactory`.
- New `njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.jms.JmsFactory`.

**New modules**
- `njams-sdk-jms-namespace-tests/` (aggregator) with `njams-sdk-jms-javax-test/`, `njams-sdk-jms-jakarta-test/`, `njams-sdk-jms-mixed-test/`.

---

## Tasks

### Task 1: Put both JMS APIs on one compile classpath

**Files:**
- Modify: `pom.xml` (properties and `dependencyManagement`)
- Modify: `njams-sdk/pom.xml` (dependencies, ActiveMQ exclusions)

**Interfaces:**
- Consumes: nothing.
- Produces: `javax.jms.*` and `jakarta.jms.*` both resolvable at compile and test scope in `njams-sdk`.

- [ ] **Step 1: Read SDK-467 in Jira** and record in the commit message whether its symptom is affected by replacing `jakarta.jms:jakarta.jms-api:2.0.3` with `javax.jms:javax.jms-api`. The user assessed it as cosmetic; if the ticket says otherwise, stop and raise it.

- [ ] **Step 2: Replace the single JMS API dependency with two**

In `pom.xml` `dependencyManagement`, keep `jakarta.jms:jakarta.jms-api` but move the version to 3.1.0, and add `javax.jms:javax.jms-api:2.0.1`. In `njams-sdk/pom.xml`, declare both with `<scope>provided</scope>`.

- [ ] **Step 3: Exclude ActiveMQ's transitive JMS API**

Add an `<exclusions>` block for `jakarta.jms:jakarta.jms-api` to both `activemq-client` and `activemq-broker` in `njams-sdk/pom.xml:460-469`. ActiveMQ 5.19 compiles against JMS 2.0, which `javax.jms:javax.jms-api:2.0.1` provides.

- [ ] **Step 4: Verify all four gates still pass**

Run: `mvn test -pl njams-sdk`
Expected: PASS, same test count as before the change.
Run: `mvn validate -Pcheckstyle -pl njams-sdk`
Expected: PASS.
Run: `rm -rf njams-sdk/target/reports/apidocs && mvn javadoc:javadoc -pl njams-sdk`
Expected: BUILD SUCCESS, no errors.
Run: `mvn dependency:tree -pl njams-sdk | grep jms`
Expected: exactly one `javax.jms:javax.jms-api` and one `jakarta.jms:jakarta.jms-api:3.1.0`, both `provided`, no other JMS API.

If any gate fails, **stop and raise it** rather than introducing a separate source root, module, or profile — the whole point of this approach is that nothing special is needed.

- [ ] **Step 5: Commit**

```bash
git add pom.xml njams-sdk/pom.xml
git commit -m "SDK-495 compile against both the javax.jms and jakarta.jms APIs"
```

---

### Task 2: `JmsMessageAccess` — one reflective accessor with a negative-caching probe

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/utils/JmsMessageAccess.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/utils/JmsMessageAccessTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces, all `public static` on a `final` class with a private constructor:
  - `boolean isMessage(Object msg)` — true when `getPropertyNames()` was found.
  - `boolean isTextMessage(Object msg)` — true when `getText()` was found.
  - `Collection<String> propertyNames(Object msg)` — never null; empty on any failure.
  - `Object objectProperty(Object msg, String key)` — null on any failure.
  - `String text(Object msg)` — null when absent or on any failure.
  - `void setStringProperty(Object msg, String key, String value)` — logs and returns on any failure.

- [ ] **Step 1: Write the failing tests**

```java
// Fixtures in the test file: a public FakeTextMessage with getPropertyNames/getObjectProperty/
// getText/setStringProperty; a FakeNonTextMessage without getText; a PlainObject with none;
// a public interface FakeMessageApi implemented by a package-private FakeHiddenMessage;
// and a FakeThrowingMessage whose getPropertyNames throws a checked exception.

@Test public void isMessage_isTrueForSomethingWithPropertyNames()
@Test public void isMessage_isFalseForAPlainObject()
@Test public void isTextMessage_isFalseWhenGetTextIsAbsent()
@Test public void propertyNames_returnsAllKeys()
@Test public void objectProperty_returnsTheValue()
@Test public void text_returnsTheBody()
@Test public void setStringProperty_setsTheValue()
@Test public void text_returnsNullForANonTextMessage()
@Test public void propertyNames_returnsEmptyWhenTheProviderThrows()   // Review Focus 1
@Test public void propertyNames_worksOnAPackagePrivateImplementation() // Review Focus 4
@Test public void probingIsDoneOncePerClass_evenWhenAccessorsAreAbsent() // negative caching
```

For the negative-caching test, count probe attempts with a fixture class whose accessor lookup is observable — e.g. assert that two calls to `isTextMessage(nonTextMessage)` leave the `ClassValue`-derived descriptor identical (`assertSame`) rather than re-resolved.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=JmsMessageAccessTest -pl njams-sdk`
Expected: FAIL — `JmsMessageAccess` does not exist.

- [ ] **Step 3: Implement `JmsMessageAccess`**

A private immutable descriptor holding the four `Method`s (null when absent), produced by a `private static final ClassValue<Descriptor>` whose `computeValue` resolves **all four eagerly, once**, so an absent accessor is recorded rather than re-probed. Resolve each against the first interface in the type's transitive interface set that declares it, falling back to the concrete class plus `setAccessible(true)`. Every invocation site catches `InvocationTargetException`, logs its `getCause()`, and returns the documented fallback; `ReflectiveOperationException` is handled the same way.

`ClassValue`, not a static `Map<Class<?>, …>` — a static map keyed by `Class` pins the classloader across an application-server redeploy.

Javadoc must state the class is internal SDK infrastructure, `public` only because `communication/fragments/` needs it, and not an extension point.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=JmsMessageAccessTest -pl njams-sdk`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/utils/JmsMessageAccess.java njams-sdk/src/test/java/com/im/njams/sdk/utils/JmsMessageAccessTest.java
git commit -m "SDK-495 add a namespace-free reflective accessor for JMS messages"
```

---

### Task 3: `StringUtils` and `JMSChunkAssembly` become `Object`-typed

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/utils/StringUtils.java:115-183`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/fragments/JMSChunkAssembly.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/utils/StringUtilsJmsTest.java` (new)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/fragments/JMSChunkAssemblyTest.java` (existing — do not modify; it must still pass)

**Interfaces:**
- Consumes: `JmsMessageAccess` (Task 2).
- Produces:
  - `StringUtils.messageToString(Object)` / `headersToString(Object)` — the live forms.
  - `StringUtils.messageToString(javax.jms.Message)` / `headersToString(javax.jms.Message)` — `@Deprecated`, delegating.
  - `JMSChunkAssembly extends GenericChunkAssembly<Object>`, constructed as `super(null, JMSChunkAssembly::getHeaders, JMSChunkAssembly::getText, false)` with both now `Function<Object, …>`.
  - `JMSChunkAssembly.setHeader(Object, String, String)` — live; `setHeader(javax.jms.Message, String, String)` — `@Deprecated`, delegating.

- [ ] **Step 1: Write the failing tests**

```java
// StringUtilsJmsTest, reusing Task 2's fixture shapes:
@Test public void messageToString_rendersClassPropertiesAndBody()
@Test public void messageToString_omitsBodyForANonTextMessage()
@Test public void messageToString_fallsBackToValueOfForAPlainObject()
@Test public void messageToString_returnsNullForNull()
@Test public void headersToString_rendersClassAndPropertiesOnly()
@Test public void messageToString_abbreviatesABodyOverOneThousandCharacters()
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=StringUtilsJmsTest -pl njams-sdk`
Expected: FAIL — no `messageToString(Object)` overload.

- [ ] **Step 3: Retype both helpers onto the accessor**

In `StringUtils`, change the parameter of `messageToString`, `headersToString` and the private `appendHeaders` to `Object` and route every JMS call through `JmsMessageAccess`. Branch on `JmsMessageAccess.isMessage(msg)`: a non-message renders as `String.valueOf(msg)`, a message keeps today's rendering. `abbreviate` and the `StringBuilder` assembly are unchanged. Add back `messageToString(javax.jms.Message)` and `headersToString(javax.jms.Message)` as `@Deprecated` one-line delegates.

The old inline `[error: <JMSException>]` marker **does not survive**, and that is accepted. `JmsMessageAccess` returns the documented fallback instead of throwing, so a failed read now renders `key=null` / `body=null`; the accessor logs the provider's real cause with the message class name at WARN, which is more diagnostic than the inline marker was. Restoring the marker would mean extending the accessor contract for a debug string, on a per-message path. A failure-path test pins the new rendering so it stays deliberate.

In `JMSChunkAssembly`, change the supertype to `GenericChunkAssembly<Object>`, retype `getHeaders`/`getText` to take `Object`, and route them through `JmsMessageAccess`. Add the `setHeader(Object, String, String)` form and keep `setHeader(javax.jms.Message, String, String)` as an `@Deprecated` delegate. Drop the `javax.jms` imports that only the removed signatures needed; keep only what the deprecated overloads require.

`JMSChunkAssembly` is Wire Contract, so this signature change falls under `message-format-changes.md`'s confirmation gate. **It is already signed off** (spec §5.9): the headers written, their names, their order and the chunk boundaries are all unchanged — only the Java parameter type moves. No `SER` ticket is needed. Do not widen the change beyond the parameter types without coming back to that gate.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=StringUtilsJmsTest,JMSChunkAssemblyTest -pl njams-sdk`
Expected: PASS. The pre-existing `JMSChunkAssemblyTest` must pass **unmodified** — that is the proof the chunk format is unchanged.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/utils/StringUtils.java njams-sdk/src/main/java/com/im/njams/sdk/communication/fragments/JMSChunkAssembly.java njams-sdk/src/test/java/com/im/njams/sdk/utils/StringUtilsJmsTest.java
git commit -m "SDK-495 make the JMS debug helpers and chunk assembly namespace-free"
```

---

### Task 4: The marker exception and `ServiceLoaderSupport`'s two buckets

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/communication/TransportNotApplicableException.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/utils/ServiceLoaderSupport.java:65-230`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/utils/ServiceLoaderSupportAvailabilityTest.java` (new)

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `TransportNotApplicableException extends RuntimeException` with `(String message)` and `(String message, Throwable cause)`.
  - `ServiceLoaderSupport.hasUnavailable()` — unchanged signature, now reporting **only** genuinely unavailable providers.
  - `ServiceLoaderSupport.describeAvailability(Function<? super S, String>)` — unchanged signature, now reporting both buckets under distinct labels.
  - `ServiceLoaderSupport.findAll(Predicate<S>)` returning `List<S>` in services-file order.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void aProviderMissingAJmsApiTypeIsNotApplicable_andHasUnavailableStaysFalse() // Review Focus 5
@Test public void aProviderFailingForAnyOtherReasonIsUnavailable_andHasUnavailableIsTrue()
@Test public void describeAvailability_namesTheMissingJmsApiEvenWhenNothingIsUnavailable()
@Test public void findAll_returnsEveryMatchInServicesFileOrder()
@Test public void findAll_returnsEmptyWhenNothingMatches()
```

Drive the load failures with the existing deliberately-failing SPI fixture mechanism (see `ScopedSpiRule` and commit 5f2d5b30) so the stack traces stay scoped to this test.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=ServiceLoaderSupportAvailabilityTest -pl njams-sdk`
Expected: FAIL — `findAll` does not exist and `hasUnavailable()` is true for both cases.

- [ ] **Step 3: Implement the split and `findAll`**

Replace the single `unavailable` set with two `Set<String>`: *not applicable* and *genuinely unavailable*. In `addUnavailable(Throwable)`, which already walks to the root cause, classify as *not applicable* when the root cause is a `NoClassDefFoundError` or `ClassNotFoundException` whose message names a type in `javax.jms.` or `jakarta.jms.`; everything else goes to *genuinely unavailable*. `hasUnavailable()` reports the second set only. `describeAvailability` lists both, labelled, so the neither-API-present message still names the missing API.

Add `findAll(Predicate<S>)` alongside the existing `find`, iterating the same `SaveIterator` and collecting matches in order.

Scope note for the Javadoc: the classification covers the two JMS namespaces, which is what the SDK ships; it is not a general extension point.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=ServiceLoaderSupportAvailabilityTest -pl njams-sdk`
Expected: PASS.
Run: `mvn test -pl njams-sdk`
Expected: PASS — the existing suite is unaffected.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/TransportNotApplicableException.java njams-sdk/src/main/java/com/im/njams/sdk/utils/ServiceLoaderSupport.java njams-sdk/src/test/java/com/im/njams/sdk/utils/ServiceLoaderSupportAvailabilityTest.java
git commit -m "SDK-495 distinguish a not-applicable SPI provider from a genuinely unavailable one"
```

---

### Task 5: `CommunicationFactory` tries candidates in order

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/CommunicationFactory.java:82-125` and `:215-285`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/CommunicationFactoryCandidateTest.java` (new)

**Interfaces:**
- Consumes: `TransportNotApplicableException` and `ServiceLoaderSupport.findAll` (Task 4).
- Produces: `getSender()` and `getReceiver(Njams)` unchanged in signature; both now iterate all name matches, and `getSender()` memoizes the winning class on the instance.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void theFirstCandidateWins_whenItInitialisesCleanly()
@Test public void aCandidateThrowingTransportNotApplicable_fallsThroughToTheNext()
@Test public void aCandidateFailingForAnyOtherReason_isAHardError_andTheNextIsNotTried() // Review Focus 3
@Test public void whenEveryCandidateDeclines_theErrorNamesEveryCause()
@Test public void theResolvedSenderIsMemoized_soASecondGetSenderDoesNotReResolve()
@Test public void concurrentGetSenderCalls_resolveExactlyOnce()                          // Review Focus 2
@Test public void whenNoProviderMatchesTheName_theErrorIsUnchangedFromToday()
```

For Review Focus 2, call `getSender()` from `maxSenderThreads` threads released by a `CountDownLatch` and assert a resolution counter reached exactly 1 and every returned sender is of the same class.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=CommunicationFactoryCandidateTest -pl njams-sdk`
Expected: FAIL — the second candidate is never tried.

- [ ] **Step 3: Implement the candidate loop and the memo**

Replace `findSenderImplementation`'s single `find` with `findAll`, and loop: instantiate, `validate()` if it is a `ClasspathValidator`, `init(settings)`. Catch **only** `TransportNotApplicableException` to move to the next candidate, collecting its cause; any other exception propagates as today's `IllegalStateException`. When the list is exhausted, throw one `IllegalStateException` naming every collected cause plus `describeAvailability(...)`.

Apply the same loop in `createReceiver`. Move the `sharedReceivers.put(clazz, receiver)` to **after** a successful `init`, so a declining or failing candidate cannot leave a dead receiver cached.

Memoize the winning sender class in a field guarded so that concurrent `getSender()` calls resolve exactly once; `SenderPool.create()` and `SenderConnector.createSender()` share one `CommunicationFactory` instance. The memo holds the *class*, not an instance — each call still returns a fresh, separately initialised sender.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=CommunicationFactoryCandidateTest -pl njams-sdk`
Expected: PASS.
Run: `mvn test -pl njams-sdk`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/CommunicationFactory.java njams-sdk/src/test/java/com/im/njams/sdk/communication/CommunicationFactoryCandidateTest.java
git commit -m "SDK-495 try every transport candidate matching the communication type, memoizing the winner"
```

---

### Task 6: Hoist the sender's namespace-free logic into `AbstractJmsSender`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/AbstractJmsSender.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/JmsSender.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/jms/JmsSenderTest.java` (existing — must pass unmodified)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/jms/AbstractJmsSenderTest.java` (new)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces — `public abstract class AbstractJmsSender extends AbstractSender`, holding `COMMUNICATION_NAME = "JMS"`, `DEFAULT_DESTINATION = "njams"`, `getName()`, `init`, `serialize`, `sendMessage`/`sendChunks`, `logError`, `isMessageRejected`, the `isCongestion` cause-chain walk and the delivery-mode mapping, with these hooks for subclasses:
  - `protected abstract void resolveFactory(ClientSettings settings);` — called first in `init`; declares `TransportNotApplicableException` by contract (unchecked). Implemented in Task 8.
  - `protected abstract Object buildMessage(String logId, String path, String body, String messageType, String clientSessionId) throws Exception;`
  - `protected abstract void setProperty(Object message, String key, String value);`
  - `protected abstract void tryToSend(Object producer, Object message) throws Exception;`
  - `protected abstract String describeDestination(Object producer);`
  - `protected abstract Class<?> congestionExceptionType();` — the namespace's `ResourceAllocationException`.
  - `protected final int resolveDeliveryMode()` — stays concrete; subclasses need only the int.
  - `protected final String destinationPrefix()` — the `PROPERTY_JMS_DESTINATION`/`…_PREFIX` resolution.

- [ ] **Step 1: Write the failing test**

```java
// AbstractJmsSenderTest drives a minimal test subclass whose hooks record calls,
// with no JMS types involved at all.
@Test public void sendMessage_buildsOneMessageWhenSplittingIsOff()
@Test public void sendChunks_buildsOneMessagePerChunkWithTheSameMessageKey()
@Test public void sendChunks_usesTheLogIdAsMessageKeyForALogMessage()
@Test public void isCongestion_walksTheCauseChainForTheConfiguredExceptionType()
@Test public void isCongestion_returnsFalseOnASelfReferencingCauseChain()
@Test public void destinationPrefix_fallsBackToNjamsWhenUnset()
@Test public void resolveDeliveryMode_mapsNonPersistentReliableAndDefault()
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=AbstractJmsSenderTest -pl njams-sdk`
Expected: FAIL — `AbstractJmsSender` does not exist.

- [ ] **Step 3: Create the base and reduce `JmsSender` to the hooks**

Move the hoisted members from `JmsSender` into `AbstractJmsSender` verbatim, replacing each JMS-typed local with the corresponding hook call. `JmsSender` keeps `connect`, `close`, producer and session creation, `buildMessage`, `setProperty`, `tryToSend`, `onException`, `librariesToCheck`, its `send(LogMessage|ProjectMessage|TraceMessage, …)` overrides (each a one-liner into `sendMessage`), and the hook implementations.

The base's Javadoc must state it is internal SDK infrastructure and not an extension point.

Hoisting `sendMessage`/`sendChunks` costs four hooks and is deliberate: that control flow sets the chunk headers, so duplicating it is the one divergence that would corrupt the wire format.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=AbstractJmsSenderTest,JmsSenderTest -pl njams-sdk`
Expected: PASS. `JmsSenderTest` must pass **unmodified** — that parity is the proof the hoist preserved behaviour.
Run: `mvn test -pl njams-sdk`
Expected: PASS, including `JmsSenderBaselineIT` and `JmsClientEndToEndBaselineIT`.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/ njams-sdk/src/test/java/com/im/njams/sdk/communication/jms/AbstractJmsSenderTest.java
git commit -m "SDK-495 hoist the JMS sender's namespace-free logic into a shared base class"
```

---

### Task 7: Hoist the receiver's namespace-free logic into `AbstractJmsReceiver`

**Files:**
- Create: `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/AbstractJmsReceiver.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/JmsReceiver.java` (`SharedJmsReceiver.java` stays untouched — see Step 3)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/jms/JmsReceiverTest.java` (existing — must pass unmodified)
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/jms/AbstractJmsReceiverTest.java` (new)

**Interfaces:**
- Consumes: `JmsMessageAccess` (Task 2) for the `getText()` probe that replaces `instanceof TextMessage`.
- Produces — `public abstract class AbstractJmsReceiver extends AbstractReceiver`, holding `getName()`, `init`, `createMessageSelector`, `ancestorPathStrings`, `provideNjamsInstances`, `updateFilters`, `stop`, `printExceptionsOnClose`, the fields `topicName`, `messageSelector`, `useMessageselector`, `oldestMessageTime`, `splitSupport`, `mapper`, `chunkAssembly`, and `protected Predicate<Object> messageFilter`, with these hooks:
  - `protected abstract void resolveFactory(ClientSettings settings);` — called first in `init`. Implemented in Task 8.
  - `protected abstract Predicate<Object> createMessageFilter();`
  - `protected abstract List<Exception> closeAll();`
  - `protected final Instruction deserializeInstruction(RawMessage resolved)` — concrete; the half of `getInstruction` after chunk resolution.

- [ ] **Step 1: Write the failing test**

```java
@Test public void createMessageSelector_joinsEveryAncestorPathOfEveryInstance()
@Test public void createMessageSelector_returnsNullWhenSelectorsAreDisabled()
@Test public void createMessageSelector_returnsNullWithNoInstances()
@Test public void init_derivesTheCommandsTopicFromTheDestinationPrefix()
@Test public void init_prefersAnExplicitCommandsDestination()
@Test public void stop_reportsEveryExceptionFromCloseAll()
@Test public void deserializeInstruction_returnsNullForAnInstructionWithoutARequest()
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=AbstractJmsReceiverTest -pl njams-sdk`
Expected: FAIL — `AbstractJmsReceiver` does not exist.

- [ ] **Step 3: Create the base and reduce `JmsReceiver` to the hooks**

Move the hoisted members verbatim. Retype `messageFilter` to `Predicate<Object>` — the field is namespace-free because the lambda that fills it lives in the subclass. In `getInstruction`, replace `message instanceof TextMessage` and the `(TextMessage)` cast with `JmsMessageAccess.isTextMessage(message)` and a direct `chunkAssembly.resolve(message)`.

`SharedJmsReceiver` keeps extending `JmsReceiver` and is otherwise untouched in this task.

The base's Javadoc must state it is internal SDK infrastructure and not an extension point.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=AbstractJmsReceiverTest,JmsReceiverTest -pl njams-sdk`
Expected: PASS, `JmsReceiverTest` unmodified.
Run: `mvn test -pl njams-sdk`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/ njams-sdk/src/test/java/com/im/njams/sdk/communication/jms/AbstractJmsReceiverTest.java
git commit -m "SDK-495 hoist the JMS receiver's namespace-free logic into a shared base class"
```

---

### Task 8: `init` resolves the factory and declines on a namespace mismatch

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/JmsSender.java`, `JmsReceiver.java`, `AbstractJmsSender.java`, `AbstractJmsReceiver.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/jms/JmsFactoryResolutionTest.java` (new)

**Interfaces:**
- Consumes: `TransportNotApplicableException` (Task 4), the `resolveFactory` hooks (Tasks 6 and 7).
- Produces: `JmsSender.init` and `JmsReceiver.init` resolve a `JmsFactory` as their first action and throw `TransportNotApplicableException` on a namespace mismatch.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void init_resolvesAndThenClosesTheFactory()
@Test public void init_throwsTransportNotApplicable_whenTheFactoryCastFails()        // ClassCastException
@Test public void init_throwsTransportNotApplicable_whenTheBrokerClientIsAbsent()    // NoClassDefFoundError
@Test public void init_propagatesAConfigurationFailureUnchanged()                    // e.g. NameNotFoundException
@Test public void init_stillAppliesEverySettingAfterTheFactoryResolves()
```

Drive these with the existing `WorkingJmsFactory` / `FailingJmsFactory` / `NoopJmsFactory` test SPI fixtures.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=JmsFactoryResolutionTest -pl njams-sdk`
Expected: FAIL — `init` does not resolve a factory.

- [ ] **Step 3: Implement `resolveFactory` in both javax classes**

`JmsFactory.find(settings, false)` inside a try-with-resources, then call `init(settings)` on it yourself — resolve and discard; `connect()` keeps resolving its own, so factory lifecycle is unchanged. Going around the static `JmsFactory.initFactory` is deliberate: it closes the factory on the failing path too, and suppresses that method's `LOG.error(..., e)` for a candidate that merely declines. Classify the failure with `ServiceLoaderSupport.isNotApplicable(Throwable)` and rethrow as `TransportNotApplicableException` naming the namespace; re-wrap a checked failure into `IllegalStateException("Could not initialize: " + factory)` as `initFactory` did, and let an unchecked one propagate untouched. See spec §5.4 bullets 2–3 for why the classification is a chain walk with a JMS-API name test rather than a check on the exception type alone.

Call it as the first statement of `AbstractJmsSender.init` and `AbstractJmsReceiver.init`, before `super.init(settings)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=JmsFactoryResolutionTest -pl njams-sdk`
Expected: PASS.
Run: `mvn test -pl njams-sdk`
Expected: PASS, including the embedded-broker ITs.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/ njams-sdk/src/test/java/com/im/njams/sdk/communication/jms/JmsFactoryResolutionTest.java
git commit -m "SDK-495 resolve the JMS factory during init and decline on a namespace mismatch"
```

---

### Task 9: Relocate and deprecate the javax tree

**Files:**
- Move: `communication/jms/{JmsSender,JmsReceiver,SharedJmsReceiver}.java` → `communication/jms/javax/`
- Move: `communication/jms/factory/{JndiJmsFactory,ActiveMqSslJmsFactory,AzureServiceBusJmsFactory}.java` → `communication/jms/javax/factory/`
- Modify: `communication/jms/factory/JmsFactory.java` (deprecate in place, do **not** move)
- Modify: `njams-sdk/src/main/resources/META-INF/services/{…AbstractSender,…AbstractReceiver}`
- Modify: `NjamsSettings.java:31-32,471-482` (Javadoc `{@link}` targets)
- Move: the corresponding test files and SPI fixtures, assertions unchanged
- Modify: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/JndiJmsFactorySmokeIT.java:18-19`

**Interfaces:**
- Consumes: Tasks 6–8.

**Carried in from Task 8 — corrected.** An earlier note here said to hoist `resolveFactory` onto the bases. **Do not.** Its body names `communication.jms.factory.JmsFactory`, whose own public signatures carry five `javax.jms` types, so referencing it from `AbstractJmsSender`/`AbstractJmsReceiver` would put javax descriptors back on the namespace-free bases. The genuinely shareable part — the cause-chain classifier — already moved to `ServiceLoaderSupport.isNotApplicable(Throwable)` in Task 8. What remains duplicated is the ~22-line probe skeleton, which is namespace-*bound*: it names the javax `JmsFactory` and its message says `javax.jms`. Task 11's jakarta copy names the jakarta `JmsFactory` instead. That is the duplication §5.2 already accepts.
- Produces: `com.im.njams.sdk.communication.jms.javax.JmsSender` (carrying `COMMUNICATION_NAME` via the base), `…javax.JmsReceiver`, `…javax.SharedJmsReceiver`, `…javax.factory.{JndiJmsFactory, ActiveMqSslJmsFactory, AzureServiceBusJmsFactory}`; `…jms.factory.JmsFactory` unchanged but deprecated.

- [ ] **Step 1: Move the six classes and their tests, updating only package and import statements**

Assertions must be byte-identical — that parity is the proof the move preserved behaviour.

- [ ] **Step 2: Run the suite to verify the move is behaviour-neutral**

Run: `mvn test -pl njams-sdk`
Expected: PASS, same test count as before the move.

- [ ] **Step 3: Deprecate the javax surface**

`@Deprecated(since = "6.1.0")` with a `@deprecated` tag pointing at the jakarta equivalent on all six relocated classes and on `communication/jms/factory/JmsFactory`. Deprecation is not inherited, so every public override in these classes needs its own annotation plus a short pointer tag.

`JmsFactory`'s Javadoc additionally states that the type can never be relocated, because third-party implementations ship `META-INF/services/com.im.njams.sdk.communication.jms.factory.JmsFactory` and would silently disappear.

Update the `{@link}` targets in `NjamsSettings` and fix `JndiJmsFactorySmokeIT`'s imports.

- [ ] **Step 4: Verify the gates**

Run: `mvn test -pl njams-sdk`
Expected: PASS.
Run: `mvn validate -Pcheckstyle -pl njams-sdk`
Expected: PASS.
Run: `rm -rf njams-sdk/target/reports/apidocs && mvn javadoc:javadoc -pl njams-sdk`
Expected: BUILD SUCCESS. Then open the generated page for `JmsSender` and confirm it renders as deprecated — the source alone does not prove it.
Run: `mvn test-compile -pl njams-sdk-communication-it -am -Pdocker-it`
Expected: BUILD SUCCESS. The `-am` is required: without it the module resolves the *installed* SNAPSHOT, which still holds the pre-move classes, and the check passes for the wrong reason. Clear `target/test-classes` first — a warm incremental build also reports SUCCESS falsely.

**Task 1 left this module behind.** It added the explicit `javax.jms-api` and the `jakarta.jms-api` exclusions to `njams-sdk/pom.xml` only, while raising the managed `jakarta.jms-api` to 3.1.0. This module declared no JMS API of its own and had been getting `javax.jms` transitively from `activemq-client`'s 2.0.3, so it stopped compiling at Task 1 and nothing noticed until here. A test-scoped `javax.jms:javax.jms-api` is added to its pom as part of this task.

- [ ] **Step 5: Commit**

```bash
git add -A njams-sdk/src njams-sdk-communication-it/src
git commit -m "SDK-495 move the javax JMS implementation into its own package and deprecate it"
```

---

### Task 10: The jakarta `JmsFactory` SPI and its three implementations

**Files:**
- Create: `communication/jms/{JmsFactory,JndiJmsFactory,ActiveMqSslJmsFactory,AzureServiceBusJmsFactory}.java`
- Create: `njams-sdk/src/main/resources/META-INF/services/com.im.njams.sdk.communication.jms.JmsFactory`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `com.im.njams.sdk.communication.jms.JmsFactory` — the same members as the javax interface but typed on `jakarta.jms`, including `static JmsFactory find(ClientSettings)` and `find(ClientSettings, boolean)`; and the three implementations with `getName()` returning `"JNDI"`, `"ActiveMQSslConnectionFactory"`, `"AzureServiceBusPremium"` exactly as the javax ones do.

- [ ] **Step 1: Confirm the Azure jakarta class name**

**Done — answered against the real artifacts in the local m2 repo (`com.azure:azure-servicebus-jms:2.1.0` and `com.microsoft.azure:azure-servicebus-jms:1.0.2`), so the implementer does not need to repeat it.**

It is **not** a one-line difference, as this step assumed. The whole package moved: `com.microsoft.azure.servicebus.jms.*` → `com.azure.servicebus.jms.*`, so **all three** reflective strings in `AzureServiceBusJmsFactory` change — `ConnectionStringBuilder`, `ServiceBusJmsConnectionFactorySettings` and `ServiceBusJmsConnectionFactory`.

Everything the SDK reflects on is otherwise identical, verified with `javap -p`: the two artifacts' `ConnectionStringBuilder` constructor sets match exactly (including the `(URI, String, String, String)` one this code uses), `ServiceBusJmsConnectionFactorySettings` has the same no-arg constructor and the same four setters, and `ServiceBusJmsConnectionFactory(ConnectionStringBuilder, ServiceBusJmsConnectionFactorySettings)` still exists. 2.1.0 declares `jakarta.jms:jakarta.jms-api` and its factory implements `jakarta.jms.ConnectionFactory`, confirming it is the jakarta variant.

- [ ] **Step 2: Create the four classes as jakarta-typed copies**

Copy each from its javax counterpart, changing only `javax.jms` → `jakarta.jms`. **`javax.naming` does not change** — JNDI is JDK, not Jakarta EE, so `InitialContext`, `NamingException` and `NameNotFoundException` stay `javax.*` in both trees. `ActiveMqSslJmsFactory`'s reflection string is unchanged too: `org.apache.activemq.ActiveMQSslConnectionFactory` exists under that exact name in `activemq-client-jakarta`.

The jakarta `find(...)` must default to the **jakarta** `JndiJmsFactory`, so neither interface's static initialisation can pull in the other namespace.

Register all three in the new services file.

- [ ] **Step 3: Verify it compiles and the gates hold**

Run: `mvn test-compile -pl njams-sdk`
Expected: BUILD SUCCESS — Task 1 put `jakarta.jms` on the classpath.
Run: `mvn validate -Pcheckstyle -pl njams-sdk && mvn javadoc:javadoc -pl njams-sdk`
Expected: BUILD SUCCESS for both.
Run: `mvn test -pl njams-sdk`
Expected: PASS — the javax path is unaffected; the jakarta factories are registered but no jakarta broker client is present, so they are skipped.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/ njams-sdk/src/main/resources/META-INF/services/
git commit -m "SDK-495 add the jakarta JmsFactory SPI and its three implementations"
```

---

### Task 11: The jakarta sender, receiver and shared receiver

**Files:**
- Create: `communication/jms/{JmsSender,JmsReceiver,SharedJmsReceiver}.java`
- Modify: `njams-sdk/src/main/resources/META-INF/services/{…AbstractSender,…AbstractReceiver}`

**Added mid-task on 2026-10-30, on the user's direction** — `JmsSenderBaselineIT` failed once the jakarta
sender was registered first, and the cause was a selection defect, not a defect in the new classes (see the
amended §5.4 bullet 4 and Review Focus 3):
- Modify: `communication/CommunicationFactory.java` — `selectApplicable` skips any failing candidate instead of
  aborting on the first non-marker failure. The now-unused `failureMessage` parameter and both call-site
  arguments go with it.
- Modify: `communication/jms/JndiJmsFactory.java` **and** `communication/jms/javax/factory/JndiJmsFactory.java` —
  `initFactory` throws `NamingException` naming `njams.sdk.communication.jms.connectionFactory` when that setting
  is blank, instead of returning with no connection factory and failing later at `createConnectionFactory()`.
  Both `ActiveMqSslJmsFactory`s override `initFactory` and are unaffected; neither `AzureServiceBusJmsFactory`
  extends `JndiJmsFactory`. This is an observable behaviour change on shipped API — recorded in spec §5.8 as a
  `breaking-change` input.
- Modify (with explicit user permission, per `testing-conventions.md`): `CommunicationFactoryCandidateTest`,
  `JmsFactoryTest`, `JndiJmsFactoryTest`, `JmsSenderTest`, `JmsReceiverTest`. Three of these pinned the old
  contract by name and were inverted and renamed; the rest only needed a `connectionFactory` in their setup.
- Modify: Javadoc for the six `protected` fields on each of the jakarta and javax `JmsSender`/`JmsReceiver`
  (`public-api-design.md` requires it on protected fields; checkstyle's `JavadocVariable` is `scope=public` and
  does not catch them).

**Interfaces:**
- Consumes: `AbstractJmsSender`, `AbstractJmsReceiver` (Tasks 6–7), the jakarta `JmsFactory` (Task 10), `TransportNotApplicableException` (Task 4).
- Produces: `com.im.njams.sdk.communication.jms.JmsSender` / `JmsReceiver` / `SharedJmsReceiver`, all reporting `getName()` → `"JMS"`.

- [ ] **Step 1: Create the three classes as jakarta-typed copies of their javax counterparts**

Same bases, same hooks, same `resolveFactory` contract from Task 8 — only the JMS imports and the `JmsFactory` reference differ. `librariesToCheck()` lists the `jakarta.jms.*` names; `javax.naming.*` entries stay as they are. `SharedJmsReceiver implements ShareableReceiver<jakarta.jms.Message>` — `ShareableReceiver<T>` is generic and erased, so `CommunicationFactory`'s `instanceof ShareableReceiver` check is unaffected.

These are **not** deprecated.

**Carried in from Task 10's review — `resolveFactory` must `catch (Throwable)`.** Mirror the javax shape at
`communication/jms/javax/JmsSender.java` exactly: `catch (Throwable e)` and then
`ServiceLoaderSupport.isNotApplicable(e)`. On a javax-only classpath the jakarta `JndiJmsFactory` still *loads*
successfully through SPI — lazy resolution never touches its jakarta method descriptors during instantiation —
so the decline comes from *using* the factory, not from looking it up. The discriminating throwable is then a
`NoClassDefFoundError: jakarta/jms/ConnectionFactory` raised by `initFactory`'s cast, i.e. an **`Error`**. A
`catch (Exception)` would let it escape and break the candidate fall-through. (Verified by the reviewer to be
pre-existing and symmetric: the javax SPI type behaves identically with `javax.jms-api` removed.)

**Also carried in: the bare name `JmsFactory` is now ambiguous inside `com.im.njams.sdk.communication.jms`.**
`FailingJmsFactory`, `NoopJmsFactory`, `WorkingJmsFactory` and `JmsFactoryResolutionTest` each rely on an
explicit single-type import of `…jms.factory.JmsFactory` to keep meaning the javax type (JLS 6.4.1). They
compile and are green, but their correctness now rests on an import line that reads like boilerplate. **Leave
them alone in this task** — Task 12 moves all four under `…jms.javax`, which resolves the ambiguity rather than
documenting around it. Do not add disambiguating comments that Task 12 would immediately delete.

**Carried in from Task 9.** The seven class-level `@deprecated` tags on the relocated javax classes name their jakarta replacement in `{@code …}`, not `{@link …}`, because the targets did not exist yet and a `{@link}` to a missing type is a hard Javadoc error. Once the three classes above (and Task 10's factories) exist, upgrade those seven references to real `{@link}`s and re-run `mvn javadoc:javadoc -pl njams-sdk`.

- [ ] **Step 2: Register them first in the services files**

Put the jakarta entries above the `…jms.javax` ones in both `…AbstractSender` and `…AbstractReceiver`. Services-file order is the tiebreak, so this makes jakarta the forward-looking default whenever both are viable.

- [ ] **Step 3: Verify**

Run: `mvn test -pl njams-sdk`
Expected: PASS. **Note, corrected 2026-10-30:** this step previously claimed "the jakarta providers fail to load"
on this classpath. That is false — Task 1 put *both* JMS APIs on `njams-sdk`'s own test classpath, so the jakarta
providers load cleanly and, being registered first, are tried first and then skipped at `init`. Nothing lands in
the *not-applicable* bucket here and that bucket cannot be exercised inside `njams-sdk` at all; Tasks 13/14's
single-namespace modules are the only place it can be. `hasUnavailable()` stays false because everything loads,
and the javax tree is still what ends up selected.
Run: `mvn test -Dtest=JmsSenderBaselineIT -pl njams-sdk` and inspect the log.
Expected: PASS with **no WARN** from `JmsFactory.find` — Review Focus 5 on a real startup.
Run: `mvn validate -Pcheckstyle -pl njams-sdk && mvn javadoc:javadoc -pl njams-sdk`
Expected: BUILD SUCCESS for both.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/ njams-sdk/src/main/resources/META-INF/services/
git commit -m "SDK-495 add the jakarta JMS sender and receivers and register them ahead of the javax ones"
```

---

### Task 12: Mirror the JMS unit tests onto the jakarta tree

Every JMS unit test currently exercises the `javax` tree only. Task 10's four factories and Task 11's three
sender/receiver classes ship with **no** tests of their own. This task closes that: each mocked JMS unit test
gets a jakarta twin, so both namespaces carry identical coverage rather than one tree being covered and the
other riding on the claim that it is a faithful copy.

**This reverses the spec's original §6.1 decision** ("No jakarta mirrors of the JMS unit tests", confirmed
2026-10-28). The user reverted it on 2026-10-30: duplicated tests are preferred over leaving either variant
uncovered, deprecated or not. §6.1, §6.3 and §6.5 have been rewritten accordingly — read them before starting.

**Files:**
- Move (`git mv`, javax-bound test helpers and the javax resolution test, so `…jms` is free for the jakarta
  twins): `communication/jms/{WorkingJmsFactory,NoopJmsFactory,FailingJmsFactory,JmsFactoryResolutionTest}.java`
  → `communication/jms/javax/`
- Create (jakarta twins, all in `njams-sdk/src/test/java/com/im/njams/sdk/communication/jms/`):
  `JmsSenderTest.java`, `JmsReceiverTest.java`, `SharedJmsReceiverTest.java`, `JmsReceiverMock.java`,
  `JmsFactoryTest.java`, `JmsFactoryUnavailableLoggingTest.java`, `JndiJmsFactoryTest.java`,
  `ActiveMqSslJmsFactoryTest.java`, `AzureServiceBusJmsFactoryTest.java`, `JmsFactoryResolutionTest.java`,
  `WorkingJmsFactory.java`, `NoopJmsFactory.java`, `FailingJmsFactory.java`
- Create: `njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.jms.JmsFactory`
- Modify: `njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.jms.factory.JmsFactory`
  (the two moved helper FQCNs), `communication/CommunicationFactoryTest.java`,
  `utils/ServiceLoaderSupportTest.java`, `communication/jms/factory/JmsFactoryUnavailableLoggingTest.java`
  (imports of the moved helpers)

**Interfaces:**
- Consumes: the jakarta `JmsFactory` and its three implementations (Task 10); the jakarta `JmsSender`,
  `JmsReceiver`, `SharedJmsReceiver` (Task 11); `ScopedSpiRule` (already in `src/test`).
- Produces: nothing other tasks build on — this task is pure coverage.

**Source of each twin.** The mirror is mechanical: copy the javax test, change the `javax.jms.*` imports to
`jakarta.jms.*`, and point the type under test at the jakarta class. **`javax.naming.*` does NOT change** —
JNDI is a JDK API, exactly as in the production classes. Assertions, test names and test counts stay identical;
that parity is what makes the twin evidence rather than decoration.

| javax source | tests | jakarta twin lands in |
|---|---|---|
| `communication/jms/javax/JmsSenderTest.java` | 11 | `communication/jms/JmsSenderTest.java` |
| `communication/jms/javax/JmsReceiverTest.java` | 21 | `communication/jms/JmsReceiverTest.java` |
| `communication/jms/javax/SharedJmsReceiverTest.java` | 2 | `communication/jms/SharedJmsReceiverTest.java` |
| `communication/jms/javax/JmsReceiverMock.java` | — (helper) | `communication/jms/JmsReceiverMock.java` |
| `communication/jms/javax/factory/JndiJmsFactoryTest.java` | 7 | `communication/jms/JndiJmsFactoryTest.java` |
| `communication/jms/javax/factory/ActiveMqSslJmsFactoryTest.java` | 3 | `communication/jms/ActiveMqSslJmsFactoryTest.java` |
| `communication/jms/javax/factory/AzureServiceBusJmsFactoryTest.java` | 3 | `communication/jms/AzureServiceBusJmsFactoryTest.java` |
| `communication/jms/factory/JmsFactoryTest.java` | 10 | `communication/jms/JmsFactoryTest.java` |
| `communication/jms/factory/JmsFactoryUnavailableLoggingTest.java` | 2 | `communication/jms/JmsFactoryUnavailableLoggingTest.java` |
| `communication/jms/JmsFactoryResolutionTest.java` (after the Step 1 move) | 18 | `communication/jms/JmsFactoryResolutionTest.java` |

That is **77 mirrored tests**. `JmsFactoryResolutionTest` is included deliberately: `resolveFactory` is the one
method §5.2 accepts as hand-copied per namespace, so the jakarta copy is exactly the kind of untested duplicate
this task exists to cover.

**The Azure twin must pin the reflective class names.** Task 10's review established that the javax
`AzureServiceBusJmsFactoryTest` never reaches a reflective name — its three tests cover identity, the
not-initialized guard and a bad provider URL — so a pure mirror of it would have stayed green against a wrong
Azure package. Add one test to the jakarta twin asserting the three strings
(`com.azure.servicebus.jms.{ConnectionStringBuilder,ServiceBusJmsConnectionFactorySettings,ServiceBusJmsConnectionFactory}`)
are the ones the factory actually reflects on, and the matching test to the javax one for
`com.microsoft.azure.servicebus.jms.*`. These are the only two tests in this task that are not a straight copy.
Adding a test to an existing javax test class is additive and authorised by this reversal, so no further
permission is needed — but do not alter any existing assertion.

- [ ] **Step 1: Move the four javax-bound test files into `…jms.javax`**

Use `git mv` so history follows. Fix the `package` declaration in each, then fix every referencing import:
`CommunicationFactoryTest`, `ServiceLoaderSupportTest`, `communication/jms/factory/JmsFactoryUnavailableLoggingTest`,
and the two helper FQCNs inside
`src/test/resources/META-INF/services/com.im.njams.sdk.communication.jms.factory.JmsFactory`
(`EmbeddedActiveMqJmsFactory` is javax-bound and stays registered there unchanged).

This also discharges Task 11's carried-in ambiguity hazard: with the javax helpers gone from `…jms`, the bare
name `JmsFactory` inside that test package unambiguously means the jakarta one.

- [ ] **Step 2: Run the suite to confirm the move alone changed nothing**

Run: `mvn -o test -pl njams-sdk`
Expected: the same figure as at the end of Task 11, 0 failures, 0 errors. A move with identical assertions must
not move the count.

- [ ] **Step 3: Write the thirteen jakarta files**

Ten twins from the table, plus jakarta `WorkingJmsFactory`, `NoopJmsFactory` and `FailingJmsFactory`
implementing the **jakarta** `com.im.njams.sdk.communication.jms.JmsFactory`. Keep the simple names identical
to their javax counterparts — the package is what distinguishes them now.

- [ ] **Step 4: Register the two jakarta SPI helpers**

Create `src/test/resources/META-INF/services/com.im.njams.sdk.communication.jms.JmsFactory` listing the jakarta
`NoopJmsFactory` and `WorkingJmsFactory`. There is no jakarta `EmbeddedActiveMqJmsFactory` — `activemq-broker`
is javax-only (§6.5), so the jakarta file has two entries where the javax one has three.

- [ ] **Step 5: Verify**

Run: `mvn -o test -pl njams-sdk`
Expected: PASS, with the test count up by **79** over Step 2's figure (77 mirrored + the two Azure
reflective-name tests). Read the figure from Maven's own summary line, not by summing
`target/surefire-reports/TEST-*.xml`.

Then confirm the new jakarta SPI registrations did not disturb the javax path, which now discovers them on
every JMS `find`: they must land in the *not-applicable* bucket, `hasUnavailable()` must stay false, and no
`JmsFactory.find` WARN may appear in a healthy javax startup (Review Focus 5).

Run: `mvn -o test -Dtest=JmsSenderBaselineIT -pl njams-sdk` and inspect the log.
Expected: PASS with no WARN from `JmsFactory.find`.
Run: `mvn -o validate -Pcheckstyle -pl njams-sdk && mvn -o javadoc:javadoc -pl njams-sdk`
Expected: BUILD SUCCESS for both. (Checkstyle does not scan test sources, but the production Javadoc is
untouched and must stay clean.)

**Three forced deviations, found during implementation on 2026-10-30.**

1. **The moved `JmsFactoryResolutionTest` needs accessor subclasses.** Its own NOTE predicted this: it read
   `AbstractJmsSender.isUseProjectQueue()` and `AbstractJmsReceiver.topicName`/`.useMessageselector`, all
   `protected`, off a non-subclass reference — which stops compiling once the file leaves `…jms`. Widening them
   is a production change, so two tests now reach the same state through named subclasses
   (`StateReadingJmsSender`/`StateReadingJmsReceiver`), the pattern this ticket already chose in `91364d60`
   rather than widening `isCongestion`. Test names and asserted values unchanged; the jakarta twin uses the
   same shape so the two files stay mirror images.
2. **`ActiveMqSslJmsFactoryTest.initCreatesActiveMqSslConnectionFactory` is `@Ignore`d on the jakarta side.**
   Structurally unrunnable here — see spec §6.1's documented exception. User's decision on 2026-10-30: keep the
   ignored mirror with its body intact and hand the assertion to Task 13. Not caused by the
   no-production-changes rule; it could not be made green with a production change either.
3. **The jakarta `JndiJmsFactoryTest` supplies its own JNDI provider.** `initIsIdempotent` as a pure mirror hit
   the same javax-ActiveMQ cast failure, but there the provider is named by a *setting* rather than hardcoded,
   so a nested `JakartaInitialContextFactory` returning a mocked `jakarta.jms.ConnectionFactory` fixes it
   test-only. Opens no socket. Test name and assertion unchanged.

Expected suite after this task: **1728 run / 0 failures / 0 errors / 2 skipped** — the second skip is item 2.

- [ ] **Step 6: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/ njams-sdk/src/test/java/com/im/njams/sdk/utils/ njams-sdk/src/test/resources/META-INF/services/
git commit -m "SDK-495 mirror the JMS unit tests onto the jakarta tree and move the javax-bound test helpers"
```

---

### Task 13: The two single-namespace test modules

**Files:**
- Create: `njams-sdk-jms-namespace-tests/pom.xml` (aggregator, `packaging=pom`, parent = root pom)
- Create: `njams-sdk-jms-namespace-tests/njams-sdk-jms-javax-test/{pom.xml,src/test/java/…/JavaxNamespaceSelectionTest.java}`
- Create: `njams-sdk-jms-namespace-tests/njams-sdk-jms-jakarta-test/{pom.xml,src/test/java/…/JakartaNamespaceSelectionTest.java}`
- Modify: `pom.xml` (`<modules>`)

**Interfaces:**
- Consumes: Tasks 10–11.
- Produces: proof that each single-namespace classpath selects its own tree.

**Carried in from Task 12.** `njams-sdk-jms-jakarta-test` is the only place on this plan with
`activemq-client-jakarta`, so it also picks up the one assertion Task 12 had to ignore: that the jakarta
`ActiveMqSslJmsFactory` really does produce an ActiveMQ SSL connection factory. Copy the body from
`njams-sdk/src/test/java/com/im/njams/sdk/communication/jms/ActiveMqSslJmsFactoryTest.java`'s `@Ignore`d
`initCreatesActiveMqSslConnectionFactory`; it is intact and needs only the right classpath. Until this lands,
the jakarta `ActiveMqSslJmsFactory.createConnectionFactory()` path has no executing coverage (spec §6.1).

- [ ] **Step 1: Create the aggregator and both children**

The aggregator carries the shared setup — the `njams-sdk` dependency, the JUnit/Mockito stack, surefire configuration, `maven.deploy.skip=true` — so each child declares only its own JMS API and broker client. Add one `<module>njams-sdk-jms-namespace-tests</module>` to the root pom's **default** `<modules>` list; these need no Docker and must run on every build.

Child classpaths: `javax.jms:javax.jms-api` + `org.apache.activemq:activemq-client` for one, `jakarta.jms:jakarta.jms-api` 3.x + `org.apache.activemq:activemq-client-jakarta` for the other.

- [ ] **Step 2: Write the tests**

```java
@Test public void communicationFactoryReturnsTheExpectedSenderImplementation()
@Test public void communicationFactoryReturnsTheExpectedReceiverImplementation()
@Test public void theOtherNamespacesProvidersAreSkipped_notFatal()
@Test public void describeAvailabilityNamesTheSkippedTree()
```

Assert the selected class by **fully-qualified** name — both trees use the same simple names. Settings point at `org.apache.activemq.jndi.ActiveMQInitialContextFactory` (and its jakarta counterpart) so the lookup synthesises a `ConnectionFactory` without touching the network.

**No test may call `Njams.start()`** — that connects. Drive `CommunicationFactory` directly.

- [ ] **Step 3: Run both modules**

Run: `mvn test -pl njams-sdk-jms-namespace-tests/njams-sdk-jms-javax-test,njams-sdk-jms-namespace-tests/njams-sdk-jms-jakarta-test`
Expected: PASS, with no broker running anywhere.

- [ ] **Step 4: Add the neither-API-present case**

Either as a third child module with no JMS API at all, or in `njams-sdk`'s own suite if a classpath without either API can be arranged there — implementer's call. It asserts that the SDK starts, that the JMS transport reports itself unavailable with a message naming the missing API, and that no `NoClassDefFoundError` escapes.

- [ ] **Step 5: Commit**

```bash
git add pom.xml njams-sdk-jms-namespace-tests/
git commit -m "SDK-495 add broker-less modules proving namespace selection on each single-namespace classpath"
```

---

### Task 14: The mixed-classpath module

**Files:**
- Create: `njams-sdk-jms-namespace-tests/njams-sdk-jms-mixed-test/{pom.xml,src/test/java/…/MixedNamespaceSelectionTest.java}`

**Interfaces:**
- Consumes: Task 13's aggregator; Tasks 5, 10, 11.
- Produces: proof that §5.4's candidate loop picks the tree the configured connection factory belongs to, on a classpath carrying both.

- [ ] **Step 1: Confirm the Artemis pairing, or substitute**

Confirm that `org.apache.activemq:artemis-jakarta-client` exposes `org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory` under the `jakarta.jms` namespace, and that the chosen version's Java baseline is 11.

If no Artemis version satisfies both, substitute any other jakarta-namespace client whose classes do not collide with AMQ Classic's — this test needs **two different products**, not Artemis specifically. Only if no such pairing exists: `openJDK-25` is available on Jenkins and may be used for the build with the compile target still 11, which additionally requires switching `pom.xml:107-108` from `<source>`/`<target>` to `<release>11`, updating the `Jenkinsfile` and `CLAUDE.md`'s "Java 11+" line, and doing that JDK move as a separate verified step first. Raise it before taking that path.

- [ ] **Step 2: Assemble the mixed classpath**

`javax.jms:javax.jms-api` + `org.apache.activemq:activemq-client` (Classic, javax) alongside `jakarta.jms:jakarta.jms-api` 3.x + the jakarta client. The javax API must come from `javax.jms:javax.jms-api`, never `jakarta.jms:jakarta.jms-api:2.0.3`, which shares coordinates with the 3.x API. Exclude `activemq-client`'s transitive pull of that artifact.

- [ ] **Step 3: Write the tests**

```java
@Test public void classicSettingsSelectTheJavaxTree()
@Test public void artemisJakartaSettingsSelectTheJakartaTree()
@Test public void twoClientsInOneJvmEachResolveTheirOwnTree()
@Test public void settingsResolvingInNeitherTreeProduceOneErrorNamingEveryCause()
```

The third is the point of the module: two `CommunicationFactory` instances with different settings, each landing on its own tree. A JVM-global switch could not pass it.

- [ ] **Step 4: Run it**

Run: `mvn test -pl njams-sdk-jms-namespace-tests/njams-sdk-jms-mixed-test`
Expected: PASS, no broker.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk-jms-namespace-tests/
git commit -m "SDK-495 prove namespace selection on a classpath carrying both APIs and two broker products"
```

---

### Task 15: Documentation and rules

**Files:**
- Modify: `wiki/FAQ.md`, `CLAUDE.md`, `.claude/rules/public-api-design.md`, `.claude/rules/communication-layer.md`

**Interfaces:**
- Consumes: everything.
- Produces: no code.

- [ ] **Step 1: Add the FAQ entry**

A new `wiki/FAQ.md` entry covering: the SDK serves both namespaces from one artifact; which one is chosen and how; `javax` is the legacy path; what a client on Camel 4 / Spring 6 / Quarkus 3 needs on its classpath; and the one silent degradation — a settings value naming a relocated factory by **fully-qualified** class name no longer matches and falls through to `JndiJmsFactory`, while `getName()` and simple-class-name matching still work. No setting changed, so `settings_full.properties` needs no update.

- [ ] **Step 2: Update the rules**

`public-api-design.md`: add `communication.jms.JmsFactory` to the registered-SPI list, and note that `communication.jms.factory.JmsFactory` is its deprecated predecessor that can never move.

`communication-layer.md`: record the two-tree arrangement and that provider names need not be unique, candidates being tried in services-file order.

`CLAUDE.md`: add the `njams-sdk-jms-namespace-tests` aggregator and its children to the project structure section.

- [ ] **Step 3: Update `AbstractSender`/`AbstractReceiver` Javadoc**

State that provider names need not be unique, that candidates are tried in services-file order, and that an implementation may decline by throwing `TransportNotApplicableException` from `init`. No signature changes.

- [ ] **Step 4: Verify**

Run: `mvn validate -Pcheckstyle -pl njams-sdk && mvn javadoc:javadoc -pl njams-sdk`
Expected: BUILD SUCCESS for both.

- [ ] **Step 5: Commit**

```bash
git add wiki/FAQ.md CLAUDE.md .claude/rules/ njams-sdk/src/main/java/com/im/njams/sdk/communication/
git commit -m "SDK-495 document jakarta.jms support and the two-tree selection contract"
```

---

## Not in this plan

Per spec §6.5, migrating `njams-sdk`'s JMS unit tests and `njams-sdk-communication-it`'s resilience suite from `javax` to `jakarta` is **sub-scope of SDK-495** and gets its own spec and plan. SDK-495 must not be resolved until it is done. It is not mechanical: `EmbeddedActiveMqBroker` uses `activemq-broker`'s `vm://` transport, which is javax-only, and `activemq-client-jakarta` has no embedded broker.

Per spec §9, holding the resolved `JmsFactory` for the sender's lifetime is a separate `njams-safe-modification`, not part of this work.
