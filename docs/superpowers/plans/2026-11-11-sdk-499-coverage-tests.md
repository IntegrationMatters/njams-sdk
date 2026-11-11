# SDK-499 Coverage Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pass the SonarCloud coverage condition for `njams-sdk` (76.9% → ≥ 80%) by excluding the deprecated Kafka
transport and Argos metrics from coverage, and by adding tests for extract rules, shared-receiver routing, the JMS
transport (both namespace trees) and the HTTP SSE receiver.

**Architecture:** One Sonar property in the root POM's `sonarcloud` profile, plus new JUnit 4/Mockito test classes
only. No production code changes; no existing test method changes. All new tests run without a broker, a server or
network traffic.

**Tech Stack:** Java 11, JUnit 4, Mockito 5 (inline mock maker), OkHttp 4 + okhttp-eventsource 4.1.1 (mocked),
JaCoCo 0.8.16, SonarCloud.

**Spec:** none — the ticket (SDK-499) is the requirement source. Binding specs by topic: SDK-495
(`docs/superpowers/specs/2026-10-28-sdk-495-jakarta-jms-support-design.md` §6.1, JMS test mirroring), SDK-375
(`docs/superpowers/specs/2026-07-02-sdk-375-sender-lifecycle-design.md` §8, deterministic tests), SDK-483
(`docs/superpowers/specs/2026-09-28-sdk-483-communication-it-module-design.md`, the Docker suite does not grow).

## Global Constraints

- **Prerequisite:** the SDK-375 receiver fixes (`AbstractReceiver.attemptConnect()`, `HttpSseReceiver.stop()`) are
  committed before Task 6 starts.
- Existing tests stay unchanged (ticket acceptance criterion). New tests go into new test classes.
- No production code changes. If a test can only be written with one, stop and ask.
- No new dependencies.
- JMS (SDK-495 §6.1): every new JMS test class exists twice — `communication/jms/` (jakarta) and
  `communication/jms/javax/` (javax) — with identical test names and assertions; only `jakarta.jms.*` ↔ `javax.jms.*`
  imports and the type under test differ. Helpers live in the matching tree's package.
- No timing-based tests: wait on conditions with a deadline (polling, latch, Mockito `timeout(...)`), never assert on
  durations or rely on a fixed sleep (`.claude/rules/testing-conventions.md`).
- Shared/static state (e.g. the scripted JMS factory's mocks) is reset in `@Before`.
- Do not run `mvn install`. Verify with `mvn test -pl njams-sdk` (single classes via `-Dtest=...`).
- Commits: `SDK-499 <description>` per task, without `#comment`; only Task 6's commit, the one that completes the
  ticket's scope, carries `#comment` (`.claude/rules/commit-conventions.md`).

## Review Focus

1. A JMS receiver test asserting a reply must check the reply's `NJAMS_CLIENTID` property and correlation ID, not
   only that `send` was called — a reply to the wrong client is the realistic production failure.
2. Message-filter tests must cover the old-message time filter (`JMSTimestamp` older than the subscription) — a
   restarted client must not replay stale commands.
3. Shared-receiver routing must prove that an instruction for an unknown client is answered with result code 99,
   not silently dropped — the server would otherwise wait for a reply forever.
4. Extract tests for `businessservice`/`businessobject` must assert the resolved path string, since `Path.resolve`
   normalizes the value.
5. HTTP reply tests must assert `njams-reply-for` matches the request's message id in both event formats (JSON
   headers and legacy) — the server correlates replies by it.

## Deviations (approved during execution)

1. **MSG-32.** An instruction without a request made `Command.getFromInstruction` (njams-messageformat) throw an
   NPE. Fixed in messageformat 5.0.2 under MSG-32; `SharedReceiverSupportRoutingTest.instructionWithoutRequestGetsResultCode1`
   depends on that version.
2. **Production fix in `SharedJmsReceiver` (both trees).** A failure while replacing the consumer for changed clients
   left the receiver connected with an outdated or missing consumer; it now triggers the receiver's reconnect.
   Folded into SDK-499. Consequently Task 4's "failing `close()` is survived" became
   `failingConsumerClose/CreationReconnectsWithTheCurrentSelector`, and `failingConsumerCloseIsSurvived` was deleted.
