# Settings Interface Rename + toProperties() Elimination Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename `WritableSettings`→`ClientSettings` and `ReadOnlySettings`→`ReadOnlyClientSetting` to clarify intent, then eliminate unnecessary `PropertyUtil.toProperties()` conversions that bypass the settings API within the SDK itself.

**Architecture:** Phase 0 is a pure mechanical rename — update the interface declarations and every reference. Phase 1 replaces the four internal `toProperties()` calls with direct settings API reads; `DataMasking.addPatterns(Properties)` is deprecated in favour of a new `ClientSettings` overload. Phase 2 (separate ticket) extends the boundary into the communication layer's `init()` interfaces.

**Tech Stack:** Java 11, JUnit 4, Mockito, Maven (`mvn test -pl njams-sdk`), Checkstyle (`mvn checkstyle:check -pl njams-sdk`).

---

## Ticket

All phases are tracked under **SDK-433** (fix version 6.0.0). Transition to *In Progress* before starting. Manage the `breaking-change` label: the rename of `WritableSettings` and `ReadOnlySettings` is a public-API breaking change (existing callers must update imports); Phase 1+2 changes to non-public-API members are not.

---

## Phase 0 — Prerequisite: Rename Interfaces

### Task 0.1: Rename `ReadOnlySettings` → `ReadOnlyClientSetting`

**Files:**
- Rename: `njams-sdk/src/main/java/com/im/njams/sdk/settings/ReadOnlySettings.java` → `ReadOnlyClientSetting.java`
- Rename: `njams-sdk/src/test/java/com/im/njams/sdk/settings/ReadOnlySettingsTest.java` → `ReadOnlyClientSettingTest.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/AbstractReadOnlySettings.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/HierarchicalSettings.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/PropertiesBackedSettings.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/ReadOnlyFilteringSettings.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/ReadOnlySettingsImpl.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/Settings.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/SettingsProvider.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/SettingsProviderFactory.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/WritableSettings.java` (`extends ReadOnlySettings` → `extends ReadOnlyClientSetting`)
- Modify (import update): `njams-sdk/src/main/java/com/im/njams/sdk/utils/PropertyUtil.java`
- Modify (import update): all files listed in the grep output below

- [ ] **Step 1: Rename the source file and update its class declaration**

  In `ReadOnlySettings.java`, the only line that needs changing is the interface declaration. Rename the file to `ReadOnlyClientSetting.java` and change:
  ```java
  public interface ReadOnlySettings {
  ```
  to:
  ```java
  public interface ReadOnlyClientSetting {
  ```

- [ ] **Step 2: Rename the test file**

  Rename `ReadOnlySettingsTest.java` → `ReadOnlyClientSettingTest.java`. Inside the file update the class name:
  ```java
  public class ReadOnlyClientSettingTest {
  ```

- [ ] **Step 3: Mass-replace all references**

  Run this PowerShell command from the repo root to replace every import and type reference in production and test sources:
  ```powershell
  Get-ChildItem -Recurse -Include "*.java" "njams-sdk\src" |
    ForEach-Object {
      (Get-Content $_.FullName -Raw) `
        -replace 'import com\.im\.njams\.sdk\.settings\.ReadOnlySettings;',
                 'import com.im.njams.sdk.settings.ReadOnlyClientSetting;' `
        -replace '\bReadOnlySettings\b', 'ReadOnlyClientSetting' |
      Set-Content $_.FullName -NoNewline
    }
  ```

  > **Note:** The second replace (`\bReadOnlySettings\b`) handles `extends`, `implements`, parameter types, return types, and Javadoc `{@link}` references in one pass. It will NOT accidentally touch `ReadOnlySettingsImpl` or `ReadOnlyFilteringSettings` because those do not match the word-boundary pattern.

- [ ] **Step 4: Build and verify**

  ```
  mvn test -pl njams-sdk -q
  ```
  Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 5: Checkstyle**

  ```
  mvn checkstyle:check -pl njams-sdk
  ```
  Expected: no violations.

- [ ] **Step 6: Commit**

  ```
  git add njams-sdk/src
  git commit -m "SDK-433 #comment Rename ReadOnlySettings interface to ReadOnlyClientSetting"
  ```

---

### Task 0.2: Rename `WritableSettings` → `ClientSettings`

