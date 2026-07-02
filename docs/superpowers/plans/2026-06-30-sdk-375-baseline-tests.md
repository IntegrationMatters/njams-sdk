# SDK-375 Baseline Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish a real-behavior baseline regression net for the sender communication path (JMS + HTTP) that pins the *stable, surviving* contract — send/receive correctness, delivery resumes after a transient mid-processing connection loss, and clean shutdown — **green on current code**, before any SDK-375 refactoring.

**Architecture:** Tests drive the real `NjamsSender` pipeline (executor → `SenderPool` → `AbstractSender` → real transport) against a **real endpoint**: an embedded ActiveMQ `vm://` broker for JMS, and a JDK `com.sun.net.httpserver.HttpServer` for HTTP. Receipt is asserted by consuming from the real broker queue / recording POSTed request bodies. One additional full-`Njams` JMS end-to-end smoke covers the public client path (start → job → stop). A tiny poll-until-deadline helper avoids fixed `Thread.sleep` timing.

**Tech Stack:** JUnit 4, Mockito (already present), `org.apache.activemq:activemq-client` (present) + `org.apache.activemq:activemq-broker` (new, test scope), JDK `HttpServer` (no new dep), OkHttp (already the HTTP transport).

## Global Constraints

- **Branch base:** `SDK-375` off `6.0-dev`; commit messages reference `SDK-375`.
- **Baseline-first:** these tests must pass on the **current, unmodified** production code. They pin only the *stable* contract and **must not** assert Phase-1/Phase-3 behaviors that SDK-375 will change (no assertions on startup-failure propagation, reconnect-only-after-connected, or no-reconnect-during-shutdown).
- **No new production dependencies.** New **test-scope** dependency `activemq-broker` only; use the version already managed for `activemq-client` (same ActiveMQ version — do **not** hardcode a different one).
- **No fixed-`sleep` timing assertions.** Poll for a condition with a deadline (helper provided in Task 2).
- **Test files need no copyright header** (per `CLAUDE.md`).
- **Kafka is out of scope** for baseline (relaxed per Decision 2).
- Commit after every task.

---

## File structure

- `njams-sdk/pom.xml` — add `activemq-broker` test dependency (Task 1).
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/Await.java` — poll-until-deadline helper (Task 2).
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/EmbeddedActiveMqBroker.java` — JUnit `ExternalResource` starting/stopping a `vm://` broker (Task 2).
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/EmbeddedActiveMqJmsFactory.java` — test `JmsFactory` returning an `ActiveMQConnectionFactory` to the embedded broker (Task 2).
- `njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.jms.factory.JmsFactory` — register the factory (Task 2, modify).
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/JmsSenderBaselineIT.java` — JMS send/receive, reconnect, shutdown (Tasks 3–5).
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/JmsClientEndToEndBaselineIT.java` — full-`Njams` JMS smoke (Task 6).
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/IngestHttpServer.java` — JDK `HttpServer` recording ingest POSTs, answering `HEAD` (Task 7).
- `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/HttpSenderBaselineIT.java` — HTTP send/receive, reconnect, shutdown (Tasks 8–10).

> Naming: `*IT` marks these as integration tests. They still run under Surefire here (the project has no Failsafe binding); the suffix is documentation. Package `communication.it` keeps them isolated from the existing unit tests.

---

### Task 1: Add the `activemq-broker` test dependency

**Files:**
- Modify: `njams-sdk/pom.xml` (dependencies block, near the existing `activemq-client` entry ~line 424-428)

**Interfaces:**
- Produces: `org.apache.activemq.broker.BrokerService` and `org.apache.activemq.ActiveMQConnectionFactory` available on the test classpath.

- [ ] **Step 1: Add the dependency**

In `njams-sdk/pom.xml`, immediately after the existing `activemq-client` test dependency, add (do **not** add a `<version>` — inherit the same managed version as `activemq-client`; if `activemq-client` specifies an explicit version, copy that exact value here):