3. **SDK-503** tracks the related, separate defect that removing a non-last client does not refresh the selector.
4. **`#comment`** is not on Task 6's commit; it goes on the commit that concludes the ticket, once the SonarCloud gate
   is confirmed on the pushed branch.

---

## Tasks

### Task 1: Exclude Kafka and Argos from the coverage measurement

**Files:**
- Modify: `pom.xml` (profile `sonarcloud`, `<properties>`, next to `sonar.coverage.jacoco.xmlReportPaths`, ~L421-427)

- [ ] **Step 1:** Add
  `<sonar.coverage.exclusions>**/com/im/njams/sdk/communication/kafka/**,**/com/im/njams/sdk/argos/**</sonar.coverage.exclusions>`
  preceded by an XML comment stating that excluding the deprecated Kafka transport and Argos metrics is a deliberate
  decision not to raise test coverage for deprecated parts (see `.claude/rules/kafka-argos-deprecated.md`).
- [ ] **Step 2:** Verify:
  `mvn -q -Psonarcloud help:evaluate -Dexpression=sonar.coverage.exclusions -DforceStdout -pl njams-sdk`
  prints exactly the pattern list above.
- [ ] **Step 3:** Commit: `SDK-499 Exclude the deprecated Kafka transport and Argos metrics from Sonar coverage.`

### Task 2: Extract-rule tests

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/ExtractHandlerRuleTest.java`

**Interfaces:**
- Consumes: `ExtractHandler.handleExtract(JobImpl, Extract, ActivityImpl, ExtractSource, String)`,
  `ExtractHandler.handleExtract(JobImpl, ActivityImpl, ExtractSource, String)`,
  `ExtractHandler.testExpression(RuleType, String, String)`, `ExtractSource.byDirection(String)`.
- Setup: same fixture shape as `ExtractHandlerTest` (spied `Njams` with `TestReceiver.getSettings()`, injected
  `Configuration` with `LogMode.COMPLETE`), but rules are built as real `ExtractRule` objects (attribute, attribute
  type, rule type, rule, inout) so one test can carry several rules. Clear `DataMasking` in `@Before`/`@After`.

- [ ] **Step 1: Write the tests** (each asserts on the activity / job state after `handleExtract`):
  1. `xpathRuleSetsAttribute` — XML `<a><b>v</b></a>`, rule `/a/b`, attribute `k` → `getAttributes().get("k") == "v"`.
  2. `xpathRuleWithNamespacesSetsAttribute` — namespaced XML with prefix in the expression → value extracted.
  3. `xpathRuleWithoutMatchSetsEmptyValue` — no match → attribute `k` is `""`.
  4. `xpathRuleWithInvalidExpressionIsIgnored` — `//[` → no attribute, no exception.
  5. `jmespathRuleSetsAttributeWithoutQuotes` — JSON `{"a":{"b":"v"}}`, rule `a.b` → `"v"` (no surrounding quotes).
  6. `jmespathRuleOnInvalidJsonIsIgnored` — data `not json` → no attribute, no exception.
  7. `eventRuleSetsEventStatus` — `RuleType.EVENT`, rule `error` → `getEventStatus() == 3`; empty rule → status
     unchanged.
  8. `eventAttributeTypeSetsEventStatus` — `AttributeType.EVENT` with REGEXP/VALUE/XPATH/JMESPATH producing
     `success`, `warning`, `error`, `2`, `other` → statuses 1, 2, 3, 2, 0 (INFO).
  9. `specialAttributeNamesSetJobAndActivityFields` — VALUE rules for `correlationlogid`, `parentlogid`,
     `externallogid`, `businessservice` (`>a>b>`), `businessobject`, `eventmessage`, `eventcode`, `payload`,
     `stacktrace` → `job.metadata().getCorrelationLogId()` … `getBusinessService()` resolved path string,
     `activity.getEventMessage()`, `getEventCode()`, `getEventPayload()`, `getStackTrace()`; names are matched
     case-insensitively (use `CorrelationLogId`).
  10. `disabledRuleAndOtherDirectionAreSkipped` — DISABLED rule and an `out` rule handled as INPUT → no attributes.
  11. `regexpOnBlankDataAndInvalidPatternAreIgnored` — blank data → no attribute; pattern `(` → no attribute.
  12. `regexpWithoutGroupUsesWholeMatch` / existing group case → group 1 when present, group 0 otherwise.
  13. `nullExtractIsIgnored` — `handleExtract(job, null, …)` does nothing.
  14. `extractFromActivityConfiguration` — configure an `ActivityConfiguration` with an extract for the model;
      the 4-arg overload applies it; without configuration nothing happens.
  15. `byDirectionIsCaseInsensitiveAndRejectsUnknown` — `"IN"`→INPUT, `"Out"`→OUTPUT, `"x"`→
      `IllegalArgumentException`.
  16. `testExpressionCoversAllRuleTypes` — JMESPATH result, DISABLED/EVENT → `null`, `null` type →
      `IllegalArgumentException`, REGEXP without match / blank → `null`.