**Files:**
- Rename: `njams-sdk/src/main/java/com/im/njams/sdk/settings/WritableSettings.java` → `ClientSettings.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/FilteringWritableSettings.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/HierarchicalSettings.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/PropertiesBackedSettings.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/Settings.java`
- Modify (declaration update): `njams-sdk/src/main/java/com/im/njams/sdk/settings/WritableSettingsImpl.java`
- Modify (import update): `njams-sdk/src/main/java/com/im/njams/sdk/utils/PropertyUtil.java`
- Modify (import update): every other file that imports `WritableSettings`

- [ ] **Step 1: Rename the source file and update its interface declaration**

  Rename `WritableSettings.java` → `ClientSettings.java`. Change:
  ```java
  public interface WritableSettings extends ReadOnlyClientSetting {
  ```
  (The `extends ReadOnlyClientSetting` was already updated by Task 0.1.)

- [ ] **Step 2: Mass-replace all references**

  ```powershell
  Get-ChildItem -Recurse -Include "*.java" "njams-sdk\src" |
    ForEach-Object {
      (Get-Content $_.FullName -Raw) `
        -replace 'import com\.im\.njams\.sdk\.settings\.WritableSettings;',
                 'import com.im.njams.sdk.settings.ClientSettings;' `
        -replace '\bWritableSettings\b', 'ClientSettings' |
      Set-Content $_.FullName -NoNewline
    }
  ```

  > **Note:** The word-boundary pattern will NOT touch `WritableSettingsImpl`, `FilteringWritableSettings`, or `WritableSettingsBuilder` — those names don't end at `WritableSettings` with a word boundary.

  After running, manually verify `FilteringWritableSettings.java` and `WritableSettingsImpl.java` still have their correct class declarations (the class *names* are unchanged; only the `implements`/`extends ClientSettings` clause should have changed).

- [ ] **Step 3: Build and verify**

  ```
  mvn test -pl njams-sdk -q
  ```
  Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 4: Checkstyle**

  ```
  mvn checkstyle:check -pl njams-sdk
  ```

- [ ] **Step 5: Commit**

  ```
  git add njams-sdk/src
  git commit -m "SDK-433 #comment Rename WritableSettings interface to ClientSettings"
  ```

---

## Phase 1 — Eliminate Internal toProperties() Conversions

All four tasks below remove calls to `PropertyUtil.toProperties()` within the SDK's own code. No external library forces these conversions.

---

### Task 1.1: Add `DataMasking.addPatterns(ClientSettings)` + deprecate Properties overload