```xml
<dependency>
    <groupId>org.apache.activemq</groupId>
    <artifactId>activemq-broker</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Verify it resolves and the versions match**

Run: `mvn -q dependency:tree -pl njams-sdk -Dincludes=org.apache.activemq:activemq-broker`
Expected: `activemq-broker` appears in the tree at the **same version** as `activemq-client`.

- [ ] **Step 3: Compile test sources**

Run: `mvn -q -pl njams-sdk test-compile`
Expected: BUILD SUCCESS (no code yet uses it; this only confirms the dependency resolves).

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/pom.xml
git commit -m "SDK-375 Add activemq-broker test dependency for baseline integration tests"
```

---

### Task 2: JMS test harness — embedded broker, JMS factory, await helper

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/Await.java`
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/EmbeddedActiveMqBroker.java`
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/EmbeddedActiveMqJmsFactory.java`
- Modify: `njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.jms.factory.JmsFactory`

**Interfaces:**
- Produces:
  - `Await.until(java.util.function.BooleanSupplier condition, long timeoutMs)` → `boolean` (true if condition met before deadline; polls every 25 ms).
  - `EmbeddedActiveMqBroker` JUnit `@Rule` (extends `org.junit.rules.ExternalResource`): `start()`/`stop()` a `vm://` broker named `EmbeddedActiveMqBroker.BROKER_NAME`; methods `restart()`, `stopBroker()`, `startBroker()`, and `String brokerUrl()`.
  - `EmbeddedActiveMqBroker.BROKER_NAME` = `"sdk375-baseline"`; `EmbeddedActiveMqBroker.BROKER_URL` = `"vm://sdk375-baseline?create=false&waitForStart=5000"`.
  - `EmbeddedActiveMqJmsFactory` (SPI `JmsFactory`), `getName()` = `"EmbeddedActiveMq"`, returns `new ActiveMQConnectionFactory(EmbeddedActiveMqBroker.BROKER_URL)`.

- [ ] **Step 1: Write the await helper**

Create `Await.java`:

```java
package com.im.njams.sdk.communication.it;

import java.util.function.BooleanSupplier;

/** Polls a condition until it becomes true or a deadline passes. Avoids fixed-duration sleeps in tests. */
final class Await {
    private Await() {
    }

    static boolean until(BooleanSupplier condition, long timeoutMs) {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return condition.getAsBoolean();
            }
        }
        return condition.getAsBoolean();
    }
}
```

- [ ] **Step 2: Write the embedded broker rule**

Create `EmbeddedActiveMqBroker.java`:

```java
package com.im.njams.sdk.communication.it;

import org.apache.activemq.broker.BrokerService;
import org.junit.rules.ExternalResource;

/**
 * Starts an in-process, non-persistent ActiveMQ broker reachable via the vm:// transport.
 * Supports stop/start to simulate a transient outage.
 */
public class EmbeddedActiveMqBroker extends ExternalResource {

    public static final String BROKER_NAME = "sdk375-baseline";
    public static final String BROKER_URL = "vm://" + BROKER_NAME + "?create=false&waitForStart=5000";

    private BrokerService broker;

    public String brokerUrl() {
        return BROKER_URL;
    }

    public void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName(BROKER_NAME);
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setDeleteAllMessagesOnStartup(true);
        broker.start();
        broker.waitUntilStarted();
    }

    public void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
            broker = null;
        }
    }

    public void restart() throws Exception {
        stopBroker();
        startBroker();
    }

    @Override
    protected void before() throws Throwable {
        startBroker();
    }

    @Override
    protected void after() {
        try {
            stopBroker();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stop embedded broker", e);
        }
    }
}
```

- [ ] **Step 3: Write the test JMS factory**

Create `EmbeddedActiveMqJmsFactory.java`:

```java
package com.im.njams.sdk.communication.it;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.settings.ClientSettings;

/** JmsFactory (selected via PROPERTY_JMS_CONNECTION_FACTORY=EmbeddedActiveMq) pointing at the embedded vm:// broker. */
public class EmbeddedActiveMqJmsFactory implements JmsFactory {

    public static final String NAME = "EmbeddedActiveMq";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(ClientSettings settings) {
        // nothing to initialize
    }

    @Override
    public ConnectionFactory createConnectionFactory() {
        return new ActiveMQConnectionFactory(EmbeddedActiveMqBroker.BROKER_URL);
    }
}
```

