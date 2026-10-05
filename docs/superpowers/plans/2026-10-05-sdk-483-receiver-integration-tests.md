# SDK-483 Receiver Integration Tests (R1–R7) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the receiver scenarios R1–R7 (spec §6.2) to the Docker-based `njams-sdk-communication-it` module, covering the receiver's connection lifecycle and server-command path against real ActiveMQ (JMS) and WireMock (HTTP).

**Architecture:** New `*IT` classes beside the existing sender ITs, driven by the fixed harness and the existing Toxiproxy fault mechanics. A few small support helpers are added to `it/support` (generic Jolokia read, a JMS command client, a shared settings builder, WireMock stub/journal helpers). JMS variants come first; HTTP variants depend on a feasibility spike (Task 1) because the HTTP receiver subscribes to a Server-Sent-Events stream that WireMock may not be able to serve.

**Tech Stack:** Java 11, JUnit 4, ActiveMQ client 5.19.x (broker image 5.19.2), WireMock 3.13.2 (image), Toxiproxy 2.12.0, Jolokia (ActiveMQ console), fabric8 docker-maven-plugin 0.48.1, maven-failsafe-plugin (root pom).

**Spec:** `docs/superpowers/specs/2026-09-28-sdk-483-communication-it-module-design.md` (§6.2, §7, §9).

**Ticket:** SDK-483 (reopened, In Progress). **breaking-change:** not applicable (test module only). **Fix version:** 6.1.0.

## Global Constraints

- Test classes are named `*IT` (root failsafe default includes `*IT`, `IT*`, `*ITCase`); abstract bases are not matched.
- JUnit 4 only (`@Rule`, `ExternalResource`, `Parameterized`); no new third-party dependencies.
- Receiver scenarios are policy-independent: do **not** parameterize over `DiscardMode`. R5 pins `none` via `env.disableMessageDiscarding(settings)`.
- The module is manual/on-demand: it stays bound to the root `docker-it` profile and is never wired into the default build.
- Kafka is out of scope.
- This module detects, it does not diagnose or fix: a defect found by a scenario becomes a separate Jira ticket, fixed with normal JUnit/mocked-IT TDD in `njams-sdk`. Do not change `njams-sdk` production code in this plan.
- Keep the client-side driving logic trivial; no scenario-specific branching beyond the documented knobs.
- Never run two suites at once (they share `target/docker-it.properties` and fixed container aliases). Never `mvn install`.
- Run command (reactor build so the working tree is tested, not an installed snapshot):
  `mvn -Pdocker-it verify -pl njams-sdk,njams-sdk-communication-it -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=<ClassName>` (omit `-Dit.test` for the full suite, about 6–10 minutes).
- Do not generate artificial machine load; the ITs are timing-based, so run them on an otherwise idle machine and re-run once before treating a timing failure as real.
- Test files need no copyright header or Javadoc (see `code-quality-general.md`).
- Commit messages: `SDK-483 <description>` (no `#comment` on intermediate commits).

## Review Focus

1. **JMS commands topic with no destination configured:** `JmsReceiver` builds its topic as `<destination>.commands`; with neither destination key set the string is `null.commands`, while `JmsSender` falls back to `njams`. Expected: sender and receiver agree. The ITs set `PROPERTY_JMS_DESTINATION=njams` explicitly (Task 2) and the discrepancy is reported to the user as a potential separate defect ticket (not fixed here).
2. **Command published before the consumer is attached is lost** (non-durable topic): a test that publishes too early passes or fails randomly. Expected: every command goes through `awaitReply`, which retries until the receiver answers.
3. **Backoff after a long outage:** the receiver's reconnect interval grows up to 60 s. Expected: recovery assertions use generous timeouts and a short outage (`DockerEnvironment.OUTAGE_MS`).
4. **Stale state between tests:** leftover toxics, mappings or `Receiver-*` threads from a previous test. Expected: every IT removes what it adds and ends with the shared `DockerEnvironment` rule's reset; thread assertions use baselines/`awaitNone`.
5. **Wrong-instance delivery on a shared receiver:** an ancestor path matches the selector but not the exact-path map (reply code 99). Expected: R6 addresses instances by exact path and asserts the code-99 case explicitly.

