# SDK-494 Connection-Failure Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every sender connection failure — at startup and during operation — diagnosable from the log at default level, reported exactly once, with its cause in the message text.

**Architecture:** A configuration error is resolved and validated once, when the sender group is created, so it travels `Njams.startup()`'s existing "could not obtain a sender" ERROR path instead of surfacing deep inside connector thread naming. Everything that remains in the connector is then a genuine connection failure, whose severity is decided by the existing `ConnectionCoordinator.shouldReconnect()` predicate: ERROR when startup dies, WARN when a background reconnect takes over. An outage during operation is reported once, where the pool elects its single reconnector.

**Tech Stack:** Java 11, SLF4J over slf4j-reload4j (log4j 1.x API in tests), JUnit 4, Mockito.

**Spec:** No separate spec document — this plan implements Jira **SDK-494** directly. The ticket carries the summary, details and acceptance criteria; the design questions it left open were settled during kickoff and are recorded in Design Decisions below.

## Global Constraints

- Java 11+, Maven 3.8+. No new third-party dependencies.
- Every commit references `SDK-494`. Intermediate commits must NOT carry `#comment`; only a final, significant commit may.
- No production file is created, so no new copyright headers are needed.
- All `public`/`protected` members changed or added need Javadoc. Verify with `mvn validate -Pcheckstyle -pl njams-sdk` and `mvn javadoc:javadoc -pl njams-sdk`.
- Public API must not change: no signature, return-type or visibility change to an existing `public`/`protected` member. Adding members is allowed.
- `njams.sdk.communication` accepts exactly `HTTP|JMS|KAFKA` per `settings_full.properties`. The sender side has never mapped `HTTPS`, unlike `CommunicationFactory.getReceiver`. Replicate the existing sender resolution exactly; do not add an `HTTPS` mapping.
- Only `SenderLoggingSpecTest` may be modified — the user granted explicit permission because it encodes the logging contract this ticket changes. No other existing test may be altered (`.claude/rules/testing-conventions.md`).
- `communication/` is on the runtime monitoring path: no settings reads per message, no new allocation in `send`/`acquire`.

## Design Decisions

1. **Severity source.** `ConnectionCoordinator.shouldReconnect()` (`!shouldShutdown() && (wasEverConnected || reconnectBeforeConnected)`) decides WARN vs. ERROR. At startup it reduces to the `reconnect` fail-behavior flag. **Revised after the final review:** `reconnectBeforeConnected` is only armed inside `startWithTimeout()`, so during the constructor's pre-warm `shouldReconnect()` is false even under the `reconnect` policy — it would report a startup that then continues as a terminal ERROR. `SenderConnector` therefore also snapshots `StartupFailBehavior.fromSettings(settings).reconnectOnStartupFailure()` at construction and treats it as "a reconnect will follow". A startup connect cancelled by a timeout reports at DEBUG instead, because the exception is the interrupt we sent ourselves; the canceller states the real reason.
2. ~~**Provisional vs. decisive.** The pre-warm connect from the `Njams` constructor is always followed by a decisive retry from `startWithTimeout()`. The pre-warm site logs DEBUG with its trace, the decisive site WARN/ERROR with its trace. That yields "logged (once)" with no new state.~~ **Superseded by the final review.** The premise was wrong for the sender path: there is no "decisive retry" worth running. `awaitStartup()` re-entered `beginConnect()`, so a pre-warm that failed fast produced a *second* startup connect against the same endpoint and a second report of the same failure. The startup connect is now single-shot — `SenderConnector.startupAttempted` is latched by `beginConnect()` and never cleared, and `awaitStartup()` waits on the attempt that already ran instead of starting another. An explicit `beginConnect()` (a second `Njams` joining a shared group) may still start a fresh attempt. This does need new state, which this decision assumed it would not: `startupAttempted`, `startupCancelled`, `startupFailureReported`.
3. **Pre-warm deliberately does not hand off** to the reconnect loop, even under the `reconnect` policy — it keeps the fail-behavior decision with the client. Documented, not changed.
4. **Per-outage reporting** lives in `SenderPool.electReconnector`, reached exactly once per outage from all three pool-side triggers.
5. **Out of scope:** `Njams.startup()`'s summary wording; the "Failed to start … thread" phrasing; a runtime guard against reconnecting on a configuration error — Task 1 makes that case structurally unreachable instead.
6. **One deliberate exception to "reported exactly once."** SDK-491 made `CommunicationFactory`'s "implementation not found" branch log `describeAvailability(...)` at ERROR before throwing (`CommunicationFactory.java:246`). Once Task 1 validates in `NjamsSender`'s constructor, a *typo'd* communication type therefore produces two ERRORs: that availability list, then `Njams.startup()`'s "could not obtain a sender" with the cause. This is kept on purpose — the list of registered providers is the diagnostic SDK-491 exists to provide, and the `startup()` ERROR does not carry it. Do not "fix" it by removing either line. The missing-setting case, which is the one SDK-494 was raised for, is unaffected: that branch has no such logging and still yields exactly one ERROR.

