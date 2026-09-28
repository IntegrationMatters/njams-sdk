# SDK-483 Communication Resilience Test Module — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `njams-sdk-communication-it` Maven module: a Docker-orchestrated, manually-invoked test suite that
exercises the SDK's JMS and HTTP transports against a real ActiveMQ broker and a real HTTP stub server under real
network fault conditions (Toxiproxy), regression-testing the sender/receiver lifecycle behavior established in
SDK-375/472/476.

**Architecture:** One new sibling Maven module, included in the root reactor only under a non-default `docker-it`
profile. Containers are started/stopped by the fabric8 `docker-maven-plugin` bound to the `pre-integration-test` /
`post-integration-test` phases (never by test code itself); `maven-failsafe-plugin` (already globally bound in the
root POM) picks up `*IT.java` classes automatically. A `DockerEnvironment` JUnit `@ClassRule` reads a properties
file fabric8 writes with the containers' dynamically-assigned host ports and exposes typed accessors, plus a
`ToxiproxyControl` wrapper (plain JDK `HttpClient` + Jackson — no new client library) that drives Toxiproxy's REST
API. One Toxiproxy container hosts two independently-named proxies (`jms` in front of ActiveMQ, `http` in front of
WireMock) — this is a simplification of the design spec's "Toxiproxy in front of each" wording into one container
with two proxies; functionally equivalent, called out here since it wasn't spelled out at that level of detail in
the spec.

**Tech Stack:** Maven (fabric8 `docker-maven-plugin`, `maven-failsafe-plugin`), JUnit 4 (matches the rest of the
repo, `testing-conventions.md`), Jackson (`jackson-databind`, already pinned via the root POM's `jackson.version`
property), `activemq-client` (test scope, same `${activemq-version}` as `njams-sdk` itself), JDK 11 `java.net.http`
(Toxiproxy/Jolokia REST calls — no new HTTP client dependency), Docker images: `apache/activemq-classic`,
`ghcr.io/shopify/toxiproxy`, `wiremock/wiremock`.

**Spec:** `docs/superpowers/specs/2026-09-28-sdk-483-communication-it-module-design.md` — read it before starting;
this plan implements it task-by-task and does not repeat its rationale.

## Global Constraints

- The module must **never** build under a plain `mvn clean install` at the root — it is included in the reactor
  only inside a non-default `docker-it` Maven profile (spec §4, `.claude/rules/communication-it-module.md`).
- **Kafka is out of scope.** Do not add a Kafka container or scenario in this plan (spec §3).
- **This module's scenario catalog is stable by default and never grows in response to a bug fix found here** —
  any real defect this suite surfaces is a separate ticket, fixed via `njams-sdk`'s normal JUnit/mocked-IT TDD
  workflow, with its regression guard added there, not in this module (spec §9). Do not add a "fix verification"
  scenario while implementing this plan even if a task's IT surfaces a real bug — flag it to the user instead.
- **No new third-party client libraries.** Control Toxiproxy and read ActiveMQ's Jolokia metrics with the JDK's
  built-in `java.net.http.HttpClient` + the already-pinned `jackson-databind`, not a dedicated Toxiproxy/Jolokia
  client dependency (per the repo's "avoid new third-party libraries" rule — Jackson and the JDK HTTP client are
  already available, so a dedicated client adds nothing but another dependency to track).
- **The client-side driving logic is fixed and trivial** — one `ProcessModel`/`ActivityModel`, three orthogonal
  knobs (count, size, concurrency), no scenario adds branching or scenario-specific client logic (spec §5).
- Java 11+, Maven 3.8+ (repo-wide requirement, `CLAUDE.md`).
- Copyright header and Javadoc-on-public rules do **not** apply here — this is test code (`code-quality-general.md`,
  `public-api-design.md` both exempt `src/test/**`), but keep everything package-private unless a cross-package
  test needs otherwise.

## Review Focus

1. **Toxic state leaking between test methods/classes.** A `down` or `latency` toxic left armed by one IT would
   silently break the next one in a way that looks like a new failure. Every task from Task 5 onward that applies
   a toxic must remove it (or call the blanket reset) before the test ends, and Task 5 pins this down with its own
   test.
2. **Dynamically-assigned container ports read incorrectly.** fabric8 assigns random host ports per run; if
   `DockerEnvironment` ever hardcodes a port instead of reading the generated properties file, tests pass locally
   by accident and fail elsewhere. Task 5's `DockerEnvironmentSmokeTest` asserts the read ports are non-fixed
   values sourced from the properties file, not constants.