- [ ] **Step 2:** Run `mvn test -pl njams-sdk -Dtest=ExtractHandlerRuleTest` — expect PASS (characterization tests of
  existing behavior). Any failure is either a test mistake or a real defect: if the latter, stop and report it
  (propose a separate ticket, do not change production code here).
- [ ] **Step 3:** Commit: `SDK-499 Add extract-rule tests for all rule types, attribute targets and directions.`

### Task 3: Shared-receiver routing tests

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/SharedReceiverSupportRoutingTest.java`

**Interfaces:**
- Consumes: `SharedReceiverSupport(R)`, `addNjams(Njams)`, `onInstruction(M, Instruction, boolean)`.
- Helper: a recording `AbstractReceiver & ShareableReceiver<Object>` (same shape as `FakeShareableReceiver` in
  `SharedReceiverSupportTest`) whose `getReceiverPath`/`getClientId` return per-test values, `extendRequest` returns
  a configurable `Response`, and `sendReply(message, instruction, clientId)` records its arguments. `Njams` instances
  are mocks with `metadata()` (client path, session id), `commands().list()` and `features().isContainerMode()`.

- [ ] **Step 1: Write the tests:**
  1. `instructionIsRoutedByReceiverPath` — two instances, path of the second → only its listener runs; one reply with
     the second's session id.
  2. `clientIdTakesPrecedenceOverPath` — client id of the first, path of the second → routed to the first.
  3. `unknownClientIsAnsweredWithResultCode99WhenRequired` — `failOnMissingInstance=true` → reply with result code
     99, message `Client instance not found.`, client id `null`.
  4. `unknownClientIsIgnoredWhenNotRequired` — `false` → no reply.
  5. `missingReceiverPathIsIgnored` — `getReceiverPath` returns `null` → no listener, no reply.
  6. `instructionWithoutCommandGetsResultCode1` — request `null` and request without command → response code 1.
  7. `extendRequestResponseIsSentWithoutListeners` — `extendRequest` returns a response → listeners not called, that
     response is replied.
  8. `failingListenerDoesNotStopOthers` — first listener throws, second sets the response → reply carries it.
  9. `noListenerResponseIsDefaulted` — no listener → code 1, message `No InstructionListener for <cmd> found`.
  10. `getRequestHandlerIsSuppressedOutsideContainerMode` — command `GetRequestHandler`, container mode off → no
      listener, no reply.
- [ ] **Step 2:** Run `mvn test -pl njams-sdk -Dtest=SharedReceiverSupportRoutingTest` — PASS.
- [ ] **Step 3:** Commit: `SDK-499 Add routing tests for the shared receiver support.`

### Task 4: JMS receiver and shared receiver tests (both trees)

**Files (each for the jakarta tree under `communication/jms/` and mirrored under `communication/jms/javax/`):**
- Create: `ScriptedJmsFactory.java` (test helper)
- Create: `JmsReceiverConnectAndReplyTest.java`
- Create: `SharedJmsReceiverRoutingTest.java`
- Modify: `njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.jms.JmsFactory` (add
  `com.im.njams.sdk.communication.jms.ScriptedJmsFactory`) and
  `…/com.im.njams.sdk.communication.jms.factory.JmsFactory` (add
  `com.im.njams.sdk.communication.jms.javax.ScriptedJmsFactory`)

**Interfaces:**
- Produces: `ScriptedJmsFactory` implements the tree's `JmsFactory`; `NAME = "ScriptedJmsFactory"`; static
  `reset()` creating fresh Mockito mocks and static getters `connectionFactory()`, `connection()`, `session()`,
  `consumer()`, `producer()`, `topic()`, `queue()`; `createConnectionFactory()` returns the mock factory, whose
  `createConnection()` and `createConnection(String, String)` return `connection()`; `connection().createSession(…)`
  returns `session()`; `session().createConsumer(any())`/`createConsumer(any(), anyString())` return `consumer()`;
  `session().createProducer(any())` returns `producer()`; `session().createTextMessage(anyString())` returns a new
  mock `TextMessage` per call; `createTopic`/`createQueue` return `topic()`/`queue()`. Tests re-stub individual mocks
  to inject failures. Selected via `PROPERTY_JMS_CONNECTION_FACTORY=ScriptedJmsFactory`.
- Receiver under test: `JmsReceiver`/`SharedJmsReceiver` with a real, unstarted
  `new Njams(path, "1.0", "test", TestSender.getSettings())` and a capturing `InstructionListener`.

- [ ] **Step 1: Write `JmsReceiverConnectAndReplyTest`:**
  1. `connectWithoutCredentials` / `connectWithCredentials` → `createConnection()` resp.
     `createConnection(user, pw)`; consumer listener set; connection started; `isConnected()`.
  2. `connectWithMessageSelectorCreatesSelectiveConsumer` / `…WithoutSelector` (`PROPERTY_JMS_SUPPORTS_MESSAGE_SELECTOR=false`)
     → `createConsumer(topic, selector)` vs. `createConsumer(topic)`.
  3. `failingConnectClosesEverythingAndThrows` — `createSession` throws `JMSException` → `NjamsSdkRuntimeException`,
     connection closed, `isDisconnected()`.
  4. `stopReportsButSurvivesFailingClose` — consumer/session/connection `close()` throw → `stop()` completes,
     `isDisconnected()`.
  5. `messageFilterWithoutSelector` — selector off: message with `NJAMS_RECEIVER` = client path or an ancestor path →
     handled; other path → ignored; `JMSTimestamp` older than the connect → ignored (Review Focus 2); property
     access throwing → ignored.
  6. `onMessageRepliesToReplyDestination` — JSON instruction `ping`, reply-to and correlation id set → producer on the
     reply destination sends a `TextMessage` with `NJAMS_CLIENTID` = session id and the correlation id; producer
     closed (Review Focus 1).
  7. `onMessageIgnoresNonJsonForeignClientAndInvalidInstruction` — content `xml`; `NJAMS_CLIENTID` of another client;
     unparseable body → no producer created.
  8. `replyIsSplitIntoChunks` — `PROPERTY_MAX_MESSAGE_SIZE` = `SplitSupport.MIN_SIZE_LIMIT`, large response → several
     sends with chunk headers.
  9. `replyToClosedDestinationIsIgnored` — `createProducer` throws `InvalidDestinationException` → no exception;
     other `JMSException` → no exception; failing producer `close()` → no exception.
  10. `onExceptionTriggersReconnect` — `onException(new JMSException("x"))` → receiver stopped (`isDisconnected()`);
      call `setShouldShutdown(true)` + `cancelReconnect()` in `@After`.
- [ ] **Step 2: Write `SharedJmsReceiverRoutingTest`:**
  1. `instructionForRegisteredClientIsAnswered` — two `Njams` added via `setNjams`; message with `NJAMS_RECEIVER` of
     the second → its listener runs, reply has its session id.
  2. `instructionForUnknownClientIsAnsweredWith99` — Review Focus 3.
  3. `addingAndRemovingClientsRecreatesTheSelectiveConsumer` — connected, selector on → each `setNjams`/last
     `removeNjams` closes the old consumer and creates a new one; while not connected nothing is recreated; failing
     `close()` is survived.
  4. `nonJsonAndInvalidMessagesAreIgnored`.
  5. `receiverPathAndClientIdFailuresYieldNull` — property getters throw `JMSException` → `getReceiverPath`/
     `getClientId` return `null`; `sendReply` with `getJMSReplyTo` throwing → no exception.
- [ ] **Step 3:** Mirror both classes and the helper into `jms/javax/` (imports and type under test only).
- [ ] **Step 4:** Run `mvn test -pl njams-sdk -Dtest='JmsReceiverConnectAndReplyTest,SharedJmsReceiverRoutingTest,JmsFactoryResolutionTest,JmsReceiverTest,SharedJmsReceiverTest'`
  — all PASS in both packages (the existing classes prove the services-file additions changed nothing).
- [ ] **Step 5:** Commit: `SDK-499 Add JMS receiver and shared receiver tests for both namespace trees.`

### Task 5: JMS sender tests (both trees)

**Files (jakarta, mirrored under `jms/javax/`):**
- Create: `JmsSenderConnectAndSendTest.java`
- Create (shared by both trees): `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderFailureSinkProbe.java`

**Interfaces:**
- Produces: `SenderFailureSinkProbe` — test helper in package `com.im.njams.sdk.communication` (where the
  package-private `AbstractSender.setFailureSink(SenderFailureSink)` is reachable): `static List<Exception>
  attach(AbstractSender sender)` installs a recording sink and returns the live list of reported causes.
- Consumes: `ScriptedJmsFactory` (Task 4). Sender under test: `JmsSender` initialised with
  `PROPERTY_JMS_CONNECTION_FACTORY=ScriptedJmsFactory`; protected `send(LogMessage|ProjectMessage|TraceMessage, String)`
  called from the same package.

- [ ] **Step 1: Write the tests:**
  1. `connectWithAndWithoutCredentials` — blank user/password → `createConnection()`; both set →
     `createConnection(user, pw)`; exception listener registered; `isConnected()`.
  2. `sharedProducerWhenSelectorsSupported` / `separateProjectProducerWithoutSelectors` → one queue `<prefix>.event`
     vs. queues `<prefix>.event` and `<prefix>.project`; delivery mode set on each producer.
  3. `failingConnectCleansUpAndThrows` — `createProducer` throws → session and connection closed,
     `NjamsSdkRuntimeException`, `isDisconnected()`; also with failing `close()` calls.
  4. `sendsEachMessageTypeWithHeaders` — Log message with logId → event producer, properties `NJAMS_LOGID`,
     `NJAMS_MESSAGEVERSION=V4`, `NJAMS_MESSAGETYPE=event`, `NJAMS_PATH`, `NJAMS_CLIENTID`; Project and Trace messages →
     project producer, no logId property.
  5. `sendFailureIsWrapped` — producer `send` throws a non-congestion `JMSException` → `NjamsSdkRuntimeException`
     per message type.
  6. `closeReleasesProducersSessionAndConnection` — both producer layouts; each `close()` throwing is survived;
     `isDisconnected()`; second `close()` is a no-op.
  7. `onExceptionNotifiesConnectionFailure` — with `SenderFailureSinkProbe.attach(sender)`, `onException(new
     JMSException("x"))` reports one `NjamsSdkRuntimeException` whose cause is that `JMSException`.
- [ ] **Step 2:** Mirror into `jms/javax/`.
- [ ] **Step 3:** Run `mvn test -pl njams-sdk -Dtest='JmsSenderConnectAndSendTest,JmsSenderTest,AbstractJmsSenderTest'` — PASS.
- [ ] **Step 4:** Commit: `SDK-499 Add JMS sender connect, send and close tests for both namespace trees.`

### Task 6: HTTP SSE receiver tests (no network)

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/http/HttpSseReceiverTest.java`