## Review Focus

- **Shared sender group** (`njams.sdk.communication.shared=true`, several `Njams` instances on one `SenderPool`): the per-outage WARN must fire once for the group, not once per sharing instance. Covered in Task 3, Step 1.
- **Shutdown racing a failed startup:** `shouldShutdown()` makes `shouldReconnect()` false, which would otherwise report a shutdown-induced failure as a terminal startup ERROR. Covered in Task 2, Step 1.
- **A `CommunicationFactory` with empty or mocked settings** (`SenderPoolTest` passes a Mockito mock; `SenderConnectorTestAccess` builds a pool with empty settings): group-name resolution must not throw there. Covered in Task 1, Step 5.
- **`reconnect` fail-behavior:** a failed startup connect must log WARN and let startup continue, never ERROR. Covered in Task 2, Step 1.
- **A reconnect attempt failing for a different reason than the trigger** (trigger: connection refused; later: an auth error): the per-attempt DEBUG must carry its own cause, not the trigger's. Covered in Task 3, Step 5.

---

## Tasks

### Task 1: Validate the transport once, and name the group from settings

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/CommunicationFactory.java:218-250`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java:122-128`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java:164-170, 705-712`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/CommunicationFactoryTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `CommunicationFactory.validateSenderConfiguration(): void` — throws `IllegalStateException` with the same messages `getSender()` uses when the setting is absent or no registered sender matches it; creates no sender. `SenderPool.getSenderName(): String` keeps its signature but returns the configured transport, read once at construction.

**Note on the name.** The settings value is what selects the sender (`getSenderLoader().find(s -> s.getName().equalsIgnoreCase(requiredSenderName))`), so the configured string *is* the group's name by definition — `NjamsSender(ClientSettings)` already derives its executor-thread name that way. Nothing needs to build a sender to find out what it is called; only validity has to be established.

- [ ] **Step 1: Write the failing tests**

```java
@Test
public void validateSenderConfigurationAcceptsAConfiguredTransport() {
    ClientSettings settings = ClientSettings.from(
        Collections.singletonMap(NjamsSettings.PROPERTY_COMMUNICATION, "http"));
    new CommunicationFactory(settings).validateSenderConfiguration();
}

@Test
public void validateSenderConfigurationNamesBothSettingKeysWhenUnconfigured() {
    ClientSettings settings = ClientSettings.from(new HashMap<>());
    try {
        new CommunicationFactory(settings).validateSenderConfiguration();
        fail("expected IllegalStateException");
    } catch (IllegalStateException e) {
        assertTrue(e.getMessage().contains(NjamsSettings.PROPERTY_COMMUNICATION));
        assertTrue(e.getMessage().contains(NjamsSettings.PROPERTY_COMMUNICATION_TYPE));
    }
}