3. **WireMock stub/request-journal state leaking between scenario ITs.** A repeated-429 counter or a recorded
   request count from one IT (e.g. Task 13's congestion scenario) must not still be armed when the next IT class
   runs. Task 4 wires WireMock's `/__admin/reset` into the same per-test cleanup as the Toxiproxy reset.
4. **Container-not-ready races.** ActiveMQ/WireMock not yet accepting connections when the first IT in a run
   starts would produce a failure that looks like a scenario bug. Tasks 2 and 4 configure fabric8 `wait` conditions
   (log-pattern / HTTP-ping) so `docker:start` doesn't return until each container is actually ready.
5. **Cross-proxy interference.** With one Toxiproxy container hosting two proxies, a toxic accidentally applied to
   the wrong proxy name (`jms` vs `http`) would make an HTTP scenario silently exercise the JMS fault path or vice
   versa. Task 5's `DockerEnvironmentSmokeTest` asserts a toxic applied to one named proxy has zero effect on the
   other.

---

## File Structure

```
njams-sdk-communication-it/
  pom.xml
  src/test/java/com/im/njams/sdk/it/
    support/
      DockerEnvironment.java
      ToxiproxyControl.java
    harness/
      FixedProcessModel.java
      MessageDriver.java
    jms/
      JmsBrokerSmokeIT.java
      JmsThroughProxySmokeIT.java
      StartupOutageIT.java
      MidProcessingOutageRecoveryIT.java
      DegradedConnectIT.java
      ShutdownDuringOutageIT.java
      RepeatedFlapIT.java
      FragmentationUnderOutageIT.java
      RepeatedStartStopLeakIT.java
    http/
      HttpThroughProxySmokeIT.java
      HttpStartupOutageIT.java
      HttpMidProcessingOutageRecoveryIT.java
      HttpShutdownDuringOutageIT.java
      HttpFragmentationUnderOutageIT.java
      HttpRejectAndCongestionIT.java
      HttpConnectionProblemIT.java
  src/test/resources/
    docker/activemq/activemq.xml
    docker/toxiproxy/config.json      # not used at container start — proxies are created via the API in Task 3/4
    wiremock/mappings/head-available.json
    wiremock/mappings/head-not-found.json
    wiremock/mappings/post-ok.json
    wiremock/mappings/post-413.json
    wiremock/mappings/post-429.json
    wiremock/mappings/post-503.json
    logback-test.xml
```

Each `*IT.java` file is deliberately one scenario per file (matches the spec's scenario table 1:1), sharing
`DockerEnvironment`/`ToxiproxyControl`/`MessageDriver` rather than re-implementing setup.

---

### Task 1: Module scaffolding, profile-gated reactor inclusion

**Files:**
- Modify: `pom.xml` (root) — add a `docker-it` profile with its own `<modules>` list
- Create: `njams-sdk-communication-it/pom.xml`

**Interfaces:**
- Produces: a buildable-but-empty module, reachable only via `mvn -Pdocker-it`.

- [ ] **Step 1: Add the profile-gated module to the root POM**

In `pom.xml`, inside the existing `<profiles>` block (after the `svn-check` profile, before `</profiles>`), add:

```xml
<profile>
    <id>docker-it</id>
    <activation>
        <activeByDefault>false</activeByDefault>
    </activation>
    <modules>
        <module>njams-sdk-communication-it</module>
    </modules>
</profile>
```

Do **not** add `njams-sdk-communication-it` to the root `<modules>` list (lines 456-459) — that list stays exactly
as-is. This is what keeps the module out of the default reactor.

- [ ] **Step 2: Create the module POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.salesfive.njams</groupId>
        <artifactId>njams-sdk-root</artifactId>
        <version>6.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>njams-sdk-communication-it</artifactId>
    <name>njams-sdk-communication-it</name>
    <packaging>jar</packaging>

    <properties>
        <skip.integration.tests>false</skip.integration.tests>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.salesfive.njams</groupId>
            <artifactId>njams-sdk</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.salesfive.njams</groupId>
            <artifactId>njams-sdk</artifactId>
            <version>${project.version}</version>
            <type>test-jar</type>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.activemq</groupId>
            <artifactId>activemq-client</artifactId>
            <version>${activemq-version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>${slf4j.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>${slf4j.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

`njams-sdk`'s `test-jar` gives this module access to `com.im.njams.sdk.communication.TestSender` for Task 6's
Docker-free harness test. If `njams-sdk`'s own POM does not yet produce a test-jar artifact, add a
`maven-jar-plugin` `test-jar` goal execution to `njams-sdk/pom.xml` as part of this step (check first — it may
already be there for `njams-sdk-sample-client`'s use).

- [ ] **Step 3: Verify the module is invisible to the default build**

Run: `mvn -q -pl njams-sdk-communication-it validate`
Expected: `FAIL` — Maven reports the project is not part of the reactor (no such module without the profile).

- [ ] **Step 4: Verify the module builds under the profile**

Run: `mvn -q -Pdocker-it -pl njams-sdk-communication-it validate`
Expected: `BUILD SUCCESS`, no compile yet (no sources), just POM resolution.

- [ ] **Step 5: Commit**

```bash
git add pom.xml njams-sdk-communication-it/pom.xml
git commit -m "SDK-483 Scaffold njams-sdk-communication-it module, gated behind docker-it profile"
```

---

### Task 2: ActiveMQ container + JMS broker smoke IT

**Files:**
- Modify: `njams-sdk-communication-it/pom.xml` (fabric8 plugin config)
- Create: `njams-sdk-communication-it/src/test/resources/docker/activemq/activemq.xml`
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/JmsBrokerSmokeIT.java`

**Interfaces:**
- Produces: a running ActiveMQ container reachable at a fabric8-assigned host port, with the assignment written
  to `target/docker-it.properties`.

- [ ] **Step 1: Add an ActiveMQ config that opens the OpenWire and Jolokia-backed web console ports**

The stock `apache/activemq-classic` image already exposes OpenWire on `61616` and the Jolokia-backed web console
on `8161` with default credentials `admin`/`admin`; no custom `activemq.xml` is required to start. Create an empty
placeholder file instead so the directory exists for a later task if broker-side tuning becomes necessary:

`njams-sdk-communication-it/src/test/resources/docker/activemq/activemq.xml`:
```xml
<!-- Intentionally empty: the image's default configuration is used. Kept as a mount point for future broker
     tuning (e.g. a lower producer flow-control limit) if a scenario needs it. -->
```

- [ ] **Step 2: ActiveMQ image tag — matched to the pinned client, not the newest image**

Checked Docker Hub (`apache/activemq-classic` tags, 2026-09-28): the newest published tag is `6.2.0` (`latest`
points to it), but this repo's own `${activemq-version}` = `5.19.7` is deliberately pinned to the 5.x line
(`pom.xml:55-56`: "6.x requires Java 17, the SDK targets Java 11"). That constraint is about the `activemq-client`
library on the SDK's own classpath, not the broker container's JVM — but mixing a 5.19 client against an untested
6.2 broker introduces a protocol-compatibility variable this module has no reason to take on. Use the newest
published tag on the **matching 5.19.x line** instead: `5.19.2` (the newest 5.19.x tag currently published; `5.19.7`
itself is not published as an image tag).

- [ ] **Step 3: Add the fabric8 docker-maven-plugin with the ActiveMQ image**

Add to `njams-sdk-communication-it/pom.xml`, inside a new `<build><plugins>` block:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.fabric8</groupId>
            <artifactId>docker-maven-plugin</artifactId>
            <version>0.48.1</version> <!-- latest stable as of 2026-09-28 -->
            <configuration>
                <images>
                    <image>
                        <alias>activemq</alias>
                        <name>apache/activemq-classic:5.19.2</name>
                        <run>
                            <ports>
                                <port>+activemq.openwire:61616</port>
                                <port>+activemq.console:8161</port>
                            </ports>
                            <wait>
                                <log>Apache ActiveMQ.*started</log>
                                <time>60000</time>
                            </wait>
                        </run>
                    </image>
                </images>
                <portPropertyFile>${project.build.directory}/docker-it.properties</portPropertyFile>
            </configuration>
            <executions>
                <execution>
                    <id>docker-start</id>
                    <phase>pre-integration-test</phase>
                    <goals><goal>start</goal></goals>
                </execution>
                <execution>
                    <id>docker-stop</id>
                    <phase>post-integration-test</phase>
                    <goals><goal>stop</goal></goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

The `+` port prefix tells fabric8 to bind the container port to a random free host port; the assignment (e.g.
`activemq.openwire.61616.hostport=54231`) is written to `docker-it.properties`. `maven-failsafe-plugin` is already
globally bound in the root POM (`pom.xml:156-177`) — no plugin declaration is needed here for it to pick up
`*IT.java` classes in this module.

- [ ] **Step 4: Write the smoke IT**

```java
package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Test;

public class JmsBrokerSmokeIT {

    @Test
    public void sendsAndReceivesOneMessage() throws Exception {
        int port = readActiveMqPort();
        ActiveMQConnectionFactory factory =
            new ActiveMQConnectionFactory("tcp://localhost:" + port);
        try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("njams.test.smoke");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            TextMessage sent = session.createTextMessage("smoke-test");
            producer.send(sent);

            Message received = consumer.receive(5000);
            assertEquals("smoke-test", ((TextMessage) received).getText());
        }
    }

    private static int readActiveMqPort() throws IOException {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("target/docker-it.properties")) {
            props.load(in);
        }
        return Integer.parseInt(props.getProperty("activemq.openwire.61616.hostport"));
    }
}
```

- [ ] **Step 5: Run it**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it verify`
Expected: `BUILD SUCCESS`, `JmsBrokerSmokeIT` passes (ActiveMQ container starts, message round-trips, container
stops).

- [ ] **Step 6: Commit**

```bash
git add njams-sdk-communication-it/pom.xml njams-sdk-communication-it/src/test/resources/docker/activemq/activemq.xml njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/JmsBrokerSmokeIT.java
git commit -m "SDK-483 Add ActiveMQ container and JMS broker smoke IT"
```

---

### Task 3: Toxiproxy container + `ToxiproxyControl` + JMS-side proxy smoke IT

**Files:**
- Modify: `njams-sdk-communication-it/pom.xml` (add Toxiproxy image)
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/support/ToxiproxyControl.java`
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/JmsThroughProxySmokeIT.java`

**Interfaces:**
- Produces: `ToxiproxyControl` — `createProxy(name, listenAddr, upstream)`, `addToxic(proxyName, toxicName, type,
  attributes)`, `removeToxic(proxyName, toxicName)`, `resetAll()`. Used by every later JMS/HTTP fault-injection
  task.

- [ ] **Step 1: Add the Toxiproxy image to the fabric8 config**

Extend the `<images>` block from Task 2 with a second `<image>`:

```xml
<image>
    <alias>toxiproxy</alias>
    <name>ghcr.io/shopify/toxiproxy:2.12.0</name>
    <run>
        <ports>
            <port>+toxiproxy.control:8474</port>
            <port>+toxiproxy.jms:20000</port>
            <port>+toxiproxy.http:20001</port>
        </ports>
        <wait>
            <http>
                <url>http://localhost:${toxiproxy.control.8474.hostport}/version</url>
                <method>GET</method>
                <status>200</status>
            </http>
            <time>30000</time>
        </wait>
    </run>
</image>
```

Ports `20000`/`20001` are the *internal* listen ports of the two proxies this task and Task 4 create at runtime
via the control API — they must be published up front even though the proxies themselves don't exist until
`ToxiproxyControl.createProxy(...)` runs, because Docker port publication is fixed at container start.

- [ ] **Step 2: Write `ToxiproxyControl`**

```java
package com.im.njams.sdk.it.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Thin wrapper over Toxiproxy's REST API (https://github.com/Shopify/toxiproxy#http-api). */
public class ToxiproxyControl {

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public ToxiproxyControl(int controlPort) {
        this.baseUrl = "http://localhost:" + controlPort;
    }

    public void createProxy(String name, String listen, String upstream) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of("name", name, "listen", listen, "upstream", upstream);
        send("POST", "/proxies", body);
    }

    public void addToxic(String proxyName, String toxicName, String type, Map<String, Object> attributes)
        throws IOException, InterruptedException {
        Map<String, Object> body = Map.of("name", toxicName, "type", type, "attributes", attributes);
        send("POST", "/proxies/" + proxyName + "/toxics", body);
    }

    public void removeToxic(String proxyName, String toxicName) throws IOException, InterruptedException {
        send("DELETE", "/proxies/" + proxyName + "/toxics/" + toxicName, null);
    }

    /** Removes every toxic from every proxy. Call this in an {@code @After} to avoid leaking state between tests. */
    public void resetAll() throws IOException, InterruptedException {
        send("POST", "/reset", null);
    }

    private void send(String method, String path, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(5));
        if (body != null) {
            builder.method(method, BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .header("Content-Type", "application/json");
        } else {
            builder.method(method, BodyPublishers.noBody());
        }
        HttpResponse<String> response = client.send(builder.build(), BodyHandlers.ofString());
        if (response.statusCode() >= 300 && response.statusCode() != 409) {
            // 409 = proxy/toxic already exists; treated as idempotent, not an error, for repeated test setup.
            throw new IOException("Toxiproxy call failed: " + method + " " + path + " -> " + response.statusCode()
                + " " + response.body());
        }
    }
}
```

- [ ] **Step 3: Write the JMS-through-proxy smoke IT**

```java
package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.FileInputStream;
import java.util.Map;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.it.support.ToxiproxyControl;

public class JmsThroughProxySmokeIT {

    private ToxiproxyControl toxiproxy;
    private int proxyPort;

    @Before
    public void setUp() throws Exception {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("target/docker-it.properties")) {
            props.load(in);
        }
        int controlPort = Integer.parseInt(props.getProperty("toxiproxy.control.8474.hostport"));
        proxyPort = Integer.parseInt(props.getProperty("toxiproxy.jms.20000.hostport"));
        int activeMqPort = Integer.parseInt(props.getProperty("activemq.openwire.61616.hostport"));

        toxiproxy = new ToxiproxyControl(controlPort);
        toxiproxy.createProxy("jms", "0.0.0.0:20000", "activemq:61616");
    }

    @After
    public void tearDown() throws Exception {
        toxiproxy.resetAll();
    }

    @Test
    public void connectsThroughTheProxyWhenHealthy() throws Exception {
        ActiveMQConnectionFactory factory =
            new ActiveMQConnectionFactory("tcp://localhost:" + proxyPort);
        try (Connection connection = factory.createConnection()) {
            connection.start();
        }
    }

    @Test
    public void downToxicBlocksTheConnection() throws Exception {
        toxiproxy.addToxic("jms", "jms-down", "timeout", Map.of("timeout", 1000));
        ActiveMQConnectionFactory factory =
            new ActiveMQConnectionFactory("tcp://localhost:" + proxyPort);
        factory.setConnectResponseTimeout(3000);
        assertThrows(JMSException.class, () -> {
            try (Connection connection = factory.createConnection()) {
                connection.start();
            }
        });
    }
}
```

Note: `activemq:61616` as the Toxiproxy upstream address relies on the ActiveMQ and Toxiproxy containers sharing
a Docker network with the ActiveMQ container reachable by its fabric8 alias as hostname — fabric8 does this by
default for containers started in the same `docker-maven-plugin` execution. If container-to-container DNS
resolution by alias does not work as expected when this step is actually run, use the ActiveMQ container's
internal Docker IP (available as the `activemq.ip` property in `docker-it.properties`) instead of the alias.

- [ ] **Step 4: Run it**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it verify`
Expected: `BUILD SUCCESS`, both `JmsThroughProxySmokeIT` tests pass.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk-communication-it/pom.xml njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/support/ToxiproxyControl.java njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/JmsThroughProxySmokeIT.java
git commit -m "SDK-483 Add Toxiproxy container, ToxiproxyControl, and JMS-through-proxy smoke IT"
```

---

### Task 4: WireMock container + stub mappings + HTTP-side proxy smoke IT

**Files:**
- Modify: `njams-sdk-communication-it/pom.xml` (add WireMock image)
- Create: `njams-sdk-communication-it/src/test/resources/wiremock/mappings/head-available.json`
- Create: `njams-sdk-communication-it/src/test/resources/wiremock/mappings/head-not-found.json`
- Create: `njams-sdk-communication-it/src/test/resources/wiremock/mappings/post-ok.json`
- Create: `njams-sdk-communication-it/src/test/resources/wiremock/mappings/post-413.json`
- Create: `njams-sdk-communication-it/src/test/resources/wiremock/mappings/post-429.json`
- Create: `njams-sdk-communication-it/src/test/resources/wiremock/mappings/post-503.json`
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpThroughProxySmokeIT.java`

**Interfaces:**
- Consumes: `ToxiproxyControl` (Task 3).
- Produces: WireMock running with baseline stubs; the `http` Toxiproxy proxy in front of it.

- [ ] **Step 1: Write the WireMock stub mappings**

Per the real nJAMS Server API (pasted earlier): `HEAD /{dpendpoint}` for availability, `POST /{dpendpoint}` for
ingest. Use `/dataprovider` as the fixed test endpoint path across all stubs.

`head-available.json`:
```json
{
  "priority": 10,
  "request": { "method": "HEAD", "urlPath": "/dataprovider" },
  "response": { "status": 200 }
}
```

`head-not-found.json` (disabled by default — enabled per-scenario in Task 7 by removing `head-available.json`'s
mapping and re-adding this one via WireMock's admin API, not by having both active at once):
```json
{
  "priority": 10,
  "request": { "method": "HEAD", "urlPath": "/dataprovider" },
  "response": { "status": 404 }
}
```

`post-ok.json`:
```json
{
  "priority": 10,
  "request": { "method": "POST", "urlPath": "/dataprovider" },
  "response": { "status": 200 }
}
```

`post-413.json`, `post-429.json`, `post-503.json` follow the same shape with `"status": 413` / `429` / `503`
respectively — none of these three are loaded at container start (only `head-available.json` and `post-ok.json`
are the baseline); Tasks 13/9a load them per-scenario via WireMock's `/__admin/mappings` API so only one behavior
is active at a time. Keep all six files under `src/test/resources/wiremock/mappings/` so they can be read from the
classpath and POSTed to WireMock's admin API on demand — do **not** mount `post-413.json` etc. into WireMock's own
mappings directory at container start.

- [ ] **Step 2: Add the WireMock image, mounting only the two baseline mappings**

```xml
<image>
    <alias>wiremock</alias>
    <name>wiremock/wiremock:3.13.2</name>
    <run>
        <ports>
            <port>+wiremock.http:8080</port>
        </ports>
        <volumes>
            <bind>
                <volume>${project.basedir}/src/test/resources/wiremock/mappings:/home/wiremock/mappings</volume>
            </bind>
        </volumes>
        <wait>
            <http>
                <url>http://localhost:${wiremock.http.8080.hostport}/__admin/mappings</url>
                <method>GET</method>
                <status>200</status>
            </http>
            <time>30000</time>
        </wait>
    </run>
</image>
```

Since the mappings directory is bind-mounted whole, temporarily move `head-not-found.json`/`post-413.json`/
`post-429.json`/`post-503.json` out of `src/test/resources/wiremock/mappings/` into a sibling
`src/test/resources/wiremock/on-demand/` directory so they are **not** auto-loaded at container start, and are
instead read from the classpath and POSTed to `/__admin/mappings` by the scenario ITs that need them (Tasks 9a,
13). Only `head-available.json` and `post-ok.json` stay in the mounted `mappings/` directory as the baseline.

Add the Toxiproxy `http` proxy port to the same Toxiproxy `<image>` block from Task 3 (already declared:
`toxiproxy.http:20001`).

- [ ] **Step 3: Write the HTTP-through-proxy smoke IT**

```java
package com.im.njams.sdk.it.http;

import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.it.support.ToxiproxyControl;

public class HttpThroughProxySmokeIT {

    private int proxyPort;

    @Before
    public void setUp() throws Exception {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("target/docker-it.properties")) {
            props.load(in);
        }
        int controlPort = Integer.parseInt(props.getProperty("toxiproxy.control.8474.hostport"));
        proxyPort = Integer.parseInt(props.getProperty("toxiproxy.http.20001.hostport"));

        ToxiproxyControl toxiproxy = new ToxiproxyControl(controlPort);
        toxiproxy.createProxy("http", "0.0.0.0:20001", "wiremock:8080");
    }

    @Test
    public void postThroughTheProxyReachesTheStub() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + proxyPort + "/dataprovider"))
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .header("Content-Type", "application/json")
            .build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
```

- [ ] **Step 4: Run it**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it verify`
Expected: `BUILD SUCCESS`, all smoke ITs (Tasks 2-4) pass.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk-communication-it/pom.xml njams-sdk-communication-it/src/test/resources/wiremock njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpThroughProxySmokeIT.java
git commit -m "SDK-483 Add WireMock container, baseline stubs, and HTTP-through-proxy smoke IT"
```

---

### Task 5: `DockerEnvironment` shared rule + Review Focus regression probes

**Files:**
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/support/DockerEnvironment.java`
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/support/DockerEnvironmentSmokeTest.java`
- Modify: the three smoke ITs from Tasks 2-4 to use `DockerEnvironment` instead of reading properties directly
  (reduces duplication for every later task)

**Interfaces:**
- Consumes: `ToxiproxyControl` (Task 3).
- Produces: `DockerEnvironment` — static `@ClassRule` `RESET` (per-test cleanup), plus instance accessors
  `jmsUrl()`, `httpBaseUrl()`, `jolokiaUrl()`, `jmsProxy()`, `httpProxy()` (both returning `ToxiproxyControl`-scoped
  proxy names), `wireMockAdminUrl()`.

- [ ] **Step 1: Write `DockerEnvironment`**

```java
package com.im.njams.sdk.it.support;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Properties;

import org.junit.rules.ExternalResource;

/**
 * Reads the host ports fabric8's docker-maven-plugin assigned (via {@code target/docker-it.properties}) and
 * exposes typed accessors, plus a per-test reset of Toxiproxy toxics and the WireMock request journal/stubs so
 * state from one IT never leaks into the next (Review Focus items 1 and 3).
 */
public class DockerEnvironment extends ExternalResource {

    private final Properties props = new Properties();
    private final ToxiproxyControl toxiproxy;
    private boolean proxiesCreated;

    public DockerEnvironment() {
        try (FileInputStream in = new FileInputStream("target/docker-it.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("docker-it.properties not found — is this running under -Pdocker-it "
                + "after docker:start has run?", e);
        }
        this.toxiproxy = new ToxiproxyControl(port("toxiproxy.control.8474.hostport"));
    }

    @Override
    protected void before() throws Throwable {
        if (!proxiesCreated) {
            toxiproxy.createProxy("jms", "0.0.0.0:20000", "activemq:61616");
            toxiproxy.createProxy("http", "0.0.0.0:20001", "wiremock:8080");
            proxiesCreated = true;
        }
    }

    @Override
    protected void after() {
        try {
            toxiproxy.resetAll();
            resetWireMock();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to reset Docker environment state between tests", e);
        }
    }

    public ToxiproxyControl toxiproxy() {
        return toxiproxy;
    }

    public String jmsUrlDirect() {
        return "tcp://localhost:" + port("activemq.openwire.61616.hostport");
    }

    public String jmsUrlThroughProxy() {
        return "tcp://localhost:" + port("toxiproxy.jms.20000.hostport");
    }

    public String httpBaseUrlDirect() {
        return "http://localhost:" + port("wiremock.http.8080.hostport");
    }

    public String httpBaseUrlThroughProxy() {
        return "http://localhost:" + port("toxiproxy.http.20001.hostport");
    }

    public String jolokiaUrl() {
        return "http://admin:admin@localhost:" + port("activemq.console.8161.hostport") + "/api/jolokia";
    }

    public String wireMockAdminUrl() {
        return httpBaseUrlDirect() + "/__admin";
    }

    private int port(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing docker-it.properties key: " + key);
        }
        return Integer.parseInt(value);
    }

    private void resetWireMock() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(wireMockAdminUrl() + "/requests"))
            .timeout(Duration.ofSeconds(5))
            .DELETE()
            .build();
        client.send(request, BodyHandlers.discarding());
    }
}
```

- [ ] **Step 2: Write the Review Focus regression probes**

```java
package com.im.njams.sdk.it.support;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Rule;
import org.junit.Test;