- [ ] **Step 4: Register the factory in SPI**

Modify `njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.jms.factory.JmsFactory` to append the new line (keep the existing two):

```
com.im.njams.sdk.communication.jms.FailingJmsFactory
com.im.njams.sdk.communication.jms.NoopJmsFactory
com.im.njams.sdk.communication.it.EmbeddedActiveMqJmsFactory
```

- [ ] **Step 5: Write a smoke test that the harness starts a broker and a raw JMS round-trip works**

Create `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/HarnessSmokeIT.java`:

```java
package com.im.njams.sdk.communication.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.junit.Rule;
import org.junit.Test;

public class HarnessSmokeIT {

    @Rule
    public EmbeddedActiveMqBroker broker = new EmbeddedActiveMqBroker();

    @Test
    public void rawJmsRoundTripThroughEmbeddedBroker() throws Exception {
        ConnectionFactorySupport cf = new ConnectionFactorySupport(broker.brokerUrl());
        try (Connection connection = cf.factory().createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("smoke.queue");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createTextMessage("hello"));
            MessageConsumer consumer = session.createConsumer(queue);
            TextMessage received = (TextMessage) consumer.receive(2000);
            assertNotNull("expected a message", received);
            assertEquals("hello", received.getText());
        }
    }
}
```

Create the small support type `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/ConnectionFactorySupport.java` (reused by later JMS assertions):

```java
package com.im.njams.sdk.communication.it;

import org.apache.activemq.ActiveMQConnectionFactory;

/** Convenience holder for an ActiveMQ ConnectionFactory used by test-side consumers. */
final class ConnectionFactorySupport {
    private final ActiveMQConnectionFactory factory;

    ConnectionFactorySupport(String brokerUrl) {
        this.factory = new ActiveMQConnectionFactory(brokerUrl);
    }

    ActiveMQConnectionFactory factory() {
        return factory;
    }
}
```

- [ ] **Step 6: Run the smoke test**

Run: `mvn -q -pl njams-sdk test -Dtest=HarnessSmokeIT`
Expected: PASS — confirms the embedded broker and raw JMS round-trip work.

- [ ] **Step 7: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/it/ njams-sdk/src/test/resources/META-INF/services/com.im.njams.sdk.communication.jms.factory.JmsFactory
git commit -m "SDK-375 Add JMS baseline test harness (embedded broker, JMS factory, await helper)"
```

---

### Task 3: JMS baseline — send/receive correctness

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/JmsSenderBaselineIT.java`

**Interfaces:**
- Consumes: `EmbeddedActiveMqBroker`, `Await`, `ConnectionFactorySupport`, `EmbeddedActiveMqJmsFactory.NAME`.
- Produces: a reusable private helper `settings()` and `consumeEventQueue(int expectedAtLeast, long timeoutMs)` used by Tasks 4–5.

- [ ] **Step 1: Write the send/receive test**

Create `JmsSenderBaselineIT.java`:

```java
package com.im.njams.sdk.communication.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.junit.Rule;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.jms.JmsSender;
import com.im.njams.sdk.settings.ClientSettings;

public class JmsSenderBaselineIT {

    @Rule
    public EmbeddedActiveMqBroker broker = new EmbeddedActiveMqBroker();

    private static final String EVENT_QUEUE = "njams.event";

    static ClientSettings settings() {
        Properties p = new Properties();
        p.put(NjamsSettings.PROPERTY_COMMUNICATION, JmsSender.COMMUNICATION_NAME);
        p.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, EmbeddedActiveMqJmsFactory.NAME);
        p.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams");
        // NONE = never discard; block and retry until sent. This pins the guaranteed-delivery contract so the
        // reconnect test (Task 4) is deterministic. (The product DEFAULT is DISCARD, which drops messages while
        // disconnected — not the contract we baseline here.)
        p.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        // keep a single sender thread for deterministic ordering in these baseline tests
        p.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "1");
        p.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "1");
        return ClientSettings.from(p);
    }

    private static LogMessage logMessage(String logId, String path) {
        LogMessage msg = new LogMessage();
        msg.setLogId(logId);
        msg.setPath(path);
        return msg;
    }

    /** Drains up to timeoutMs; returns the text bodies received from the event queue. */
    List<String> consumeEventQueue(int expectedAtLeast, long timeoutMs) throws Exception {
        List<String> bodies = new ArrayList<>();
        ConnectionFactorySupport cf = new ConnectionFactorySupport(broker.brokerUrl());
        try (Connection connection = cf.factory().createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(EVENT_QUEUE);
            MessageConsumer consumer = session.createConsumer(queue);
            final long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline && bodies.size() < expectedAtLeast) {
                TextMessage m = (TextMessage) consumer.receive(100);
                if (m != null) {
                    bodies.add(m.getText());
                }
            }
        }
        return bodies;
    }

    @Test
    public void sendsLogMessageThatArrivesOnTheEventQueue() throws Exception {
        NjamsSender sender = new NjamsSender(settings());
        try {
            sender.send(logMessage("log-1", ">a>b>"), "session-1");
            List<String> bodies = consumeEventQueue(1, 5000);
            assertEquals("exactly one message expected", 1, bodies.size());
            assertTrue("body should carry the logId", bodies.get(0).contains("log-1"));
        } finally {
            sender.close();
        }
    }
}
```

- [ ] **Step 2: Run the test — must be green on current code**

Run: `mvn -q -pl njams-sdk test -Dtest=JmsSenderBaselineIT#sendsLogMessageThatArrivesOnTheEventQueue`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/it/JmsSenderBaselineIT.java
git commit -m "SDK-375 Add JMS baseline: log message send/receive over embedded broker"
```

---

### Task 4: JMS baseline — delivery resumes after a transient outage (Phase 2)

**Files:**
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/JmsSenderBaselineIT.java`

**Interfaces:**
- Consumes: `settings()`, `consumeEventQueue(...)`, `broker.stopBroker()/startBroker()/restart()`, `Await`.

- [ ] **Step 1: Write the reconnect test**

Add to `JmsSenderBaselineIT`:

```java
    @Test
    public void deliveryResumesAfterTransientBrokerOutage() throws Exception {
        NjamsSender sender = new NjamsSender(settings());
        try {
            // 1) prove connected: first message arrives
            sender.send(logMessage("before-outage", ">a>b>"), "session-1");
            assertEquals(1, consumeEventQueue(1, 5000).size());

            // 2) transient outage
            broker.stopBroker();

            // 3) enqueue a message during the outage; with DISCARD_POLICY=none the sender blocks and retries
            //    rather than dropping it
            sender.send(logMessage("during-outage", ">a>b>"), "session-1");

            // 4) restore the broker
            broker.startBroker();

            // 5) the buffered message is delivered once reconnected
            List<String> bodies = consumeEventQueue(1, 15000);
            assertEquals("message sent during the outage must be delivered after reconnect", 1, bodies.size());
            assertTrue(bodies.get(0).contains("during-outage"));
        } finally {
            sender.close();
        }
    }
```

- [ ] **Step 2: Run the test — must be green on current code**

Run: `mvn -q -pl njams-sdk test -Dtest=JmsSenderBaselineIT#deliveryResumesAfterTransientBrokerOutage`
Expected: PASS (current code reconnects and delivers under `DISCARD_POLICY=none`).

> If this proves flaky on current code because the JVM-global `static` reconnect state interferes with a broker restart, that is itself evidence for SDK-375; do **not** weaken the assertion — capture the observed behavior and raise it before proceeding.

- [ ] **Step 3: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/it/JmsSenderBaselineIT.java
git commit -m "SDK-375 Add JMS baseline: delivery resumes after transient broker outage"
```

---

### Task 5: JMS baseline — clean shutdown flushes and terminates

**Files:**
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/JmsSenderBaselineIT.java`

**Interfaces:**
- Consumes: `settings()`, `consumeEventQueue(...)`, `Await`.

- [ ] **Step 1: Write the shutdown test**

Add to `JmsSenderBaselineIT`:

```java
    @Test
    public void closeReturnsPromptlyAndDeliversInFlightMessage() throws Exception {
        NjamsSender sender = new NjamsSender(settings());
        sender.send(logMessage("final", ">a>b>"), "session-1");

        long start = System.currentTimeMillis();
        sender.close();
        long elapsed = System.currentTimeMillis() - start;

        // close() drains with a 10s await; a healthy connection should terminate well within it
        assertTrue("close() should return promptly when connected, took " + elapsed + " ms", elapsed < 10_000);

        List<String> bodies = consumeEventQueue(1, 5000);
        assertEquals("the in-flight message should have been delivered before shutdown completed", 1, bodies.size());
        assertTrue(bodies.get(0).contains("final"));
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q -pl njams-sdk test -Dtest=JmsSenderBaselineIT#closeReturnsPromptlyAndDeliversInFlightMessage`
Expected: PASS.

- [ ] **Step 3: Run the whole JMS baseline class**

Run: `mvn -q -pl njams-sdk test -Dtest=JmsSenderBaselineIT`
Expected: PASS (all three tests).

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/it/JmsSenderBaselineIT.java
git commit -m "SDK-375 Add JMS baseline: clean shutdown flushes in-flight message"
```

---

### Task 6: JMS baseline — full-`Njams` end-to-end smoke

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/JmsClientEndToEndBaselineIT.java`

**Interfaces:**
- Consumes: `EmbeddedActiveMqBroker`, `EmbeddedActiveMqJmsFactory.NAME`, `ConnectionFactorySupport`, `Await`.

> Rationale: exercises the public client path (`Njams.start()` → create job → `job.end()` (flushes a LogMessage) → `Njams.stop()`) over the real broker. JMS works end-to-end because the embedded broker serves both the sender's queues and the receiver's topic. Asserts only that project/log messages arrive and that start/stop complete — **not** startup-failure or shutdown-reconnect specifics.

- [ ] **Step 1: Write the end-to-end smoke**

Create `JmsClientEndToEndBaselineIT.java`:

```java
package com.im.njams.sdk.communication.it;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.jms.JmsSender;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class JmsClientEndToEndBaselineIT {

    @Rule
    public EmbeddedActiveMqBroker broker = new EmbeddedActiveMqBroker();

    private static Settings settings() {
        Settings s = new Settings();
        s.put(NjamsSettings.PROPERTY_COMMUNICATION, JmsSender.COMMUNICATION_NAME);
        s.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, EmbeddedActiveMqJmsFactory.NAME);
        s.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams");
        return s;
    }

    private List<String> drain(String queueName, int expectedAtLeast, long timeoutMs) throws Exception {
        List<String> bodies = new ArrayList<>();
        try (Connection connection = new ConnectionFactorySupport(broker.brokerUrl()).factory().createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(queueName);
            MessageConsumer consumer = session.createConsumer(queue);
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline && bodies.size() < expectedAtLeast) {
                TextMessage m = (TextMessage) consumer.receive(100);
                if (m != null) {
                    bodies.add(m.getText());
                }
            }
        }
        return bodies;
    }

    @Test
    public void startRunJobStopDeliversMessages() throws Exception {
        Njams njams = new Njams(Path.of("SDK4", "IT"), "TEST", "SDK4", settings());
        ProcessModel process = njams.model().create("PROCESSES");
        ActivityModel start = process.createActivity("act", "Act", null);
        start.setStarter(true);

        assertTrue("Njams should start against the embedded broker", njams.start());

        Job job = process.createJob();
        job.start();
        job.createActivity(start).setStarter().build();
        job.end(); // flushes a LogMessage

        // project message (from start()) and log message (from job.end()) both land on njams.event by default
        List<String> bodies = drain("njams.event", 1, 10000);
        assertTrue("at least one message should be delivered end-to-end", bodies.size() >= 1);

        njams.stop();
    }
}
```

- [ ] **Step 2: Run it**

Run: `mvn -q -pl njams-sdk test -Dtest=JmsClientEndToEndBaselineIT`
Expected: PASS.

> If `Njams.start()` returns `false` here, the receiver could not connect over the embedded broker — inspect the receiver's topic/selector config and adjust the settings (e.g. `PROPERTY_JMS_SUPPORTS_MESSAGE_SELECTOR`) rather than weakening the assertion; raise it if unclear.