**Context:** `Njams.initializeDataMasking()` calls `DataMasking.addPatterns(PropertyUtil.toProperties(settings))`. `DataMasking.addPatterns(Properties)` only scans keys by prefix — it does not pass `Properties` to any external library. A new overload taking `ClientSettings` replaces it; the old `Properties` overload is deprecated.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/logmessage/DataMasking.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/Njams.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/logmessage/DataMaskingTest.java`

- [ ] **Step 1: Write the failing test**

  In `DataMaskingTest.java`, add (after the existing `addPatternsFromProperties` test):

  ```java
  @Test
  public void addPatternsFromSettings() {
      DataMasking.clearAllPatterns();
      Settings settings = new Settings();
      settings.put(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX + "ssn", "\\d{3}-\\d{2}-\\d{4}");
      settings.put("njams.sdk.other.key", "irrelevant");

      DataMasking.addPatterns(settings);

      assertEquals(1, DataMasking.getPatterns().size());
  }
  ```

  Required imports: `com.im.njams.sdk.settings.ClientSettings`, `com.im.njams.sdk.settings.Settings`, `com.im.njams.sdk.NjamsSettings`.

- [ ] **Step 2: Run the test to confirm it fails**

  ```
  mvn test -Dtest=DataMaskingTest#addPatternsFromSettings -pl njams-sdk
  ```
  Expected: FAIL — method `addPatterns(ClientSettings)` does not exist yet.

- [ ] **Step 3: Add the new overload to `DataMasking`**

  In `DataMasking.java`, add this method directly above the existing `addPatterns(Properties)` method:

  ```java
  /**
   * Reads all properties whose key starts with
   * {@value com.im.njams.sdk.NjamsSettings#PROPERTY_DATA_MASKING_REGEX_PREFIX}
   * from the given settings and adds them to the data masking list.
   *
   * @param settings the settings to read masking patterns from
   */
  public static void addPatterns(ClientSettings settings) {
      for (Map.Entry<String, String> entry : settings) {
          if (entry.getKey().startsWith(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX)) {
              addPattern(
                  entry.getKey().substring(NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX.length()),
                  entry.getValue());
          }
      }
  }
  ```

  Add the required import: `import com.im.njams.sdk.settings.ClientSettings;`

- [ ] **Step 4: Deprecate the old Properties overload**

  On the existing `addPatterns(Properties properties)` method, add the `@Deprecated` annotation and update its Javadoc:

  ```java
  /**
   * Reads all properties whose key starts with
   * {@value com.im.njams.sdk.NjamsSettings#PROPERTY_DATA_MASKING_REGEX_PREFIX}
   * from the given properties and adds them to the data masking list.
   *
   * @param properties the properties to read masking patterns from
   * @deprecated Use {@link #addPatterns(ClientSettings)} instead.
   */
  @Deprecated
  public static void addPatterns(Properties properties) {
  ```

- [ ] **Step 5: Update the caller in `Njams.java`**

  In `Njams.initializeDataMasking()` (line ~1393), change:
  ```java
  DataMasking.addPatterns(PropertyUtil.toProperties(settings));
  ```
  to:
  ```java
  DataMasking.addPatterns(settings);
  ```

  Remove the `PropertyUtil` import if it is no longer used elsewhere in `Njams.java`.

- [ ] **Step 6: Run tests**

  ```
  mvn test -Dtest=DataMaskingTest -pl njams-sdk
  ```
  Expected: all `DataMaskingTest` tests pass.

- [ ] **Step 7: Full build**

  ```
  mvn test -pl njams-sdk -q
  ```

- [ ] **Step 8: Checkstyle**

  ```
  mvn checkstyle:check -pl njams-sdk
  ```

- [ ] **Step 9: Commit**

  ```
  git add njams-sdk/src/main/java/com/im/njams/sdk/logmessage/DataMasking.java \
          njams-sdk/src/main/java/com/im/njams/sdk/Njams.java \
          njams-sdk/src/test/java/com/im/njams/sdk/logmessage/DataMaskingTest.java
  git commit -m "SDK-433 #comment Add DataMasking.addPatterns(ClientSettings), deprecate Properties overload"
  ```

---

### Task 1.2: Read settings directly in `ArgosSender.init()`

**Context:** `ArgosSender.init(ClientSettings)` converts settings to `Properties` only to read 3 values (host, port, enabled). No external library is involved. `ReadOnlyClientSetting` already has `getPropertyWithDeprecationWarning(expectedKey, defaultValue, deprecatedKey)` which replicates the deprecated-key fallback.

**Note:** The existing `getProperty(Properties, key, default)` helper uses `key.replace("\\.sdk\\.", ".client.")` — the backslash escapes are Java string literals that result in the literal string `\.sdk\.`. Since property keys contain regular `.sdk.` (no backslashes), this replace never matches, making the deprecated-key fallback currently dead code. The migration below fixes this silently by using the correct string `".sdk."`.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/argos/ArgosSender.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/argos/ArgosSenderTest.java`

- [ ] **Step 1: Write a failing test for deprecated-key fallback**

  In `ArgosSenderTest.java`, add:

  ```java
  @Test
  public void initWithDeprecatedKeys() {
      ArgosSender sender = new ArgosSender();
      Settings settings = new Settings();
      // use the old .client. key names instead of the current .sdk. ones
      settings.put("njams.client.subagent.host", "legacy-host");
      settings.put("njams.client.subagent.port", "4711");
      settings.put("njams.client.subagent.enabled", "true");

      sender.init(settings);

      // reflect to read private fields
      assertEquals("legacy-host", getField(sender, "host"));
      assertEquals(4711, getField(sender, "port"));
      assertTrue((Boolean) getField(sender, "enabled"));
  }

  private static Object getField(Object target, String name) throws Exception {
      java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      return f.get(target);
  }
  ```

  (The test will initially pass only if the deprecated fallback already works, which it currently does not. Run it to confirm the current state.)

- [ ] **Step 2: Run to establish current baseline**

  ```
  mvn test -Dtest=ArgosSenderTest -pl njams-sdk
  ```
  Note whether `initWithDeprecatedKeys` passes or fails. It is expected to fail (deprecated keys not recognized).

- [ ] **Step 3: Replace Properties conversion in `ArgosSender.init()`**

  In `ArgosSender.java`, replace the body of `init(ClientSettings settings)`:

  ```java
  public synchronized void init(ClientSettings settings) {
      if (isInitialized) {
          LOG.debug("ArgosSender already initialized.");
          return;
      }
      LOG.debug("Initialize ArgosSender.");
      enabled = Boolean.parseBoolean(settings.getPropertyWithDeprecationWarning(
          NjamsSettings.PROPERTY_ARGOS_SUBAGENT_ENABLED, DEFAULT_ENABLED,
          NjamsSettings.PROPERTY_ARGOS_SUBAGENT_ENABLED.replace(".sdk.", ".client.")));
      host = settings.getPropertyWithDeprecationWarning(
          NjamsSettings.PROPERTY_ARGOS_SUBAGENT_HOST, DEFAULT_HOST,
          NjamsSettings.PROPERTY_ARGOS_SUBAGENT_HOST.replace(".sdk.", ".client."));
      final String portStr = settings.getPropertyWithDeprecationWarning(
          NjamsSettings.PROPERTY_ARGOS_SUBAGENT_PORT, String.valueOf(DEFAULT_PORT),
          NjamsSettings.PROPERTY_ARGOS_SUBAGENT_PORT.replace(".sdk.", ".client."));
      try {
          port = Integer.parseInt(portStr);
      } catch (NumberFormatException e) {
          LOG.debug("Could not parse property: ", e);
          LOG.warn("Could not parse property {} to an Integer. Using default port {} instead.",
              NjamsSettings.PROPERTY_ARGOS_SUBAGENT_PORT, DEFAULT_PORT);
          port = DEFAULT_PORT;
      }
      isInitialized = true;
  }
  ```

  Remove the private `getProperty(Properties, String, String)` helper method — it is now unused.

  Remove the `import java.util.Properties;` import if it is no longer used.

- [ ] **Step 4: Run tests**

  ```
  mvn test -Dtest=ArgosSenderTest -pl njams-sdk
  ```
  Expected: all tests pass including `initWithDeprecatedKeys`.

- [ ] **Step 5: Full build + checkstyle**

  ```
  mvn test -pl njams-sdk -q && mvn checkstyle:check -pl njams-sdk
  ```

- [ ] **Step 6: Commit**

  ```
  git add njams-sdk/src/main/java/com/im/njams/sdk/argos/ArgosSender.java \
          njams-sdk/src/test/java/com/im/njams/sdk/argos/ArgosSenderTest.java
  git commit -m "SDK-433 #comment Read settings directly in ArgosSender, remove Properties conversion"
  ```

---

### Task 1.3: Remove Properties from `MaxQueueLengthHandler` and `NjamsSender.init()`

**Context:** `NjamsSender(ClientSettings)` calls `init(PropertyUtil.toProperties(settings))`. Inside `init(Properties)`, every value is already read from `this.settings` *except* the `Properties` object passed to `MaxQueueLengthHandler`. `MaxQueueLengthHandler` reads only `PROPERTY_DISCARD_POLICY` from it. No external library is involved.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/MaxQueueLengthHandler.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/NjamsSenderTest.java`

- [ ] **Step 1: Write a failing test for `MaxQueueLengthHandler` with `ClientSettings`**

  In `NjamsSenderTest.java`, check how `MaxQueueLengthHandler` is currently constructed in tests (via reflection, look for `getRejectedExecutionHandler()`). Add a focused unit test:

  ```java
  @Test
  public void maxQueueLengthHandlerReadsDiscardPolicyFromSettings() {
      Settings settings = new Settings();
      settings.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "discard");
      // Construct NjamsSender — this internally creates MaxQueueLengthHandler
      // We verify the handler was initialized with the correct policy
      NjamsSender sender = new NjamsSender(settings);
      ThreadPoolExecutor executor = (ThreadPoolExecutor) getField(sender, "executor");
      MaxQueueLengthHandler handler = (MaxQueueLengthHandler) executor.getRejectedExecutionHandler();
      assertNotNull(handler);
      // trigger rejection on a saturated executor and verify discard behaviour (no exception thrown)
      executor.shutdown();
      handler.rejectedExecution(() -> {}, executor);
  }
  ```

  This test currently passes (because the existing `Properties`-based path works). Running it establishes a baseline that must still pass after the refactoring.

- [ ] **Step 2: Run baseline**

  ```
  mvn test -Dtest=NjamsSenderTest -pl njams-sdk
  ```
  Expected: all existing tests pass.

- [ ] **Step 3: Change `MaxQueueLengthHandler` constructor to accept `ClientSettings`**

  In `MaxQueueLengthHandler.java`, replace:
  ```java
  import static com.im.njams.sdk.utils.PropertyUtil.getPropertyWithDeprecationWarning;
  import java.util.Properties;
  ```
  with:
  ```java
  import com.im.njams.sdk.settings.ClientSettings;
  ```

  Change the constructor:
  ```java
  @SuppressWarnings("removal")
  public MaxQueueLengthHandler(final ClientSettings settings, final Supplier<Boolean> isConnectionLost) {
      discardPolicy = DiscardPolicy.byValue(settings.getPropertyWithDeprecationWarning(
          NjamsSettings.PROPERTY_DISCARD_POLICY, NjamsSettings.OLD_DISCARD_POLICY));
      this.isConnectionLost = isConnectionLost;
  }
  ```

- [ ] **Step 4: Update `NjamsSender.init()` to remove the Properties parameter**

  In `NjamsSender.java`, change the constructor:
  ```java
  public NjamsSender(ClientSettings settings) {
      this.settings = settings;
      name = settings.getProperty(NjamsSettings.PROPERTY_COMMUNICATION);
      init();
  }
  ```

  Change `init(Properties properties)` to `init()` (remove the parameter entirely):
  ```java
  public void init() {
      int minSenderThreads =
          (int) settings.getLongWithDeprecationWarning(PROPERTY_MIN_SENDER_THREADS, 1, OLD_MIN_SENDER_THREADS);
      int maxSenderThreads =
          (int) settings.getLongWithDeprecationWarning(PROPERTY_MAX_SENDER_THREADS, 8, OLD_MAX_SENDER_THREADS);
      int maxQueueLength =
          (int) settings.getLongWithDeprecationWarning(PROPERTY_MAX_QUEUE_LENGTH, 8, OLD_MAX_QUEUE_LENGTH);
      long idleTime =
          settings.getLongWithDeprecationWarning(PROPERTY_SENDER_THREAD_IDLE_TIME, 10000, OLD_SENDER_THREAD_IDLE_TIME);
      LOG.debug("Init thread pool (min={}, max={}, queue={}, idle={})", minSenderThreads, maxSenderThreads,
          maxQueueLength, idleTime);
      validateThreadPool(minSenderThreads, maxSenderThreads, maxQueueLength, idleTime);
      ThreadFactory threadFactory = new ThreadFactoryBuilder()
          .setNamePrefix(getName() + "-Sender-Thread").setDaemon(true).build();
      final CommunicationFactory communicationFactory = new CommunicationFactory(settings);
      senderPool = new SenderPool(communicationFactory);
      executor = new ThreadPoolExecutor(minSenderThreads, maxSenderThreads, idleTime, TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(maxQueueLength), threadFactory,
          new MaxQueueLengthHandler(settings, senderPool::isConnectionFailure));
  }
  ```

  Update the Javadoc on `init()` to remove the `@param properties` tag and update the description.

  Remove `import java.util.Properties;` if no longer used.

- [ ] **Step 5: Run tests**

  ```
  mvn test -Dtest=NjamsSenderTest -pl njams-sdk
  ```
  Expected: all tests pass.

- [ ] **Step 6: Full build + checkstyle**

  ```
  mvn test -pl njams-sdk -q && mvn checkstyle:check -pl njams-sdk
  ```

- [ ] **Step 7: Commit**

  ```
  git add njams-sdk/src/main/java/com/im/njams/sdk/communication/MaxQueueLengthHandler.java \
          njams-sdk/src/main/java/com/im/njams/sdk/communication/NjamsSender.java \
          njams-sdk/src/test/java/com/im/njams/sdk/communication/NjamsSenderTest.java
  git commit -m "SDK-433 #comment Remove Properties from MaxQueueLengthHandler and NjamsSender.init()"
  ```

---

### Task 1.4: Reverse delegation in `SplitSupport` — `ClientSettings` constructor becomes canonical

**Context:** `SplitSupport` has two constructors: `SplitSupport(ClientSettings, int)` and `SplitSupport(Properties, int)`. Currently the `ClientSettings` constructor delegates to the `Properties` constructor via `PropertyUtil.toProperties()`. All production transport callers (`HttpSender`, `HttpSseReceiver`, `JmsReceiver`, `JmsSender`, `KafkaReceiver`, `KafkaSender`) use the `Properties` constructor directly — they receive `Properties` from `AbstractSender.init(Properties)`. The `ClientSettings` constructor is only called from tests. Reversing the delegation removes the unnecessary conversion for tests and positions the code correctly for Phase 2.

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/fragments/SplitSupport.java`
- Test: `njams-sdk/src/test/java/com/im/njams/sdk/communication/fragments/SplitSupportTest.java`