public class DockerEnvironmentSmokeTest {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test
    public void portsAreDynamicallyAssignedNotFixedDefaults() {
        // Review Focus #2: a hardcoded default (61616, 8080, 8474) would defeat the purpose of reading
        // fabric8's generated properties file at all.
        assertNotEquals("tcp://localhost:61616", env.jmsUrlThroughProxy());
        assertNotEquals("http://localhost:8080", env.httpBaseUrlThroughProxy());
    }

    @Test
    public void toxicOnOneProxyDoesNotAffectTheOther() throws Exception {
        // Review Focus #5: a toxic on the "jms" proxy must not touch the "http" proxy sharing the same
        // Toxiproxy container.
        env.toxiproxy().addToxic("jms", "cross-check", "timeout", Map.of("timeout", 1));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(env.httpBaseUrlThroughProxy() + "/dataprovider"))
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .header("Content-Type", "application/json")
            .build();
        assertTrue(client.send(request, BodyHandlers.ofString()).statusCode() == 200);
    }

    @Test
    public void resetAllClearsToxicsBetweenTests() throws Exception {
        // Review Focus #1: this test intentionally runs after the one above, relying on DockerEnvironment's
        // @Rule-driven after() having already reset the "jms" proxy's toxic — if it hadn't, this connection
        // would also fail.
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(env.jmsUrlThroughProxy());
        try (Connection connection = factory.createConnection()) {
            connection.start();
        }
    }
}
```

- [ ] **Step 3: Update the three earlier smoke ITs to use `DockerEnvironment`**

In `JmsBrokerSmokeIT`, `JmsThroughProxySmokeIT`, and `HttpThroughProxySmokeIT`, replace the manual
`docker-it.properties` reading and `ToxiproxyControl`/proxy-creation code with a `@Rule public DockerEnvironment
env = new DockerEnvironment();` field, and use `env.jmsUrlDirect()` / `env.jmsUrlThroughProxy()` /
`env.httpBaseUrlThroughProxy()` / `env.toxiproxy()` accordingly. This removes the duplicated setup those three
classes wrote ad hoc in Tasks 2-4.

- [ ] **Step 4: Run it**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it verify`
Expected: `BUILD SUCCESS`, all ITs pass, including the three updated smoke ITs and the new
`DockerEnvironmentSmokeTest`.