---

## File Map

| Action | File |
|---|---|
| Modify | `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/support/DockerEnvironment.java` (generic Jolokia read, topic consumer count) |
| Create | `.../it/support/ReceiverSettings.java` (JMS/HTTP settings for receiver ITs) |
| Create | `.../it/support/JmsCommandClient.java` (publish a command, await the reply) |
| Create | `.../it/support/WireMockStubs.java` (extracted on-demand stub load/remove; SSE stub, Task 1/7) |
| Modify | `.../it/support/WireMockJournal.java` (count by path with optional header match, Task 7) |
| Create | `.../it/jms/JmsCommandRoundTripIT.java` (R3) |
| Create | `.../it/jms/ReceiverReconnectIT.java` (R1) |
| Create | `.../it/jms/ReceiverStartupOutageIT.java` (R2) |
| Create | `.../it/jms/ReceiverShutdownDuringReconnectIT.java` (R4) |
| Create | `.../it/jms/ReceiverAfterSenderOutageIT.java` (R5) |
| Create | `.../it/jms/SharedReceiverIT.java` (R6) |
| Create | `.../it/jms/ReceiverStartStopLeakIT.java` (R7) |
| Create | `.../it/http/HttpReceiver*IT.java` (HTTP variants of R1–R5, Task 7, only if the spike allows) |
| Modify | `njams-sdk-communication-it/README.md`, spec status line |

(`...` = `njams-sdk-communication-it/src/test/java/com/im/njams/sdk`.)

---

## Task 0 — Confirm ticket and baseline

- [ ] **Step 1:** Confirm SDK-483 is In Progress and assigned to the current user (done on 2026-10-05; re-check with `getJiraIssue`).
- [ ] **Step 2:** Confirm a clean baseline: the full suite passed on 2026-10-05 (65 ITs, `BUILD SUCCESS`). No re-run is needed unless the tree changed in `njams-sdk` since; if it did, run the full command from *Global Constraints* once and note the result.

## Task 1 — Feasibility spike (decides the HTTP variants and the JMS observables)

**Files:** throwaway `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/support/SpikeIT.java` (deleted at the end of this task, not committed).

**Produces:** three recorded facts used by later tasks: (a) the exact ActiveMQ topic MBean name/attribute for the consumer count, (b) whether WireMock 3.13.2 can serve a long-lived SSE stream that the HTTP receiver treats as connected, (c) whether the receiver's reply `POST /api/httpcommunication/reply` is visible in the WireMock journal.

- [ ] **Step 1: Write the spike**

```java
package com.im.njams.sdk.it.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.Rule;
import org.junit.Test;

public class SpikeIT {
    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test
    public void spike() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        // (a) list the topic MBeans the broker currently knows (run after creating the topic once)
        System.out.println("MBEANS " + http.send(HttpRequest.newBuilder(URI.create(env.jolokiaUrl() + "/search/org.apache.activemq:type=Broker,brokerName=localhost,destinationType=Topic,*"))
            .header("Origin", "http://localhost").GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        // (b) register an SSE stub that stays open ~20 s and fetch it with a plain client
        String stub = "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/api/httpcommunication/subscribe\"},"
            + "\"response\":{\"status\":200,\"headers\":{\"Content-Type\":\"text/event-stream\"},"
            + "\"body\":\"id: 1\\nevent: {\\\"njams-receiver\\\":\\\">x>\\\",\\\"njams-message-id\\\":\\\"m1\\\",\\\"njams-content\\\":\\\"json\\\"}\\ndata: {\\\"request\\\":{\\\"command\\\":\\\"Ping\\\"}}\\n\\n\","
            + "\"chunkedDribbleDelay\":{\"numberOfChunks\":20,\"totalDuration\":20000}}}";
        http.send(HttpRequest.newBuilder(URI.create(env.wireMockAdminUrl() + "/mappings")).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(stub)).build(), HttpResponse.BodyHandlers.ofString());
        long t0 = System.nanoTime();
        HttpResponse<java.io.InputStream> r = http.send(HttpRequest.newBuilder(URI.create(env.httpBaseUrlDirect() + "/api/httpcommunication/subscribe")).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream());
        System.out.println("SSE status=" + r.statusCode() + " ct=" + r.headers().firstValue("Content-Type")
            + " firstByteMs=" + (System.nanoTime() - t0) / 1_000_000);
        byte[] first = r.body().readNBytes(16);
        System.out.println("SSE first bytes after ms=" + (System.nanoTime() - t0) / 1_000_000 + " n=" + first.length);
    }
}
```