- [ ] **Step 1: Run existing tests to establish baseline**

  ```
  mvn test -Dtest=SplitSupportTest -pl njams-sdk
  ```
  Expected: all tests pass.

- [ ] **Step 2: Restructure `SplitSupport` constructors**

  The canonical implementation moves to the `ClientSettings` constructor. The `Properties` constructor becomes a thin bridge that converts to `ClientSettings` and delegates.

  In `SplitSupport.java`, replace the two constructors with:

  ```java
  /**
   * Constructor that initializes this instance from the given {@link ClientSettings}.
   *
   * @param settings  the settings for this instance
   * @param techLimit technical maximum message size imposed by the transport, or &lt;= 0 if none
   */
  public SplitSupport(final ClientSettings settings, final int techLimit) {
      final String transport = settings.getProperty(NjamsSettings.PROPERTY_COMMUNICATION);

      final int configuredLimit = settings.getInt(NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, -1);
      if (settings.getBool(TESTING_NO_LIMIT_CHECKS, false)) {
          maxMessageBytes = configuredLimit;
      } else {
          maxMessageBytes = Math.max(0, resolveLimit(configuredLimit, techLimit));
      }
      if (maxMessageBytes > 0) {
          LOG.info("Limiting max message size to {} bytes", maxMessageBytes);
      }

      if (HttpSender.NAME.equalsIgnoreCase(transport) || "HTTPS".equalsIgnoreCase(transport)) {
          chunkNoHeader = NJAMS_CHUNK_NO_HTTP_HEADER;
          chunksHeader = NJAMS_CHUNKS_HTTP_HEADER;
          chunkMessageKeyHeader = NJAMS_CHUNK_MESSAGE_KEY_HTTP_HEADER;
          LOG.debug("Using nginx compatible http headers.");
      } else {
          chunkNoHeader = NJAMS_CHUNK_NO_HEADER;
          chunksHeader = NJAMS_CHUNKS_HEADER;
          chunkMessageKeyHeader = NJAMS_CHUNK_MESSAGE_KEY_HEADER;
          LOG.debug("Using common message properties.");
      }
  }

  /**
   * Constructor that initializes this instance from the given {@link Properties}.
   * Used by transport implementations whose {@code init(Properties)} method has not yet
   * been migrated to {@link ClientSettings}.
   *
   * @param properties the properties for this instance
   * @param techLimit  technical maximum message size imposed by the transport, or &lt;= 0 if none
   */
  public SplitSupport(final Properties properties, final int techLimit) {
      this(ClientSettings.from(properties), techLimit);
  }
  ```

  > **Note:** `ClientSettings.from(properties)` is the existing static factory method (renamed from `WritableSettings.from(Properties)` in Phase 0) that wraps a `Properties` in a settings object.