@Test
public void validateSenderConfigurationRejectsAnUnknownCommunicationType() {
    ClientSettings settings = ClientSettings.from(
        Collections.singletonMap(NjamsSettings.PROPERTY_COMMUNICATION, "NOPE"));
    try {
        new CommunicationFactory(settings).validateSenderConfiguration();
        fail("expected IllegalStateException");
    } catch (IllegalStateException e) {
        assertTrue(e.getMessage().contains("NOPE"));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=CommunicationFactoryTest -pl njams-sdk`
Expected: FAIL — `validateSenderConfiguration()` does not exist.

- [ ] **Step 3: Implement `CommunicationFactory.validateSenderConfiguration()`**

Public `void` method performing exactly the two checks `getSender()` already makes — setting present, and `getSenderLoader().find(...)` matches — throwing the same `IllegalStateException` messages, but never calling `newInstance()`, `ClasspathValidator.validate()` or `init(settings)`. Have `getSender()` call it first so the two cannot drift. Add the alternative key to the "Unable to find …" message so it matches `getReceiver()`'s wording. Javadoc both.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=CommunicationFactoryTest -pl njams-sdk`
Expected: PASS

- [ ] **Step 5: Read the group name from settings in `SenderPool`, validate eagerly in `NjamsSender`**

In `SenderPool`'s three-argument constructor, read the configured transport once into a `private final String senderName`, with the same `settings.getPropertyWithAlternativeKey(PROPERTY_COMMUNICATION, PROPERTY_COMMUNICATION_TYPE)` call the constructor already uses for the discard policy, falling back to `"unknown"` when it is absent — the no-settings constructors and `SenderPoolTest`'s mocked factory must keep working. `getSenderName()` returns that field and no longer touches the factory, so it can neither allocate a sender nor throw. In `NjamsSender(ClientSettings)`, keep the existing `name` read and add a `new CommunicationFactory(settings).validateSenderConfiguration()` call before `init()`, so a missing or unknown transport fails there rather than inside a connector thread. Update that constructor's Javadoc, which today only describes reading the key.

- [ ] **Step 6: Prove the reported symptom is gone, end to end**

This is the case from the ticket: a client configured with no communication type at all. Capture the `com.im.njams.sdk.Njams` category and assert the configuration error now reaches the client as one actionable ERROR, rather than as a thread-start warning.

```java
@Test
public void startupWithoutACommunicationTypeFailsWithAnActionableError() {
    ClientSettings settings = ClientSettings.from(new HashMap<>());
    settings.put(NjamsSettings.PROPERTY_CONFIGURATION_PROVIDER, "memory");
    Njams njams = new Njams(new Path("test"), "1.0", settings);

    assertEquals(StartupResult.FAIL, njams.startup());
    assertEquals("one actionable error", 1, countAtLevel(njamsAppender, Level.ERROR));
    assertTrue("names the setting at fault",
        messageAtLevelContains(njamsAppender, Level.ERROR, NjamsSettings.PROPERTY_COMMUNICATION));
}
```

Place it in a new `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/StartupConfigurationErrorSpecTest.java` extending `AbstractLifecycleSpecTest`. This task runs before the shared helpers exist, so copy the small `CapturingAppender` pattern and the two count/contains helpers from `SenderLoggingSpecTest` into this class rather than depending on it. Adapt the `Njams` constructor arguments to the signature in this branch.

Attach the appender to the `com.im.njams.sdk.Njams` category specifically, not to a parent. Design Decision 6 means a *typo'd* transport legitimately produces a second ERROR from `com.im.njams.sdk.communication.CommunicationFactory`; a broader category would capture it and make the count-of-one assertion wrong for reasons that are not a defect. This test covers the missing-setting case, which has no such second line.

- [ ] **Step 7: Verify the whole communication suite**

Run: `mvn test -Dtest="com.im.njams.sdk.communication.**" -pl njams-sdk`
Expected: PASS. `SenderPoolTest` (mocked factory) and the lifecycle spec tests must be green.

- [ ] **Step 8: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/ njams-sdk/src/test/java/com/im/njams/sdk/communication/CommunicationFactoryTest.java
git commit -m "SDK-494 Validate the configured transport once at sender creation and name the sender group from the settings, instead of building a throwaway sender for each thread name."
```

---

### Task 2: Report a startup connection failure once, at the right level

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderConnector.java:103-110, 141-170`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java:331-336`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderLoggingSpecTest.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/SenderConnectorTestAccess.java`

**Interfaces:**
- Consumes: Task 1's `SenderPool.getSenderName()`, which no longer throws.
- Produces: `SenderConnectorTestAccess.shutdown(): void` — flags the connector's coordinator as shutting down, for the shutdown-race test.

- [ ] **Step 1: Write the failing tests**

In `SenderLoggingSpecTest`, with the capturing appender on `com.im.njams.sdk.communication.SenderConnector` at DEBUG:

```java
@Test
public void failedStartupUnderFailFastLogsOnceAtError() throws Exception {
    LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
    SenderConnectorTestAccess s = SenderConnectorTestAccess.create();
    s.beginConnect();
    s.awaitStartup(5000);

    assertEquals("startup failure reported exactly once at error", 1, countAtLevel(Level.ERROR));
    assertEquals("and never additionally at warn", 0, countAtLevel(Level.WARN));
    assertTrue("the cause belongs in the message text",
        messageAtLevelContains(Level.ERROR, "LIFECYCLE_TEST"));
}

@Test
public void failedStartupUnderReconnectPolicyLogsOnceAtWarn() throws Exception {
    LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
    SenderConnectorTestAccess s = SenderConnectorTestAccess.createWithReconnectBeforeConnected();
    s.beginConnect();
    s.awaitStartup(5000);

    assertEquals("a recoverable startup failure is not an error", 0, countAtLevel(Level.ERROR));
    assertEquals("reported once at warn", 1, countAtLevel(Level.WARN));
}

@Test
public void startupFailureDuringShutdownIsNotReportedAsTerminal() throws Exception {
    LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
    SenderConnectorTestAccess s = SenderConnectorTestAccess.create();
    s.shutdown();
    s.beginConnect();
    s.awaitStartup(2000);

    assertEquals("a shutdown-induced failure is not a startup error", 0, countAtLevel(Level.ERROR));
}
```

Add a `messageAtLevelContains(Level, String)` helper beside the existing `countMessagesStartingWith`, and a `shutdown()` passthrough on `SenderConnectorTestAccess` delegating to `coordinator.setShouldShutdown(true)`. Task 3 needs the same helpers against a second appender, so give `countAtLevel` and `messageAtLevelContains` an overload taking the `CapturingAppender` as its first argument, with the one-argument forms delegating to it for the connector appender.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=SenderLoggingSpecTest -pl njams-sdk`
Expected: FAIL — the startup failure is logged at DEBUG today, so both counts are 0.

- [ ] **Step 3: Move the decisive report to the predicate-chosen level**

In `runStartupConnect()`'s catch, replace `LOG.debug("Startup connect failed.", e)` with a report at ERROR when `!coordinator.shouldReconnect()` and at WARN otherwise, passing the exception so the trace is kept and putting the cause in the message text through the existing `getExceptionWithCauses(e)`. No "will retry" wording on the ERROR branch. A failure observed while `coordinator.shouldShutdown()` is set is not a startup failure — log that at DEBUG.

- [ ] **Step 4: Demote the provisional pre-warm reports to DEBUG**

In `Njams.beginConnect()`, change the receiver pre-warm `LOG.warn("beginConnect() failed to pre-initialize receiver; start() will retry.", e)` to DEBUG — `startReceiver()` makes the decisive WARN report ~500 ms later, so nothing is lost at default level. In `SenderConnector.beginConnect()`'s `catch (RuntimeException e)`, report at DEBUG as well; after Task 1 this catch only ever sees a genuine thread-start failure, and the decisive site reports it.

- [ ] **Step 5: Reword the two thread-start catch messages**

`SenderConnector.beginConnect()`'s catch still says "the group remains disconnected. A later call will retry." and `startReconnectLocked()`'s twin says "a later trigger will retry." Drop the group clause — the group cannot be named from there and the term is SDK-internal — and drop any retry claim the code does not guarantee. Append the cause through `getExceptionWithCauses(e)` so the reason is in the text. Keep the "failed to start the … thread" phrasing: the thread genuinely did not start, whatever prevented it.

- [ ] **Step 6: Document why a pre-warm failure does not hand off**

Add to `runStartupConnect()`'s Javadoc that the `shouldReconnect()` gate in the `finally` block is deliberately false at construction time, so a reconnect loop started from the constructor never pre-empts the client's fail-behavior decision in `startWithTimeout`.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `mvn test -Dtest=SenderLoggingSpecTest -pl njams-sdk`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/ njams-sdk/src/test/java/com/im/njams/sdk/communication/
git commit -m "SDK-494 Report a failed startup connect once, at error or warn depending on whether a reconnect follows."
```

---

### Task 3: Report an outage once, where the reconnector is elected

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderPool.java:660-675`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/SenderConnector.java:214-241`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderLoggingSpecTest.java`

**Interfaces:**
- Consumes: Task 2's severity handling in `SenderConnector`; `ConnectionCoordinator.wasEverConnected()`, already present.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Write the failing tests**

Attach a second capturing appender to `com.im.njams.sdk.communication.SenderPool` in `setUp`, and drive the outage through the pool so `electReconnector` actually runs:

```java
@Test
public void anOutageIsReportedOnceAtWarnWithItsCause() throws Exception {
    SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
    pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("broker gone"));

    assertEquals("exactly one outage report", 1, countAtLevel(poolAppender, Level.WARN));
    assertTrue("the cause belongs in the message text",
        messageAtLevelContains(poolAppender, Level.WARN, "broker gone"));
}

@Test
public void furtherFailuresWithinOneOutageDoNotReportAgain() throws Exception {
    SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
    pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("broker gone"));
    pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("broker still gone"));

    assertEquals("one outage, one report", 1, countAtLevel(poolAppender, Level.WARN));
}