- [ ] **Step 2: Run it**

Run: `mvn -Pdocker-it verify -pl njams-sdk,njams-sdk-communication-it -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=SpikeIT`
Expected: prints `SSE status=200` with `Content-Type` `text/event-stream` and a stream that delivers its first bytes immediately and stays open for ~20 s.

- [ ] **Step 3: Verify the receiver treats it as connected (HTTP)** — extend the spike with a started HTTP `Njams` (settings as in `HttpStartupOutageIT`, `PROPERTY_HTTP_BASE_URL=env.httpBaseUrlDirect()`), a stub whose event names the client's exact path (`njams.metadata().getClientPath().toString()`), then print the journal entries for `/api/httpcommunication/reply`. Expected: `start()` returns `true`, the subscribe stub is hit, and a `POST /api/httpcommunication/reply` with header `njams-reply-for: m1` appears in the journal.

- [ ] **Step 4: Verify the JMS observables** — start a JMS `Njams` (settings from Task 2's `ReceiverSettings.jms`) and print `SEARCH` results for the topic MBean and a `read` of `ConsumerCount` on `njams.commands`.

- [ ] **Step 5: Record the facts and decide**
  - If (b)/(c) hold: HTTP variants (Task 7) proceed with the stub shape verified here.
  - If WireMock cannot hold the SSE stream open or the reply is not journaled: **stop and report to the user** with what failed and a proposal (e.g. a small dedicated SSE stub container); do not build Task 7 without confirmation.
  - If the MBean name differs from the one used in Task 2 Step 1, use the verified name there.

- [ ] **Step 6:** Delete `SpikeIT.java` (`git status` must show no spike file). No commit for this task.

## Task 2 — Support helpers

**Files:** modify `DockerEnvironment.java`; create `ReceiverSettings.java`, `JmsCommandClient.java`.

**Produces (relied on by Tasks 3–7):**
- `DockerEnvironment#jolokiaRead(String mbean, String attribute): JsonNode`
- `DockerEnvironment#commandsTopicConsumerCount(): int` (0 when the topic MBean does not exist yet)
- `DockerEnvironment#awaitCommandsTopicConsumerCount(int expected, Duration timeout): int` (polls every 500 ms; returns the last value read)
- `ReceiverSettings.jms(DockerEnvironment): ClientSettings`, `ReceiverSettings.http(DockerEnvironment): ClientSettings`, `ReceiverSettings.COMMANDS_TOPIC = "njams.commands"`
- `JmsCommandClient(DockerEnvironment, String topic)`, `Instruction request(Command, String receiverPath, String clientId, Duration)`, `Instruction awaitReply(Command, String receiverPath, String clientId, Duration)`, `close()`

- [ ] **Step 1: Generalize the Jolokia read in `DockerEnvironment`.** Replace the body of `brokerConnectionCount()` so it delegates, and add the two new methods (keep `brokerConnectionCount()`'s signature and behavior):

```java
public int brokerConnectionCount() throws IOException, InterruptedException, URISyntaxException {
    return jolokiaRead("org.apache.activemq:type=Broker,brokerName=localhost", "CurrentConnectionsCount").asInt();
}

public int commandsTopicConsumerCount() throws IOException, InterruptedException, URISyntaxException {
    JsonNode value = jolokiaRead("org.apache.activemq:type=Broker,brokerName=localhost,destinationType=Topic,"
        + "destinationName=" + ReceiverSettings.COMMANDS_TOPIC, "ConsumerCount");
    return value == null || value.isNull() ? 0 : value.asInt();
}

public int awaitCommandsTopicConsumerCount(int expected, Duration timeout)
    throws IOException, InterruptedException, URISyntaxException {
    long deadline = System.nanoTime() + timeout.toNanos();
    int last = commandsTopicConsumerCount();
    while (last != expected && System.nanoTime() < deadline) {
        Thread.sleep(500);
        last = commandsTopicConsumerCount();
    }
    return last;
}

/** Reads one attribute through Jolokia; returns {@code null} if the MBean does not exist (yet). */
public JsonNode jolokiaRead(String mbean, String attribute) throws IOException, InterruptedException, URISyntaxException {
    // body of the former brokerConnectionCount(): same POST, headers (Authorization, Origin, Content-Type),
    // payload {"type":"read","mbean":mbean,"attribute":attribute};
    // return root.has("value") ? root.get("value") : null;
}
```
(Move the existing request-building code into `jolokiaRead` verbatim, parameterizing mbean/attribute.)

- [ ] **Step 2: Create `ReceiverSettings`**

```java
package com.im.njams.sdk.it.support;

import java.util.Properties;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

/** Settings shared by the receiver ITs. */
public final class ReceiverSettings {
    /** Commands topic for {@code PROPERTY_JMS_DESTINATION=njams} (receiver topic = destination + ".commands"). */
    public static final String COMMANDS_TOPIC = "njams.commands";

    private ReceiverSettings() {
    }

    public static ClientSettings jms(DockerEnvironment env) {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        // explicit: the receiver otherwise derives "null.commands" from an unset destination (see Review Focus 1)
        settings.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams");
        env.configureJms(settings);
        env.disableMessageDiscarding(settings);
        return settings;
    }

    public static ClientSettings http(DockerEnvironment env) {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        return settings;
    }
}
```

- [ ] **Step 3: Create `JmsCommandClient`**

```java
package com.im.njams.sdk.it.support;

import java.time.Duration;
import java.time.LocalDateTime;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.communication.MessageHeaders;
import com.im.njams.sdk.utils.JsonUtils;

/** Publishes a server command straight to the broker (not through the proxy) and reads the client's reply. */
public final class JmsCommandClient implements AutoCloseable {
    private final Connection connection;
    private final Session session;
    private final Topic commands;

    public JmsCommandClient(DockerEnvironment env, String commandsTopic) throws JMSException {
        connection = new ActiveMQConnectionFactory(env.jmsUrlDirect()).createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        commands = session.createTopic(commandsTopic);
    }

    /** Sends one command; returns the reply, or {@code null} if none arrived within {@code timeout}. */
    public Instruction request(Command command, String receiverPath, String clientId, Duration timeout)
        throws JMSException {
        TemporaryQueue replyTo = session.createTemporaryQueue();
        try (MessageConsumer replies = session.createConsumer(replyTo);
             MessageProducer producer = session.createProducer(commands)) {
            Request request = new Request();
            request.setCommand(command.commandString());
            request.setDateTime(LocalDateTime.now());
            Instruction instruction = new Instruction();
            instruction.setRequest(request);
            TextMessage message = session.createTextMessage(JsonUtils.serialize(instruction));
            message.setStringProperty(MessageHeaders.NJAMS_RECEIVER_HEADER, receiverPath);
            message.setStringProperty(MessageHeaders.NJAMS_CONTENT_HEADER, MessageHeaders.CONTENT_TYPE_JSON);
            if (clientId != null) {
                message.setStringProperty(MessageHeaders.NJAMS_CLIENTID_HEADER, clientId);
            }
            message.setJMSReplyTo(replyTo);
            producer.send(message);
            Message reply = replies.receive(timeout.toMillis());
            return reply instanceof TextMessage
                ? JsonUtils.parse(((TextMessage) reply).getText(), Instruction.class) : null;
        } finally {
            replyTo.delete();
        }
    }

    /**
     * Repeats {@link #request} (the commands topic is non-durable, so a command sent before the receiver's
     * consumer is attached is lost) until a reply arrives or {@code timeout} elapsed; returns {@code null} then.
     */
    public Instruction awaitReply(Command command, String receiverPath, String clientId, Duration timeout)
        throws JMSException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Instruction reply = request(command, receiverPath, clientId, Duration.ofSeconds(1));
            if (reply != null) {
                return reply;
            }
            Thread.sleep(500);
        }
        return null;
    }

    @Override
    public void close() throws JMSException {
        connection.close();
    }
}
```

- [ ] **Step 4: Compile.** Run: `mvn -q test-compile -pl njams-sdk,njams-sdk-communication-it` — Expected: no errors.
- [ ] **Step 5: Commit** `SDK-483 Add receiver IT support helpers`.

## Task 3 — R3 command round trip (JMS), validates the helpers

**Files:** create `.../it/jms/JmsCommandRoundTripIT.java`.

- [ ] **Step 1: Write the test**

```java
package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.JmsCommandClient;
import com.im.njams.sdk.it.support.ReceiverSettings;

/** R3: a server command reaches the client over JMS and its reply is delivered. */
public class JmsCommandRoundTripIT {
    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 60000)
    public void pingIsAnsweredWithPong() throws Exception {
        njams = new Njams(Path.of("R3Jms"), "1.0.0", "CommunicationIT", ReceiverSettings.jms(env));
        assertTrue(njams.start());
        String path = njams.metadata().getClientPath().toString();
        try (JmsCommandClient client = new JmsCommandClient(env, ReceiverSettings.COMMANDS_TOPIC)) {
            Instruction reply = client.awaitReply(Command.PING, path, null, Duration.ofSeconds(30));
            assertNotNull("no reply to PING within 30 s", reply);
            assertEquals(0, reply.getResponse().getResultCode());
            assertEquals("Pong", reply.getResponse().getResultMessage());
            assertEquals(njams.metadata().getClientSessionId(), reply.getResponse().getParameters().get("clientId"));
        }
    }
}
```

- [ ] **Step 2: Run:** `... -Dit.test=JmsCommandRoundTripIT` — Expected: PASS. If it fails because no reply arrives, diagnose with the DEBUG log (`simplelogger.properties` is at debug): check the topic name in `JmsReceiver` log lines and that the selector path matches `getClientPath().toString()`. Do not change `njams-sdk`; if the cause is an SDK defect, stop and report to the user.
- [ ] **Step 3: Commit** `SDK-483 Add JMS command round trip IT (R3)`.

## Task 4 — R1, R2, R4 (JMS)

**Files:** create `ReceiverReconnectIT.java`, `ReceiverStartupOutageIT.java`, `ReceiverShutdownDuringReconnectIT.java` in `.../it/jms/`.

**Common shape:** `@Rule DockerEnvironment env`; `@After` removes the toxic (`env.toxiproxy().removeToxic("jms", NAME)` in a try/catch that ignores "not found") and stops `njams`; toxic added with `env.toxiproxy().addToxic("jms", NAME, "timeout", Map.of("timeout", 1))` (the existing "down" toxic).

- [ ] **Step 1: R1 `ReceiverReconnectIT`**

```java
@Test(timeout = 120000)
public void receiverReconnectsAfterConnectionLossAndHandlesCommands() throws Exception {
    njams = new Njams(Path.of("R1Jms"), "1.0.0", "CommunicationIT", ReceiverSettings.jms(env));
    assertTrue(njams.start());
    String path = njams.metadata().getClientPath().toString();
    try (JmsCommandClient client = new JmsCommandClient(env, ReceiverSettings.COMMANDS_TOPIC)) {
        assertNotNull(client.awaitReply(Command.PING, path, null, Duration.ofSeconds(30)));
        env.toxiproxy().addToxic("jms", TOXIC, "timeout", Map.of("timeout", 1));
        Thread.sleep(DockerEnvironment.OUTAGE_MS);
        env.toxiproxy().removeToxic("jms", TOXIC);
        assertNotNull("receiver did not recover", client.awaitReply(Command.PING, path, null, Duration.ofSeconds(90)));
        assertEquals("exactly one consumer on the commands topic after recovery", 1,
            env.awaitCommandsTopicConsumerCount(1, Duration.ofSeconds(30)));
        assertTrue("more than one receiver reconnector alive",
            SdkThreads.alive("Receiver-Sender-Reconnector-Thread").size() <= 1);
    }
}
```
(use `env.awaitCommandsTopicConsumerCount(1, Duration.ofSeconds(30))` for the consumer-count assertion; also import `java.time.Duration` where needed in `DockerEnvironment`.)

- [ ] **Step 2: R2 `ReceiverStartupOutageIT`** — settings from `ReceiverSettings.jms(env)` plus `PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR="RECONNECT"` and `PROPERTY_COMMUNICATION_CONNECT_TIMEOUT="2000"`; the toxic is added **before** `new Njams(...)` (the receiver connect starts in the constructor); `assertTrue(njams.start())`; remove the toxic; `awaitReply(PING, ..., 90 s)` non-null; consumer count settles at 1.

- [ ] **Step 3: R4 `ReceiverShutdownDuringReconnectIT`** — start, add the toxic, wait 1 s so the receiver is reconnecting, time `njams.stop()` (`< 10 s`, same assertion style as `ShutdownDuringOutageIT`), then `SdkThreads.awaitNone(Duration.ofSeconds(10), SdkThreads.RECEIVER)` is empty; remove the toxic; after the broker is reachable, `env.awaitCommandsTopicConsumerCount(0, 30 s)` returns 0.

- [ ] **Step 4: Run** each class (`-Dit.test=ReceiverReconnectIT` etc.) — Expected: PASS. A failure that points at SDK behavior (e.g. a second consumer, a surviving `Receiver-*` thread) is a finding: stop, capture the log excerpt, report to the user for a separate ticket; do not weaken the assertion.
- [ ] **Step 5: Commit** `SDK-483 Add JMS receiver reconnect, startup-outage and shutdown ITs (R1, R2, R4)`.

## Task 5 — R5 and R7 (JMS)

**Files:** create `ReceiverAfterSenderOutageIT.java`, `ReceiverStartStopLeakIT.java`.

- [ ] **Step 1: R5** — settings `ReceiverSettings.jms(env)` (discard mode `none`); start; baseline `env.awaitCommandsTopicConsumerCount(1, 30 s) == 1`; add the `jms` down toxic for `OUTAGE_MS`, remove it; `awaitReply(PING, ..., 90 s)` non-null; then `awaitCommandsTopicConsumerCount(1, 60 s) == 1` (cycled once, no extra consumer); `SdkThreads.alive("Receiver-Recovery-Cycle-Thread")` eventually empty (`awaitNone`, 30 s).
- [ ] **Step 2: R7** — 10 cycles of `new Njams(Path.of("R7Jms-" + i), ...)`, `start()`, `stop()`; afterwards `SdkThreads.awaitNone(Duration.ofSeconds(10), SdkThreads.RECEIVER)` empty and `env.awaitCommandsTopicConsumerCount(0, 30 s) == 0`.
- [ ] **Step 3: Run** both classes — Expected: PASS (same finding rule as Task 4 Step 4).
- [ ] **Step 4: Commit** `SDK-483 Add JMS receiver sender-outage and leak ITs (R5, R7)`.

## Task 6 — R6 shared JMS receiver

**Files:** create `.../it/jms/SharedReceiverIT.java`.

- [ ] **Step 1: Write the test.** Settings: `ReceiverSettings.jms(env)` plus `PROPERTY_SHARED_COMMUNICATIONS="true"`. Create `a` (`Path.of("R6Jms", "A")`) and `b` (`Path.of("R6Jms", "B")`), start both. With one `JmsCommandClient`:
  1. `awaitReply(PING, pathOf(a), null, 30 s)` → `clientId` parameter equals `a`'s `clientSessionId`; same for `b`.
  2. Addressing the **ancestor** path `Path.of("R6Jms").toString()` yields `resultCode 99` ("Client instance not found.").
  3. `a.stop()`: the consumer count stays `1` (`b` still uses the shared receiver) and `b` still answers PING.
  4. `b.stop()`: `awaitCommandsTopicConsumerCount(0, 30 s) == 0`.
  5. A fresh `Njams` `c` (`Path.of("R6Jms", "C")`) starts and answers PING (a new shared receiver was built).
- [ ] **Step 2: Run** `-Dit.test=SharedReceiverIT` — Expected: PASS; findings are handled as in Task 4 Step 4.
- [ ] **Step 3: Commit** `SDK-483 Add shared JMS receiver IT (R6)`.

## Task 7 — HTTP variants of R1, R2, R3, R4, R5 (only if Task 1 confirmed SSE support)

**Gate:** proceed only with the stub shape recorded in Task 1. Otherwise this task is replaced by the user's decision.

**Files:** create `WireMockStubs.java` (extract the private on-demand stub load/remove helper from `HttpStartupOutageIT:252-272` and add `sseStubFor(String clientPath, String messageId)` returning the mapping id; keep `HttpStartupOutageIT` behavior unchanged by delegating to it), extend `WireMockJournal` with `countMatching(env, method, urlPath, headerName, headerValue)` (match `request.headers`, same exact-URL rule), and create `HttpReceiverCommandRoundTripIT` (R3), `HttpReceiverReconnectIT` (R1/R2/R5: subscribe-GET count rises after the outage, a new reply POST appears), `HttpReceiverShutdownDuringReconnectIT` (R4: `stop()` < 10 s, no `Receiver-*` thread).

- [ ] **Step 1: R3 test** — register `sseStubFor(path, "m-r3")`; start `ReceiverSettings.http(env)` `Njams`; `WireMockJournal.awaitCount(env, "POST", "/api/httpcommunication/reply", ...)` with header `njams-reply-for = m-r3` reaches ≥ 1 within 30 s.
- [ ] **Step 2: R1/R2/R5 tests** — outage via the `http` proxy down toxic (`OUTAGE_MS`), then removal; assert the subscribe-GET count increases after removal and a reply POST for a fresh message id appears; R2 adds the toxic before constructing `Njams` with `PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR=RECONNECT`; R5 additionally asserts a single `Receiver-Recovery-Cycle-Thread` at most.
- [ ] **Step 3: R4 test** as in Task 4 Step 3, thread check only.
- [ ] **Step 4: Run** the HTTP classes — Expected: PASS; findings as before.
- [ ] **Step 5: Commit** `SDK-483 Add HTTP receiver ITs (R1-R5)`.

## Task 8 — Documentation, full run, finish

- [ ] **Step 1:** Update `njams-sdk-communication-it/README.md`: scenario rows for R1–R7 in the *Scenarios* table (columns: `# | Scenario / fault | Expected behavior | Test class(es) | Notes`), the "Receiver threads across start/stop" row (JMS leak check R7), the *Support code* bullet (new helpers), the *Docker environment* Jolokia remark, and the IT count/duration line in *How to run* (updated after Step 2).
- [ ] **Step 2:** Run the full suite once (command in *Global Constraints*, no `-Dit.test`); record the IT count and duration for the README. Expected: `BUILD SUCCESS`.
- [ ] **Step 3:** Update the spec header status line (plan implemented) and, if the feasibility spike changed anything, §6.2's open point.
- [ ] **Step 4:** Commit `SDK-483 Document the receiver ITs`.
- [ ] **Step 5:** Hand over to `njams-ticket-finish` when the user is ready to resolve SDK-483 (it removes this plan file, keeps the spec, posts the closing comment).

## Self-Review Checklist

- Spec coverage: R1→Tasks 4/7, R2→4/7, R3→3/7, R4→4/7, R5→5/7, R6→6, R7→5; §7 receiver row→R4/R7; open feasibility point→Task 1.
- No production code changes; findings become separate tickets.
- Names consistent: `ReceiverSettings.jms/http/COMMANDS_TOPIC`, `JmsCommandClient.request/awaitReply`, `DockerEnvironment.jolokiaRead/commandsTopicConsumerCount/awaitCommandsTopicConsumerCount`.