- [ ] **Step 3: Remove now-unused PropertyUtil import if applicable**

  Check the imports in `SplitSupport.java` and remove `PropertyUtil` if no other method in the file uses it.

- [ ] **Step 4: Run tests**

  ```
  mvn test -Dtest=SplitSupportTest -pl njams-sdk
  ```
  Expected: all tests pass.

- [ ] **Step 5: Full build + checkstyle**

  ```
  mvn test -pl njams-sdk -q && mvn checkstyle:check -pl njams-sdk
  ```

- [ ] **Step 6: Commit**

  ```
  git add njams-sdk/src/main/java/com/im/njams/sdk/communication/fragments/SplitSupport.java
  git commit -m "SDK-433 #comment Reverse SplitSupport constructor delegation to eliminate toProperties() conversion"
  ```

---

## Phase 2 — Migrate Receiver/AbstractSender init() Interfaces *(Separate ticket)*

> This phase requires its own Jira ticket. Do not start until Phase 0 and Phase 1 are committed and passing.

**Goal:** Change `Receiver.init(Properties)` and `AbstractSender.init(Properties)` to accept `ClientSettings`. Each transport implementation is then responsible for converting to `Properties` at the point it actually calls an external library (e.g., `new KafkaProducer<>(PropertyUtil.toProperties(kafkaSettings))`).