- [ ] **Step 3: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/it/JmsClientEndToEndBaselineIT.java
git commit -m "SDK-375 Add JMS baseline: full-Njams start/job/stop end-to-end smoke"
```

---

### Task 7: HTTP test harness — JDK ingest server

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/IngestHttpServer.java`

**Interfaces:**
- Produces: `IngestHttpServer` JUnit `@Rule` (extends `ExternalResource`):
  - `String baseUrl()` (e.g. `http://localhost:<port>/`), `String dataproviderSuffix()` = `"testdp"`.
  - `List<String> receivedBodies()`, `List<java.util.Map<String,String>> receivedHeaders()` (thread-safe snapshots).
  - `void stopServer()` / `void startServer()` to simulate an outage; `int postCount()`.
  - Handles `HEAD /api/processing/ingest/testdp` → 200; `POST /api/processing/ingest/testdp` → records body+headers, returns 200; `GET /api/public/version` → 200 `{}`.

- [ ] **Step 1: Write the server rule**

Create `IngestHttpServer.java`:

```java
package com.im.njams.sdk.communication.it;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.rules.ExternalResource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Minimal in-process nJAMS ingest endpoint backed by the JDK HttpServer. Records POSTed bodies and headers. */
public class IngestHttpServer extends ExternalResource {

    public static final String SUFFIX = "testdp";
    private static final String INGEST_PATH = "/api/processing/ingest/" + SUFFIX;
    private static final String VERSION_PATH = "/api/public/version";

    private HttpServer server;
    private int port;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<Map<String, String>> headers = new CopyOnWriteArrayList<>();

    public String baseUrl() {
        return "http://localhost:" + port + "/";
    }

    public String dataproviderSuffix() {
        return SUFFIX;
    }

    public List<String> receivedBodies() {
        return bodies;
    }

    public List<Map<String, String>> receivedHeaders() {
        return headers;
    }

    public int postCount() {
        return bodies.size();
    }

    public void startServer() throws IOException {
        // reuse the previously assigned port on restart; 0 lets the OS pick on first start
        server = HttpServer.create(new InetSocketAddress(port), 0);
        port = server.getAddress().getPort();
        server.createContext(INGEST_PATH, this::handleIngest);
        server.createContext(VERSION_PATH, this::handleVersion);
        server.setExecutor(null);
        server.start();
    }

    public void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handleIngest(HttpExchange exchange) throws IOException {
        final String method = exchange.getRequestMethod();
        if ("HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            final byte[] raw = readAll(exchange.getRequestBody());
            bodies.add(new String(raw, StandardCharsets.UTF_8));
            final java.util.HashMap<String, String> h = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> h.put(k, v.isEmpty() ? "" : v.get(0)));
            headers.add(h);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(405, -1);
        exchange.close();
    }

    private void handleVersion(HttpExchange exchange) throws IOException {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    @Override
    protected void before() throws Throwable {
        startServer();
    }

    @Override
    protected void after() {
        stopServer();
    }
}
```

- [ ] **Step 2: Compile test sources**

Run: `mvn -q -pl njams-sdk test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/it/IngestHttpServer.java
git commit -m "SDK-375 Add HTTP baseline test harness (JDK ingest HttpServer)"
```

---

### Task 8: HTTP baseline — send/receive correctness

**Files:**
- Create: `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/HttpSenderBaselineIT.java`

**Interfaces:**
- Consumes: `IngestHttpServer`, `Await`, `HttpSender.NAME`, `NjamsSender`.
- Produces: private helpers `settings(IngestHttpServer)`, `resetHttpConnectionTestCache()` (resets the static `HttpSender.connectionTest`), and `logMessage(...)` reused by Tasks 9–10.

- [ ] **Step 1: Write the send/receive test**

Create `HttpSenderBaselineIT.java`:

```java
package com.im.njams.sdk.communication.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Properties;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.http.HttpSender;
import com.im.njams.sdk.settings.ClientSettings;

public class HttpSenderBaselineIT {

    @Rule
    public IngestHttpServer server = new IngestHttpServer();

    /** HttpSender caches its connection-test in a static field; reset it so each test starts clean. */
    @Before
    public void resetHttpConnectionTestCache() throws Exception {
        Field f = HttpSender.class.getDeclaredField("connectionTest");
        f.setAccessible(true);
        f.set(null, null);
    }

    ClientSettings settings(IngestHttpServer server) {
        Properties p = new Properties();
        p.put(NjamsSettings.PROPERTY_COMMUNICATION, HttpSender.NAME);
        p.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, server.baseUrl());
        p.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, server.dataproviderSuffix());
        // NONE = never discard; block and retry until sent (see the JMS settings() note). Pins guaranteed delivery
        // so the reconnect test (Task 9) is deterministic; product DEFAULT is DISCARD.
        p.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        p.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "1");
        p.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "1");
        return ClientSettings.from(p);
    }

    static LogMessage logMessage(String logId, String path) {
        LogMessage msg = new LogMessage();
        msg.setLogId(logId);
        msg.setPath(path);
        return msg;
    }

    @Test
    public void sendsLogMessageThatArrivesAtTheIngestEndpoint() {
        NjamsSender sender = new NjamsSender(settings(server));
        try {
            sender.send(logMessage("log-1", ">a>b>"), "session-1");
            assertTrue("a POST should reach the ingest endpoint",
                Await.until(() -> server.postCount() >= 1, 5000));
            assertEquals(1, server.postCount());
            assertTrue("body should carry the logId", server.receivedBodies().get(0).contains("log-1"));
        } finally {
            sender.close();
        }
    }
}
```

- [ ] **Step 2: Run the test — must be green on current code**

Run: `mvn -q -pl njams-sdk test -Dtest=HttpSenderBaselineIT#sendsLogMessageThatArrivesAtTheIngestEndpoint`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/it/HttpSenderBaselineIT.java
git commit -m "SDK-375 Add HTTP baseline: log message send/receive over JDK ingest server"
```

---

### Task 9: HTTP baseline — delivery resumes after a transient outage (Phase 2)

**Files:**
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/HttpSenderBaselineIT.java`

**Interfaces:**
- Consumes: `settings(server)`, `server.stopServer()/startServer()`, `Await`.

- [ ] **Step 1: Write the reconnect test**

Add to `HttpSenderBaselineIT`:

```java
    @Test
    public void deliveryResumesAfterTransientServerOutage() throws Exception {
        NjamsSender sender = new NjamsSender(settings(server));
        try {
            sender.send(logMessage("before-outage", ">a>b>"), "session-1");
            assertTrue(Await.until(() -> server.postCount() >= 1, 5000));

            server.stopServer();
            sender.send(logMessage("during-outage", ">a>b>"), "session-1");
            server.startServer();

            assertTrue("message sent during the outage must arrive after the server returns",
                Await.until(() -> server.postCount() >= 2, 15000));
            assertTrue(server.receivedBodies().stream().anyMatch(b -> b.contains("during-outage")));
        } finally {
            sender.close();
        }
    }
```

- [ ] **Step 2: Run the test — must be green on current code**

Run: `mvn -q -pl njams-sdk test -Dtest=HttpSenderBaselineIT#deliveryResumesAfterTransientServerOutage`
Expected: PASS.

> Note: `IngestHttpServer.startServer()` reuses the same port after `stopServer()`, so the sender's cached `OkHttpClient` reconnects to the same endpoint. `DISCARD_POLICY=none` makes the during-outage message block-and-retry (verified through `AbstractSender.send()` → `onException` → reconnect, and `HttpSender.tryToSend` which does not discard under `none`). If current code drops it anyway, capture that as evidence for SDK-375 rather than weakening the assertion.