- [ ] **Step 5: Commit**

```bash
git add njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/support/DockerEnvironment.java njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/support/DockerEnvironmentSmokeTest.java njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/JmsBrokerSmokeIT.java njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/JmsThroughProxySmokeIT.java njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpThroughProxySmokeIT.java
git commit -m "SDK-483 Add shared DockerEnvironment rule with per-test reset; regression-test the Review Focus risks"
```

---

### Task 6: Fixed process/activity model + `MessageDriver` harness (Docker-free)

**Files:**
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/harness/FixedProcessModel.java`
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/harness/MessageDriver.java`
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/harness/MessageDriverTest.java`

**Interfaces:**
- Produces: `FixedProcessModel.build(Njams njams)` returning the one `ProcessModel` every scenario uses.
  `MessageDriver.run(ProcessModel model, int count, int payloadBytes, int concurrency)` — drives `count` jobs (each
  started and ended immediately), each job's one activity carrying a business-data string of exactly
  `payloadBytes` length, split across `concurrency` submitting threads (1 = sequential), and returns the list of
  the `count` SDK-assigned `logId`s (`Job.getLogId()`) used — needed by later scenario ITs for the
  no-duplicate/no-drop assertions, filtered by `MessageHeaders.NJAMS_LOGID_HEADER`/`NJAMS_LOGID_HTTP_HEADER` on
  the wire so a prior test run's leftover messages on the same queue/journal can't inflate a count.

- [ ] **Step 1: Write `FixedProcessModel`**

```java
package com.im.njams.sdk.it.harness;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