**After this phase, `PropertyUtil.toProperties()` will only appear inside Kafka transport code**, where it is required by Kafka's `KafkaProducer(Properties)` constructor.

---

### Task 2.1: Change `Receiver.init()` signature

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/Receiver.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractReceiver.java`

- [ ] **Step 1: Update `Receiver` interface**

  In `Receiver.java`, change:
  ```java
  void init(Properties properties);
  ```
  to:
  ```java
  void init(ClientSettings settings);
  ```

  Update the import: remove `java.util.Properties`, add `com.im.njams.sdk.settings.ClientSettings`.

- [ ] **Step 2: Update `AbstractReceiver`**

  `AbstractReceiver` stores `Properties properties` as a field and implements `init(Properties)`. Change the field to `ClientSettings settings` and update the `init` method:

  ```java
  protected ClientSettings settings;

  @Override
  public void init(ClientSettings settings) {
      this.settings = settings;
  }
  ```

  Update imports accordingly.

- [ ] **Step 3: Verify compilation (build will fail until transport implementations are updated)**

  ```
  mvn compile -pl njams-sdk 2>&1 | grep "error:"
  ```
  Expected: compile errors in `JmsReceiver`, `KafkaReceiver`, `HttpSseReceiver`, `SharedJmsReceiver`, `SharedHttpSseReceiver`, `SharedKafkaReceiver`. Note them — Task 2.3–2.5 will fix them.

---

### Task 2.2: Change `AbstractSender.init()` signature

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/AbstractSender.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/CommunicationFactory.java`