@Test
public void anOutageOnASharedGroupIsReportedOncePerGroup() throws Exception {
    SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
    pool.addExceptionListener(cause -> { });
    pool.addExceptionListener(cause -> { });
    pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("broker gone"));

    assertEquals("per group, not per listener", 1, countAtLevel(poolAppender, Level.WARN));
}
```

Adapt the listener lambdas to `SenderExceptionListener`'s actual signature.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=SenderLoggingSpecTest -pl njams-sdk`
Expected: FAIL — `SenderPool` logs nothing at WARN today.

- [ ] **Step 3: Report the outage in `electReconnector`**

At the top of `SenderPool.electReconnector(Exception cause, List<SenderExceptionListener> listeners)`, before `connector.startReconnect(cause)`, log one WARN carrying `cause` for the trace and its `toString` chain in the message. Word it from `coordinator.wasEverConnected()`: a lost connection when true, a connect that never succeeded when false. This is the only report for all three pool-side triggers, including the `acquire()` connect failure that is logged nowhere today.

- [ ] **Step 4: Demote the connector's now-duplicate trigger line**

Change `runReconnectLoop`'s `LOG.info("Initialized reconnect, because of: {}", ...)` to DEBUG — Step 3's WARN now carries that information at default level.

- [ ] **Step 5: Log each reconnect attempt's own failure at DEBUG**