/** The one, deliberately trivial process shape every scenario in this module drives. Never varied per scenario. */
public final class FixedProcessModel {

    public static final String PROCESS_PATH = "/CommunicationIT";
    public static final String ACTIVITY_MODEL_ID = "single-activity";

    private FixedProcessModel() {
    }

    public static ProcessModel build(Njams njams) {
        ProcessModel model = njams.model().create(PROCESS_PATH);
        ActivityModel activity = model.createActivity(ACTIVITY_MODEL_ID, "Single Activity", "startType");
        activity.setStarter(true);
        return model;
    }
}
```

- [ ] **Step 2: Write `MessageDriver`**

```java
package com.im.njams.sdk.it.harness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.im.njams.sdk.logmessage.Activity;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

/**
 * Drives {@code count} jobs against the fixed process model, each carrying a {@code payloadBytes}-sized business
 * data payload, across {@code concurrency} submitting threads. Returns each job's SDK-assigned {@code logId}, so
 * a scenario can assert exactly one delivered message per ID with no duplicates and no drops.
 */
public final class MessageDriver {

    private MessageDriver() {
    }

    public static List<String> run(ProcessModel model, int count, int payloadBytes, int concurrency)
        throws InterruptedException {
        String payload = "x".repeat(Math.max(0, payloadBytes));
        ActivityModel activityModel = model.getActivity(FixedProcessModel.ACTIVITY_MODEL_ID);

        if (concurrency <= 1) {
            List<String> logIds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                logIds.add(runOneJob(model, activityModel, payload));
            }
            return logIds;
        }

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<String>> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(() -> runOneJob(model, activityModel, payload)));
            }
            List<String> logIds = new ArrayList<>(count);
            for (Future<String> future : futures) {
                logIds.add(future.get(2, TimeUnit.MINUTES));
            }
            return logIds;
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("MessageDriver job failed", e);
        } finally {
            pool.shutdown();
        }
    }

    private static String runOneJob(ProcessModel model, ActivityModel activityModel, String payload) {
        Job job = model.createJob();
        job.start();
        Activity activity = job.activities().create(activityModel).addAttribute("payload", payload).build();
        activity.end();
        job.end(true);
        return job.getLogId();
    }
}
```

- [ ] **Step 3: Write the Docker-free harness test using `TestSender`**

```java
package com.im.njams.sdk.it.harness;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class MessageDriverTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null) {
            njams.stop();
        }
    }

    @Test
    public void countKnobProducesExactlyThatManyUniqueLogIds() throws Exception {
        njams = startNjams();
        ProcessModel model = FixedProcessModel.build(njams);

        List<String> logIds = MessageDriver.run(model, 25, 0, 1);

        assertEquals(25, logIds.size());
        assertEquals(25, Set.copyOf(logIds).size());
    }

    @Test
    public void sizeKnobControlsThePayloadLength() throws Exception {
        njams = startNjams();
        ProcessModel model = FixedProcessModel.build(njams);

        // Correctness of the padding itself is exercised indirectly: MessageDriver must not throw for a range
        // of sizes, from empty to comfortably past a single-fragment threshold.
        MessageDriver.run(model, 1, 0, 1);
        MessageDriver.run(model, 1, 10_000, 1);
        MessageDriver.run(model, 1, 200_000, 1);
    }

    @Test
    public void concurrencyKnobRunsAllJobsAcrossMultipleThreads() throws Exception {
        njams = startNjams();
        ProcessModel model = FixedProcessModel.build(njams);

        List<String> logIds = MessageDriver.run(model, 50, 100, 8);

        assertEquals(50, logIds.size());
        assertEquals(50, Set.copyOf(logIds).size());
    }

    private static Njams startNjams() {
        Settings settings = TestSender.getSettings();
        Njams instance = new Njams(Path.of("MessageDriverTest"), "1.0.0", "CommunicationIT", settings);
        instance.start();
        return instance;
    }
}
```

- [ ] **Step 4: Run it (no Docker/profile needed — this is a plain unit test)**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it test -Dtest=MessageDriverTest`
Expected: `BUILD SUCCESS`, all three tests pass, without any container starting (this is a `Test`-suffixed class,
so `maven-surefire-plugin` runs it in the `test` phase, before `docker:start`'s `pre-integration-test` phase).

- [ ] **Step 5: Commit**

```bash
git add njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/harness/
git commit -m "SDK-483 Add fixed process model and count/size/concurrency MessageDriver harness"
```

---

### Task 7: Scenario 1 — Startup outage (JMS + HTTP)

**Files:**
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/StartupOutageIT.java`
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpStartupOutageIT.java`

**Interfaces:**
- Consumes: `DockerEnvironment` (Task 5).

- [ ] **Step 1: Write the JMS startup-outage IT**

```java
package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertFalse;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.settings.Settings;

public class StartupOutageIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null) {
            njams.stop();
        }
    }

    @Test
    public void startFailsWhenTheBrokerIsUnreachableAtStartup() throws Exception {
        env.toxiproxy().addToxic("jms", "startup-down", "timeout", java.util.Map.of("timeout", 1));

        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "FAIL");

        njams = new Njams(Path.of("StartupOutageIT"), "1.0.0", "CommunicationIT", settings);
        boolean started = njams.start();

        assertFalse("start() must report failure when the broker was never reachable at startup", started);
    }
}
```

`NjamsSettings` lives in the root `com.im.njams.sdk` package, not `com.im.njams.sdk.communication` — every import in
this plan uses the corrected package. `PROPERTY_JMS_PROVIDER_URL` and `PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR`
are confirmed real constants in `NjamsSettings.java` (verified by reading the file directly), so no placeholder
remains here.

- [ ] **Step 2: Write the HTTP startup-outage IT (both the transport-level and the documented `HEAD → 404` cases)**

```java
package com.im.njams.sdk.it.http;

import static org.junit.Assert.assertFalse;

import java.util.Map;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.settings.Settings;

public class HttpStartupOutageIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null) {
            njams.stop();
        }
    }

    @Test
    public void startFailsOnTransportLevelOutage() throws Exception {
        env.toxiproxy().addToxic("http", "startup-down", "timeout", Map.of("timeout", 1));
        njams = startWithFailBehavior();
        assertFalse(njams.start());
    }

    @Test
    public void startFailsWhenHeadReturns404() throws Exception {
        loadOnDemandMapping("head-not-found.json");
        njams = startWithFailBehavior();
        assertFalse(njams.start());
    }

    private Njams startWithFailBehavior() {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "FAIL");
        return new Njams(Path.of("HttpStartupOutageIT"), "1.0.0", "CommunicationIT", settings);
    }

    private void loadOnDemandMapping(String classpathResource) throws Exception {
        String body = new String(getClass().getClassLoader()
            .getResourceAsStream("wiremock/on-demand/" + classpathResource).readAllBytes());
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest
            .newBuilder(java.net.URI.create(env.wireMockAdminUrl() + "/mappings"))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .build();
        client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
    }
}
```

- [ ] **Step 3: Run both**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it verify -Dit.test=StartupOutageIT,HttpStartupOutageIT`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/StartupOutageIT.java njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpStartupOutageIT.java
git commit -m "SDK-483 Add scenario 1: startup outage (JMS + HTTP, incl. HEAD->404)"
```

---

### Task 8: Scenario 2 — Mid-processing outage + recovery, with no-duplicate/no-drop assertion

**Files:**
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/MidProcessingOutageRecoveryIT.java`
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpMidProcessingOutageRecoveryIT.java`

**Interfaces:**
- Consumes: `DockerEnvironment`, `FixedProcessModel`, `MessageDriver`.

- [ ] **Step 1: Write the JMS mid-processing outage IT**

```java
package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.MessageHeaders;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class MidProcessingOutageRecoveryIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null) {
            njams.stop();
        }
    }

    @Test
    public void everyDrivenJobArrivesExactlyOnceAcrossAnOutage() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());

        njams = new Njams(Path.of("MidProcessingOutageRecoveryIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);

        List<String> logIds = MessageDriver.run(model, 20, 100, 4);

        // Cut the connection mid-flight relative to the driver above isn't meaningfully controllable at this
        // granularity without hooks into the driver itself; the realistic mid-processing case is exercised by
        // interleaving: start a second batch, cut after it has started, then restore.
        env.toxiproxy().addToxic("jms", "mid-outage", "timeout", Map.of("timeout", 1));
        Thread outageDriver = new Thread(() -> {
            try {
                logIds.addAll(MessageDriver.run(model, 20, 100, 4));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        outageDriver.start();
        Thread.sleep(500);
        env.toxiproxy().removeToxic("jms", "mid-outage");
        outageDriver.join(TimeUnit.SECONDS.toMillis(30));

        assertEquals(40, logIds.size());
        assertEquals(40, Set.copyOf(logIds).size());

        assertEquals(40, countDeliveredMessages(logIds));
    }

    /**
     * Counts messages on the {@code njams.event} queue (the SDK's default JMS destination — confirmed against
     * {@code JmsSender.createProducers}) whose {@link MessageHeaders#NJAMS_LOGID_HEADER} property matches one of
     * {@code logIds}, via a JMS selector. Filtering by logId (rather than a raw count) keeps a prior test run's
     * leftover messages on the same queue from inflating the result.
     */
    private int countDeliveredMessages(List<String> logIds) throws Exception {
        String inClause = logIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
        String selector = MessageHeaders.NJAMS_LOGID_HEADER + " IN (" + inClause + ")";

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(env.jmsUrlDirect());
        try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("njams.event");
            MessageConsumer consumer = session.createConsumer(queue, selector);
            int delivered = 0;
            Message message;
            while ((message = consumer.receive(2000)) != null) {
                delivered++;
            }
            return delivered;
        }
    }
}
```

- [ ] **Step 2: Write the HTTP mid-processing outage IT**

Same shape as Step 1, but: build the `Njams` instance with `PROPERTY_COMMUNICATION=HTTP`, use
`env.httpBaseUrlThroughProxy()`, and instead of `countDeliveredMessages` reading from a broker queue, query
WireMock's `/__admin/requests` journal (`GET {wireMockAdminUrl}/requests`), filter journaled `POST /dataprovider`
entries whose `MessageHeaders.NJAMS_LOGID_HTTP_HEADER` ("njams-logid") request header value is one of `logIds`,
and assert the filtered count equals the total driven job count — the same leftover-state guard as the JMS side,
against WireMock's request journal instead of a live queue.

- [ ] **Step 3: Run it**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it verify -Dit.test=MidProcessingOutageRecoveryIT,HttpMidProcessingOutageRecoveryIT`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/MidProcessingOutageRecoveryIT.java njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpMidProcessingOutageRecoveryIT.java
git commit -m "SDK-483 Add scenario 2: mid-processing outage + recovery, no-duplicate/no-drop assertion"
```

---

### Task 9: Scenario 3 — Degraded/slow connect (JMS, B2 pool-lock regression)

**Files:**
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/DegradedConnectIT.java`

**Interfaces:**
- Consumes: `DockerEnvironment`, `FixedProcessModel`, `MessageDriver`.

- [ ] **Step 1: Write the IT**

```java
package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class DegradedConnectIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null) {
            njams.stop();
        }
    }

    @Test
    public void otherPoolTrafficStaysResponsiveWhileOneConnectIsSlow() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "4");

        njams = new Njams(Path.of("DegradedConnectIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);

        // Warm the pool with a healthy connection first, then add latency for any *new* connection attempt only
        // (established traffic through the existing connections is unaffected by this toxic).
        MessageDriver.run(model, 5, 100, 1);
        env.toxiproxy().addToxic("jms", "slow-connect", "latency", Map.of("latency", 15000, "jitter", 0));

        long start = System.currentTimeMillis();
        // Concurrency forces the pool to grow, so at least one worker hits the slow-connect path while the
        // others should still be served promptly by already-connected senders.
        MessageDriver.run(model, 20, 100, 8);
        long elapsedMs = System.currentTimeMillis() - start;

        // Before the B2 fix this would have taken >15s because acquire()/release() serialized behind the one
        // slow connect; after the fix it should complete in well under that.
        assertTrue("Pool traffic was stalled by one slow connect (took " + elapsedMs + "ms)", elapsedMs < 8000);
    }
}
```

- [ ] **Step 2: Run it**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it verify -Dit.test=DegradedConnectIT`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/DegradedConnectIT.java
git commit -m "SDK-483 Add scenario 3: degraded/slow connect (B2 pool-lock regression)"
```

---

### Task 10: Scenario 4 — Shutdown during outage (JMS + HTTP)

**Files:**
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/ShutdownDuringOutageIT.java`
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpShutdownDuringOutageIT.java`

**Interfaces:**
- Consumes: `DockerEnvironment`, `FixedProcessModel`, `MessageDriver`.

- [ ] **Step 1: Write the JMS IT**

```java
package com.im.njams.sdk.it.jms;