- [ ] **Step 1: Update `AbstractSender`**

  `AbstractSender` has `protected Properties properties` as a field and `public void init(Properties properties)`. Change:

  ```java
  protected ClientSettings settings;

  public void init(ClientSettings settings) {
      this.settings = settings;
  }
  ```

  Update imports.

- [ ] **Step 2: Update `CommunicationFactory`**

  In `CommunicationFactory.createReceiver()` (line ~128), change:
  ```java
  Properties properties = PropertyUtil.toProperties(settings);
  properties.setProperty(INTERNAL_PROPERTY_CLIENTPATH, njams.getClientPath().toString());
  ...
  receiver.init(properties);
  ```
  to:
  ```java
  ClientSettings receiverSettings = ClientSettings.from(settings);
  receiverSettings.put(INTERNAL_PROPERTY_CLIENTPATH, njams.getClientPath().toString());
  ...
  receiver.init(receiverSettings);
  ```

  In `CommunicationFactory.getSender()` (line ~183), change:
  ```java
  newInstance.init(PropertyUtil.toProperties(settings));
  ```
  to:
  ```java
  newInstance.init(settings);
  ```

  Remove `import java.util.Properties;` and the `PropertyUtil.toProperties` call site.

---

### Task 2.3: Update JMS transport implementations

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/JmsSender.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/JmsReceiver.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/jms/SharedJmsReceiver.java`

- [ ] **Step 1: Update `JmsSender.init()`**

  Change the signature:
  ```java
  public void init(ClientSettings settings) {
      super.init(settings);
      ...
  }
  ```

  Replace all `properties.getProperty(key)` / `properties.getProperty(key, default)` calls with `settings.getProperty(key)` / `settings.getProperty(key, default)`. JmsSender only reads individual keys — no `Properties` object is passed to any JMS library call.

  Remove `import java.util.Properties;`, add `import com.im.njams.sdk.settings.ClientSettings;`.

  Update the `SplitSupport` constructor call from `new SplitSupport(properties, -1)` to `new SplitSupport(settings, -1)`.

- [ ] **Step 2: Update `JmsReceiver.init()`**

  Same pattern as `JmsSender`. The stored `private Properties properties` field changes to `ClientSettings settings` (or relies on `AbstractReceiver.settings`). All `properties.getProperty(...)` calls become `settings.getProperty(...)`.

  The `SplitSupport` call changes from `new SplitSupport(props, -1)` to `new SplitSupport(settings, -1)`.

- [ ] **Step 3: Update `SharedJmsReceiver`**

  Update `init()` signature and any references to match.

- [ ] **Step 4: Run JMS-related tests**

  ```
  mvn test -pl njams-sdk -Dtest="Jms*" -q
  ```
  Expected: all pass (JMS tests may be limited since an actual broker is required; at minimum compilation must succeed).

---

### Task 2.4: Update Kafka transport implementations

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/kafka/KafkaSender.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/kafka/KafkaReceiver.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/kafka/SharedKafkaReceiver.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/kafka/KafkaUtil.java`