`runReconnectLoop`'s catch logs nothing at any level today. Add a `LOG.debug` carrying that attempt's own exception, so an attempt failing for a different reason than the trigger is visible under DEBUG without adding anything at default level.

- [ ] **Step 6: Retarget the existing reconnect assertions**

Both existing tests assert `countMessagesStartingWith(Level.INFO, "Initialized reconnect") == 1` on the `SenderConnector` category. Point them at the new per-outage WARN on the `SenderPool` category, keeping the same intent: reported exactly once, and no more often as retries accumulate. `manyFailedReconnectAttemptsDoNotFloodTheLog`'s `countAtLevel(Level.WARN) == 0` assertion stays as-is against the `SenderConnector` appender — routine retries must still add nothing there.

- [ ] **Step 7: Run the full lifecycle suite**

Run: `mvn test -Dtest="com.im.njams.sdk.communication.lifecycle.**" -pl njams-sdk`
Expected: PASS

- [ ] **Step 8: Verify checkstyle and Javadoc, then commit**

Run: `mvn validate -Pcheckstyle -pl njams-sdk && mvn javadoc:javadoc -pl njams-sdk`
Expected: BUILD SUCCESS, no Javadoc errors.

```bash
git add njams-sdk/src/main/java/com/im/njams/sdk/communication/ njams-sdk/src/test/java/com/im/njams/sdk/communication/lifecycle/SenderLoggingSpecTest.java
git commit -m "SDK-494 Report a sender outage once at warn where the reconnector is elected, covering the previously unreported pool-growth connect failure."
```