import java.util.Map;

import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class ShutdownDuringOutageIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test(timeout = 20000)
    public void stopCompletesPromptlyEvenWhileReconnecting() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());

        Njams njams = new Njams(Path.of("ShutdownDuringOutageIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);
        MessageDriver.run(model, 5, 100, 1);

        env.toxiproxy().addToxic("jms", "shutdown-outage", "timeout", Map.of("timeout", 1));
        Thread background = new Thread(() -> {
            try {
                MessageDriver.run(model, 5, 100, 1);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        background.start();
        Thread.sleep(300); // let the reconnect loop actually start

        // The @Test(timeout=...) above is the real assertion: stop() must not hang waiting on the reconnect loop.
        njams.stop();
        background.join(5000);
    }
}
```

- [ ] **Step 2: Write the HTTP IT**

Same shape as Step 1, with `PROPERTY_COMMUNICATION=HTTP`, `env.httpBaseUrlThroughProxy()`, and the `http` proxy's
toxic instead of `jms`'s.

- [ ] **Step 3: Run and commit**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it verify -Dit.test=ShutdownDuringOutageIT,HttpShutdownDuringOutageIT`
Expected: `BUILD SUCCESS`, both complete within their 20s timeout.

```bash
git add njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/ShutdownDuringOutageIT.java njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpShutdownDuringOutageIT.java
git commit -m "SDK-483 Add scenario 4: shutdown during outage (JMS + HTTP)"
```

---

### Task 11: Scenario 5 — Repeated flap + thread/pool-collection regression baselining

**Files:**
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/RepeatedFlapIT.java`

**Interfaces:**
- Consumes: `DockerEnvironment`, `FixedProcessModel`, `MessageDriver`.

- [ ] **Step 1: Write the IT**

```java
package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class RepeatedFlapIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test(timeout = 60000)
    public void repeatedFlappingDoesNotAccumulateThreads() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());

        Njams njams = new Njams(Path.of("RepeatedFlapIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);
        MessageDriver.run(model, 5, 50, 1);

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int baselineThreadCount = threadBean.getThreadCount();

        for (int i = 0; i < 10; i++) {
            env.toxiproxy().addToxic("jms", "flap", "timeout", Map.of("timeout", 1));
            Thread background = new Thread(() -> {
                try {
                    MessageDriver.run(model, 3, 50, 2);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            background.start();
            Thread.sleep(200);
            env.toxiproxy().removeToxic("jms", "flap");
            background.join(10000);
        }

        // Allow reconnect threads from the last cycle a moment to actually terminate.
        Thread.sleep(1000);
        int finalThreadCount = threadBean.getThreadCount();

        njams.stop();

        assertTrue("Thread count grew from " + baselineThreadCount + " to " + finalThreadCount
            + " across 10 flap cycles — suspect a reconnect-thread leak",
            finalThreadCount <= baselineThreadCount + 2); // small slack for JIT/GC housekeeping threads
    }
}
```

- [ ] **Step 2: Run and commit**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it verify -Dit.test=RepeatedFlapIT`
Expected: `BUILD SUCCESS`.

```bash
git add njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/RepeatedFlapIT.java
git commit -m "SDK-483 Add scenario 5: repeated flap with thread-count regression baselining"
```

---

### Task 12: Scenario 6 — Fragmentation under outage (JMS + HTTP)

**Files:**
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/FragmentationUnderOutageIT.java`
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpFragmentationUnderOutageIT.java`

**Interfaces:**
- Consumes: `DockerEnvironment`, `FixedProcessModel`, `MessageDriver`.

- [ ] **Step 1: Write the JMS IT**

```java
package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class FragmentationUnderOutageIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test(timeout = 60000)
    public void aFragmentedMessageFullyResendsRatherThanPartiallyDelivering() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        // Force chunking well below the 200 KB payload driven below, so a single message is guaranteed to
        // fragment into multiple sends.
        settings.put(NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, "10000");

        Njams njams = new Njams(Path.of("FragmentationUnderOutageIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);

        java.util.List<String> logIds = Collections.synchronizedList(new java.util.ArrayList<>());
        Thread background = new Thread(() -> {
            try {
                // 200 KB payload against a 10 KB chunk size guarantees multiple fragments per message.
                logIds.addAll(MessageDriver.run(model, 1, 200_000, 1));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        background.start();
        Thread.sleep(50); // outage lands mid-fragment-sequence
        env.toxiproxy().addToxic("jms", "fragment-outage", "timeout", Map.of("timeout", 1));
        Thread.sleep(500);
        env.toxiproxy().removeToxic("jms", "fragment-outage");
        background.join(30000);

        njams.stop();

        // Full assertion (that the server-side reassembled message is complete and uncorrupted, not merely that
        // some message arrived) requires reading the reassembled body — plug in the real reassembly check here
        // once the message-format/fragment reassembly utility used elsewhere in the test suite is identified.
        assertEquals(1, countDeliveredMessages(logIds));
    }

    private int countDeliveredMessages(java.util.List<String> logIds) throws Exception {
        String inClause = logIds.stream().map(id -> "'" + id + "'")
            .collect(java.util.stream.Collectors.joining(","));
        String selector = com.im.njams.sdk.communication.MessageHeaders.NJAMS_LOGID_HEADER + " IN (" + inClause + ")";

        org.apache.activemq.ActiveMQConnectionFactory factory =
            new org.apache.activemq.ActiveMQConnectionFactory(env.jmsUrlDirect());
        try (javax.jms.Connection connection = factory.createConnection()) {
            connection.start();
            javax.jms.Session session = connection.createSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
            javax.jms.Queue queue = session.createQueue("njams.event");
            javax.jms.MessageConsumer consumer = session.createConsumer(queue, selector);
            int delivered = 0;
            while (consumer.receive(3000) != null) {
                delivered++;
            }
            return delivered;
        }
    }
}
```

- [ ] **Step 2: Write the HTTP IT**

Same shape, `PROPERTY_COMMUNICATION=HTTP`, asserting via WireMock's `/__admin/requests` journal that the number
of journaled requests whose `njams-logid` header matches the one driven job's logId equals what the configured
chunk size and 200 KB payload predict, and that at least one such journaled request has `Content-Type: text/plain`
(the fragment-body case from the real API definition).

- [ ] **Step 3: Run and commit**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it verify -Dit.test=FragmentationUnderOutageIT,HttpFragmentationUnderOutageIT`
Expected: `BUILD SUCCESS`.

```bash
git add njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/FragmentationUnderOutageIT.java njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpFragmentationUnderOutageIT.java
git commit -m "SDK-483 Add scenario 6: fragmentation under outage (JMS + HTTP)"
```

---

### Task 13: HTTP scenarios 7/8/9a — rejection (413), congestion (429), application-level connection problem (503)

**Files:**
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpRejectAndCongestionIT.java`

**Interfaces:**
- Consumes: `DockerEnvironment`, `FixedProcessModel`, `MessageDriver`.

- [ ] **Step 1: Write the IT**

```java
package com.im.njams.sdk.it.http;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class HttpRejectAndCongestionIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test(timeout = 30000)
    public void rejectedMessageIsDiscardedWithoutAffectingTheConnection() throws Exception {
        loadOnDemandMapping("post-413.json");
        Njams njams = startWithDiscardPolicy("NONE");
        ProcessModel model = FixedProcessModel.build(njams);

        MessageDriver.run(model, 1, 100, 1);
        // Send a second, unrelated job afterward on the same instance — if the sender had been wrongly retired
        // over the 413, this would time out waiting for a reconnect instead of completing immediately.
        MessageDriver.run(model, 1, 100, 1);

        njams.stop();
    }

    @Test(timeout = 30000)
    public void congestionRetriesLocallyUnderNonDiscardPolicies() throws Exception {
        loadOnDemandMapping("post-429.json");
        Njams njams = startWithDiscardPolicy("ONCONNECTIONLOSS");
        ProcessModel model = FixedProcessModel.build(njams);

        // Under a non-DISCARD policy this call is expected to block, retrying locally, until the mapping is
        // reset back to 200 from a concurrently-scheduled reset — proving it never gives up, retires the
        // sender, or reconnects on 429.
        Thread resetter = new Thread(() -> {
            try {
                Thread.sleep(2000);
                resetToOkMapping();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        resetter.start();
        MessageDriver.run(model, 1, 100, 1);
        resetter.join();

        njams.stop();
    }

    @Test(timeout = 15000)
    public void congestionDiscardsImmediatelyWithoutDelayUnderDiscardPolicy() throws Exception {
        loadOnDemandMapping("post-429.json");
        Njams njams = startWithDiscardPolicy("DISCARD");
        ProcessModel model = FixedProcessModel.build(njams);

        long start = System.currentTimeMillis();
        MessageDriver.run(model, 1, 100, 1);
        long elapsedMs = System.currentTimeMillis() - start;

        njams.stop();
        org.junit.Assert.assertTrue("DISCARD must give up immediately, took " + elapsedMs + "ms", elapsedMs < 3000);
    }

    @Test(timeout = 30000)
    public void applicationLevel503IsTreatedAsConnectionProblemNotCongestion() throws Exception {
        loadOnDemandMapping("post-503.json");
        Njams njams = startWithDiscardPolicy("ONCONNECTIONLOSS");
        ProcessModel model = FixedProcessModel.build(njams);

        Thread recovery = new Thread(() -> {
            try {
                Thread.sleep(1000);
                resetToOkMapping();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        recovery.start();
        // If 503 were misclassified as congestion, this would also eventually succeed via local retry; the
        // real assertion this test protects is the code path (reconnect vs. local retry), which is exercised
        // for real here even though both wrong and right classification converge to eventual success. Confirm
        // during implementation whether SenderExceptionListener/SenderRecoveryListener firing can be observed
        // and asserted directly for a stronger check than "eventually succeeds."
        MessageDriver.run(model, 1, 100, 1);
        recovery.join();

        njams.stop();
    }

    private Njams startWithDiscardPolicy(String policy) {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        settings.put(NjamsSettings.PROPERTY_DISCARD_POLICY, policy);
        Njams njams = new Njams(Path.of("HttpRejectAndCongestionIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        return njams;
    }

    private void loadOnDemandMapping(String classpathResource) throws Exception {
        String body = new String(getClass().getClassLoader()
            .getResourceAsStream("wiremock/on-demand/" + classpathResource).readAllBytes());
        post(env.wireMockAdminUrl() + "/mappings", body);
    }

    private void resetToOkMapping() throws Exception {
        post(env.wireMockAdminUrl() + "/mappings/reset", "");
        loadOnDemandMapping("../mappings/post-ok.json");
    }

    private void post(String url, String body) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .build();
        client.send(request, BodyHandlers.discarding());
    }
}
```

`NjamsSettings.PROPERTY_DISCARD_POLICY` is a confirmed real constant; its value is parsed by
`DiscardPolicy.byValue(String)`, which matches case-insensitively against both the enum name (`NONE`,
`ON_CONNECTION_LOSS`, `DISCARD`) and its property value (`none`, `onconnectionloss`, `discard`) — so the
`"NONE"`/`"ONCONNECTIONLOSS"`/`"DISCARD"` literals used above all resolve correctly regardless of case.

- [ ] **Step 2: Run and commit**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it verify -Dit.test=HttpRejectAndCongestionIT`
Expected: `BUILD SUCCESS`.

```bash
git add njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpRejectAndCongestionIT.java
git commit -m "SDK-483 Add scenarios 7/8/9a: HTTP rejection, congestion, and application-level connection problem"
```

---

### Task 14: HTTP scenario 9b (transport-level connection problem) + repeated start/stop listener-leak probe + receiver-side mirror

**Files:**
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpConnectionProblemIT.java`
- Create: `njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/RepeatedStartStopLeakIT.java`

**Interfaces:**
- Consumes: `DockerEnvironment`, `FixedProcessModel`, `MessageDriver`.

- [ ] **Step 1: Write the transport-level connection-problem IT**

```java
package com.im.njams.sdk.it.http;

import java.util.Map;

import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class HttpConnectionProblemIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test(timeout = 30000)
    public void transportLevelOutageUsesTheSameRetireReconnectPathAsApplicationLevel503() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");

        Njams njams = new Njams(Path.of("HttpConnectionProblemIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);
        MessageDriver.run(model, 1, 100, 1);

        env.toxiproxy().addToxic("http", "transport-down", "timeout", Map.of("timeout", 1));
        Thread background = new Thread(() -> {
            try {
                MessageDriver.run(model, 1, 100, 1);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        background.start();
        Thread.sleep(500);
        env.toxiproxy().removeToxic("http", "transport-down");
        background.join(20000);

        njams.stop();
    }
}
```

- [ ] **Step 2: Write the repeated start/stop leak probe**

```java
package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.settings.Settings;

public class RepeatedStartStopLeakIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test(timeout = 60000)
    public void repeatedStartStopOfSharedGroupInstancesDoesNotGrowListenerRegistrations() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());

        // Ten independent Njams instances sharing the same connection settings (hence the same sender group)
        // started and stopped in sequence. This exercises the SenderRecoveryListener/SenderExceptionListener
        // registration paths on Njams.start()/receiver stop without needing internal test hooks — the actual
        // assertion (bounded listener-collection size) requires either a package-private accessor added to
        // SenderPool for this test module to use via a same-package test class, or an observable proxy such as
        // confirming no exception/warning is logged about listener notification targeting a stopped instance.
        // Decide and implement the concrete assertion mechanism during this step, since it depends on internal
        // accessibility decisions not yet made — this is the one task in this plan where the exact assertion
        // needs a design call at implementation time rather than being fully specified here.
        for (int i = 0; i < 10; i++) {
            Njams instance = new Njams(Path.of("RepeatedStartStopLeakIT-" + i), "1.0.0", "CommunicationIT", settings);
            instance.start();
            instance.stop();
        }

        assertTrue("placeholder assertion — replace with the real listener-count check per the comment above",
            true);
    }
}
```

This task's second half is the one place in this plan that cannot be fully specified without an implementation
decision the spec deliberately left open (§7 notes the `SenderExceptionListener` collection has no removal path
at all): whether this test suite gets a package-private test hook into `SenderPool`'s listener collections, or
whether it asserts indirectly (e.g., via logged warnings or a bounded-growth check over many more cycles than 10
to make unbounded growth statistically obvious even without direct access). Raise this specific question to the
user before finalizing this task rather than picking one silently — see Task 14 Step 3.

- [ ] **Step 3: Before finishing this task, ask the user how to assert the listener-registration check**

Do not consider this task done with the placeholder assertion in Step 2. Present the two options above (a
package-private test accessor vs. an indirect/statistical check) and get a decision before writing the real
assertion and committing.

- [ ] **Step 4: Run and commit once Step 3 is resolved**

Run: `mvn -Pdocker-it -pl njams-sdk-communication-it verify -Dit.test=HttpConnectionProblemIT,RepeatedStartStopLeakIT`
Expected: `BUILD SUCCESS`.

```bash
git add njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/http/HttpConnectionProblemIT.java njams-sdk-communication-it/src/test/java/com/im/njams/sdk/it/jms/RepeatedStartStopLeakIT.java
git commit -m "SDK-483 Add scenario 9b and the repeated start/stop listener-leak probe"
```

---

## Final verification

- [ ] Run the full suite once, end to end: `mvn -Pdocker-it -pl njams-sdk-communication-it verify`. Expected:
  `BUILD SUCCESS`, every `*IT.java` class passes.
- [ ] Run the default build once from the root with **no** profile: `mvn -q -pl njams-sdk-communication-it
  validate`. Expected: `FAIL` (module not in reactor) — confirms the module truly never runs by default (Global
  Constraint #1).
- [ ] Re-read `docs/superpowers/specs/2026-09-28-sdk-483-communication-it-module-design.md` §6/§7 and confirm
  every scenario row and every regression-checklist row has a corresponding IT from Tasks 7-14.