**Key difference from JMS:** Kafka requires a `Properties` object at the point it calls `new KafkaProducer<>(kafkaProperties)` and `KafkaUtil.filterKafkaProperties(njamsProperties, ...)`. These are the *only* remaining legitimate uses of `Properties` in the Kafka path.

- [ ] **Step 1: Update `KafkaSender.init()`**

  Change signature:
  ```java
  public void init(final ClientSettings settings) {
      super.init(settings);
      kafkaProperties = KafkaUtil.filterKafkaProperties(PropertyUtil.toProperties(settings), ClientType.PRODUCER);
      ...
  }
  ```

  `KafkaUtil.filterKafkaProperties` still takes `Properties` — that is correct. `PropertyUtil.toProperties(settings)` converts here, at the actual boundary with the Kafka library. This is the intended location for the conversion.

  Update `SplitSupport` call: `new SplitSupport(settings, getProducerLimit(kafkaProperties))`.

- [ ] **Step 2: Update `KafkaReceiver.init()`**

  Change signature:
  ```java
  public void init(final ClientSettings settings) {
      super.init(settings);
      final Properties njamsProperties = PropertyUtil.toProperties(settings);
      // njamsProperties is kept as a local; only used to call KafkaUtil.filterKafkaProperties
      // and CommandsConsumer which both require Properties for Kafka library calls
      ...
  }
  ```

  Specifically, after migration the stored `this.njamsProperties` field (currently `Properties`) should be converted to `ClientSettings`. All direct `properties.getProperty(...)` reads become `settings.getProperty(...)`. Only the `KafkaUtil.filterKafkaProperties(...)` and `CommandsConsumer(Properties, ...)` calls still receive a `Properties` built from `PropertyUtil.toProperties(settings)` at that point.

- [ ] **Step 3: Update `KafkaUtil.testTopics()` if needed**

  `KafkaUtil.testTopics(Properties, ...)` passes `Properties` to `filterKafkaProperties` which creates a Kafka client. This can stay as-is since it is called from Kafka transport code that now has `PropertyUtil.toProperties()` locally available.

- [ ] **Step 4: Update `SharedKafkaReceiver`**

  Update `init()` signature.

- [ ] **Step 5: Run Kafka-related tests**

  ```
  mvn test -pl njams-sdk -Dtest="Kafka*" -q
  ```

---

### Task 2.5: Update HTTP transport implementations

**Files:**
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/http/HttpSender.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/http/HttpSseReceiver.java`
- Modify: `njams-sdk/src/main/java/com/im/njams/sdk/communication/http/SharedHttpSseReceiver.java`

HTTP transport reads individual properties and does not pass a `Properties` object to any HTTP client library. The migration is the same pattern as JMS.

- [ ] **Step 1: Update `HttpSender.init()`**

  Change signature to `init(ClientSettings settings)`. Replace `properties.getProperty(...)` with `settings.getProperty(...)`. Update `SplitSupport` call: `new SplitSupport(settings, getMaxMessageSize(settings))` (adjust `getMaxMessageSize` to accept `ClientSettings`).

- [ ] **Step 2: Update `HttpSseReceiver.init()`**

  Same pattern. Update `SplitSupport` call.

- [ ] **Step 3: Update `SharedHttpSseReceiver`**

  Update `init()` signature.

- [ ] **Step 4: Run HTTP-related tests**

  ```
  mvn test -pl njams-sdk -Dtest="Http*" -q
  ```

---

### Task 2.6: Final build + commit Phase 2

- [ ] **Step 1: Full build**

  ```
  mvn test -pl njams-sdk -q
  ```
  Expected: BUILD SUCCESS.

- [ ] **Step 2: Verify `PropertyUtil.toProperties` remaining usages**

  ```
  grep -rn "PropertyUtil\.toProperties" njams-sdk/src/main/java
  ```
  Expected output: only occurrences inside `communication/kafka/KafkaSender.java` and `communication/kafka/KafkaReceiver.java`. No other production code should reference `toProperties`.

- [ ] **Step 3: Checkstyle**

  ```
  mvn checkstyle:check -pl njams-sdk
  ```

- [ ] **Step 4: Commit**

  ```
  git add njams-sdk/src
  git commit -m "SDK-433 #comment Migrate Receiver/AbstractSender init() from Properties to ClientSettings"
  ```