**Interfaces:**
- Consumes: public `onMessage(String, MessageEvent)`, `onError(Throwable)`, `onOpen/onComment/onClosed`, protected
  `sendReply(String, Instruction, String)`, protected fields `client`, `source`, `subscribeUri`, `replyUrl`.
- Seams (proven by prototype): nested subclass of `HttpSseReceiver` with a status setter and an `onException`
  override that only records; `client` set to a Mockito `OkHttpClient` whose `newCall` captures each `Request` and
  returns a `Call` answering 200 with `ResponseBody.create("", null)`; `connectionPool()` stubbed with
  `new ConnectionPool()`. Events: `new MessageEvent(name, data, lastEventId, null)`. Real unstarted `Njams` with a
  capturing listener; settings `PROPERTY_COMMUNICATION=HTTP`, `PROPERTY_HTTP_BASE_URL=http://localhost:1`.
- Not covered here (already covered by SDK-375's `HttpSseReceiverLifecycleTest`): `connect()`, drop, reconnect,
  stop after drop.

- [ ] **Step 1: Write the tests:**
  1. `jsonHeaderEventIsAnswered` — reply POST to `<base>/api/httpcommunication/reply` with `njams-reply-for`,
     `njams-receiver=server`, `njams-messagetype=Reply`, `njams-clientid`, the legacy `njams_*` duplicates, body with
     the listener's response (Review Focus 5).
  2. `legacyEventIsAnswered` — name = client path, id = message id → `njams-reply-for` = that id.
  3. `rejectedEventsAreNotAnswered` — blank name, other receiver, other client id, legacy without id, content `xml`,
     unparseable data, `GetRequestHandler` with container mode off → zero `newCall`.
  4. `chunkedEventIsAnsweredOnceComplete` — chunk 1 of 2 → no reply; chunk 2 → one reply.
  5. `largeReplyIsSentInChunks` — `PROPERTY_MAX_MESSAGE_SIZE` = `SplitSupport.MIN_SIZE_LIMIT`, ~24 KB response →
     several POSTs with `njams-chunk-no` 1..n, same `njams-chunk-message-key`; `clientId == null` → no
     `njams-clientid` header.
  6. `failedReplyIsSwallowed` — `execute()` throws `IOException` → no exception.
  7. `onErrorHandling` — socket timeout while connected → still connected, nothing recorded; `IOException` while
     connected → disconnected, recorded once; again while disconnected → not recorded; `Error` while connected →
     recorded `NjamsSdkRuntimeException("Connection failed.")`.
  8. `initValidatesBaseUrl` — missing base URL / `http://bad host:1` → `NjamsSdkRuntimeException`; with and without
     trailing `/` → same reply URL.
  9. `stopReleasesClient` — connected with mock client → disconnected, `client == null`; not connected without
     resources → no-op.
  10. `lifecycleCallbacksAreNoOps` — `onOpen`, `onComment`, `onClosed` don't throw.
- [ ] **Step 2:** Run `mvn test -pl njams-sdk -Dtest='HttpSseReceiverTest,HttpSseReceiverLifecycleTest'` — PASS.
- [ ] **Step 3:** Commit: `SDK-499 #comment Add HTTP SSE receiver tests, completing the coverage tests for the
  SonarCloud gate.`

### Task 7: Verify the coverage gate locally

- [ ] **Step 1:** Run `mvn clean verify -Psonarcloud -pl njams-sdk` — BUILD SUCCESS.
- [ ] **Step 2:** From `njams-sdk/target/site/jacoco-ut/jacoco.xml` and `jacoco-it/jacoco.xml`, merged per class
  (max covered per line/branch), excluding packages `communication/kafka` and `argos`, compute
  `(covered lines + covered branches) / (lines + branches)`. Expected ≥ 80% (≈ 85% estimated). Also report the
  per-class figures for `ExtractHandler`, `SharedReceiverSupport`, the JMS sender/receiver/shared-receiver classes
  of both trees and `HttpSseReceiver`.
- [ ] **Step 3:** Run `mvn validate -Pcheckstyle -pl njams-sdk` and `mvn javadoc:javadoc -pl njams-sdk` — no errors.
- [ ] **Step 4:** Report the figures. If the gate is missed, stop and propose the next-largest gaps — do not add
  tests beyond this plan without confirmation. Task 7 normally commits nothing (no empty commits); the ticket's
  closing comment is posted via `njams-ticket-finish` once the next CI build shows the SonarCloud gate passing.