- [ ] **Step 3: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/it/HttpSenderBaselineIT.java
git commit -m "SDK-375 Add HTTP baseline: delivery resumes after transient server outage"
```

---

### Task 10: HTTP baseline — clean shutdown; full suite green

**Files:**
- Modify: `njams-sdk/src/test/java/com/im/njams/sdk/communication/it/HttpSenderBaselineIT.java`

**Interfaces:**
- Consumes: `settings(server)`, `Await`.

- [ ] **Step 1: Write the shutdown test**

Add to `HttpSenderBaselineIT`:

```java
    @Test
    public void closeReturnsPromptlyAndDeliversInFlightMessage() {
        NjamsSender sender = new NjamsSender(settings(server));
        sender.send(logMessage("final", ">a>b>"), "session-1");

        long startMs = System.currentTimeMillis();
        sender.close();
        long elapsed = System.currentTimeMillis() - startMs;

        assertTrue("close() should return promptly when connected, took " + elapsed + " ms", elapsed < 10_000);
        assertTrue("the in-flight message should have been delivered",
            Await.until(() -> server.receivedBodies().stream().anyMatch(b -> b.contains("final")), 5000));
    }
```

- [ ] **Step 2: Run the full HTTP baseline class**

Run: `mvn -q -pl njams-sdk test -Dtest=HttpSenderBaselineIT`
Expected: PASS (all three tests).

- [ ] **Step 3: Run the entire communication.it package + full module build**

Run: `mvn -q -pl njams-sdk test -Dtest="com.im.njams.sdk.communication.it.*"`
Expected: PASS.
Run: `mvn -q -pl njams-sdk test`
Expected: PASS — the new baseline tests coexist with the existing suite (watch for cross-test interference from the `HttpSender.connectionTest` static and the JVM-global reconnect state; if the full run is flaky where the isolated classes are green, that is SDK-375 evidence — record it, do not paper over it).

- [ ] **Step 4: Commit**

```bash
git add njams-sdk/src/test/java/com/im/njams/sdk/communication/it/HttpSenderBaselineIT.java
git commit -m "SDK-375 Add HTTP baseline: clean shutdown; complete baseline test suite"
```

---

## Self-Review

**Spec coverage (against Decision 2 / working-agreement §7 D2.1 role 1 + D2.3):**
- Send/receive correctness — JMS Task 3, HTTP Task 8. ✅
- Delivery resumes after transient mid-processing loss (Phase 2) — JMS Task 4, HTTP Task 9. ✅
- Clean shutdown flushes + terminates — JMS Task 5, HTTP Task 10. ✅
- Real endpoints: embedded ActiveMQ `vm://` (Tasks 1–2), JDK `HttpServer` (Task 7). ✅
- Full-client end-to-end path — JMS Task 6. ✅
- Kafka relaxed / excluded — stated in Global Constraints. ✅
- No new production dependency; one new test dep — Task 1. ✅
- No pinning of Phase-1/Phase-3 to-be-changed behavior — assertions are limited to send/receive + resume + prompt clean close; notes explicitly forbid weakening assertions and flag deviations as SDK-375 evidence. ✅

**Placeholder scan:** no TBD/TODO; all steps contain concrete code and exact commands. ✅

**Type consistency:** `NjamsSender(ClientSettings)` + `send(CommonMessage, String)` + `close()` (verified in source); `JmsSender.COMMUNICATION_NAME`, `HttpSender.NAME`, `EmbeddedActiveMqJmsFactory.NAME`, `IngestHttpServer.SUFFIX` referenced consistently; settings keys (`PROPERTY_COMMUNICATION`, `PROPERTY_JMS_CONNECTION_FACTORY`, `PROPERTY_JMS_DESTINATION`, `PROPERTY_HTTP_BASE_URL`, `PROPERTY_HTTP_DATAPROVIDER_SUFFIX`, `PROPERTY_MIN/MAX_SENDER_THREADS`) verified in `NjamsSettings`. ✅

**Resolved (was an open risk):** the product default `DiscardPolicy.DEFAULT = DISCARD` drops messages while disconnected, so the reconnect tests explicitly set `PROPERTY_DISCARD_POLICY=none` to pin the guaranteed-delivery-resumes contract. Verified through `AbstractSender.send()` and `HttpSender.tryToSend()` that `none` blocks-and-retries on both transports. Remaining execution-time watch item: the JVM-global `static` reconnect state (`AbstractSender.hasConnected`/`connecting`) may still make the *full-module* run flakier than the isolated classes — if so, that is SDK-375 evidence to record, not a reason to weaken assertions.
```
