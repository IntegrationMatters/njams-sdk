# SonarCloud Findings — Decision Register

Persistent record of how SonarCloud findings for njams-sdk were triaged, so that **findings we deliberately
rejected as false positives are not re-litigated** every time Sonar re-analyses the code. When a rule below is
flagged again on the same construct, apply the recorded decision (re-mark it *False Positive* / *Won't Fix* in
SonarCloud with the same rationale) instead of "fixing" correct code.

**Always re-evaluate in context first** (per `.claude/rules/sonarcloud-workflow.md`): a decision here applies only
while the underlying reason still holds. If the code changed so the rationale no longer applies, treat it as a fresh
finding.

Project coordinates, auth and the query recipe are in `.claude/rules/sonarcloud-workflow.md` and the
`njams-sonarcloud-triage` skill. The GitHub build workflow analyses every branch under its own name, so query with
`&branch=<current git branch>`.

Entries follow `.claude/skills/njams-sonarcloud-triage/reference/decision-entry-template.md`.

---

## Rejected as false positive (re-apply on recurrence)

### `java:S2095` — Resources should be closed (×5, SDK-500)
**Verdict: false positive.** The flagged JMS consumers/producers are not local resources: they are stored in
fields and closed by the owning sender's/receiver's close path, and a failure between creation and field
assignment closes the session/connection, which closes all its consumers/producers per the JMS spec. The Kafka
(de)serializers are handed to `KafkaConsumer`/`KafkaProducer`, which take ownership and close them (also when
their constructor fails); their `close()` is a no-op anyway.
- `communication/jms/JmsReceiver.createConsumer` (consumer → field `consumer`, closed in `closeAll()`, called
  from `stop()`, `onException` and the connect-failure catch).
- `communication/jms/JmsSender.createProducer` (→ `eventProducer`/`projectProducer`, closed in `close()`;
  connect-failure catch closes session/connection).
- The javax copies (`communication/jms/javax/JmsReceiver`, `…/javax/JmsSender`) have the identical construct and
  take the same verdict if flagged.
- `communication/kafka/CommandsConsumer` (`new StringDeserializer()` into `new KafkaConsumer<>(…)`),
  `communication/kafka/KafkaReceiver` (`new StringSerializer()` into `new KafkaProducer<>(…)`).

### `java:S2755` — XML parsers should not be vulnerable to XXE (×1, SDK-500)
**Verdict: false positive (no untrusted input).** The default diagram path only serializes a DOM the SDK builds
in code — nothing is parsed. The only parsed input is the XSLT the client application itself passes to the
public `withXslt(...)`, which is trusted. The fix Sonar expects (`ACCESS_EXTERNAL_DTD`/`ACCESS_EXTERNAL_STYLESHEET`
attributes) throws `IllegalArgumentException` on the bundled Saxon-B 9.1, and blocking external access via a
resolver would break client stylesheets using `xsl:include`/`xsl:import`/`document()`.
- `model/svg/NjamsProcessDiagramFactory.createTransformerFactory` (Saxon `TransformerFactory`, secure processing
  enabled unless `njams.sdk.disable.secure.processing=true`).

### `java:S5527` — Server hostnames should be verified during SSL/TLS connections (×1, SDK-500)
**Verdict: false positive (documented opt-in).** Hostname verification is disabled only when the user explicitly
sets `njams.sdk.communication.http.ssl.unsafe.disableHostnameVerification=true` (default `false`, documented in the
FAQ as reducing security, logs a warning). Otherwise OkHttp's default verifier applies. Removing the option
would drop a public setting.
- `communication/http/HttpClientFactory` (the `PROPERTY_HTTP_DISABLE_HOSTNAME_VERIFICATION` branch). The sibling
  opt-in `PROPERTY_HTTP_TRUST_ALL_CERTIFICATES` trust manager takes the same verdict if flagged (e.g. `java:S4830`).

### `java:S2175` — Inappropriate "Collection" calls (×1, SDK-500)
**Verdict: false positive.** `CommunicationFactory.removeNjamsFromSharedReceiver` calls
`sharedReceivers.remove(receiver.getClass(), receiver)` with a `ShareableReceiver<?>`, which is statically unrelated
to the map's `AbstractReceiver` value type. At runtime every caller (`Njams`) passes an instance it has checked
with `instanceof AbstractReceiver`, and every implementation extends `AbstractReceiver`, so the removal matches the
cached instance.
- `communication/CommunicationFactory` (`sharedReceivers.remove(...)`).

### `java:S2445` — Blocks should be synchronized on "private final" fields (×1, SDK-500)
**Verdict: false positive.** `ServiceLoaderSupport.copyOf(Set)` locks its parameter, but the only arguments are
the class's own `final` `Collections.synchronizedSet` fields `unavailable`/`notApplicable`; locking the wrapper's
monitor is the documented way to iterate a synchronized set.
- `utils/ServiceLoaderSupport.copyOf`.

### `java:S8745` — Only one method should be annotated @BeforeClass (×1, SDK-500)
**Verdict: false positive.** JUnit 4 runs both `@BeforeClass` methods; they initialize independent static fields,
so no setup is skipped.
- Test `communication/NjamsSenderTest` (`createSettings`, `createDiscardPolicySettings`).

### `java:S3077` — Non-primitive fields should not be "volatile" (×13, SDK-500)
**Verdict: false positive.** Every flagged field uses `volatile` only for safe publication: the reference is
replaced as a whole (copy-on-write, double-checked init or test hook) and the referenced object is immutable,
never mutated after publication, or itself thread-safe. `AtomicReference` would add an indirection without a
correctness gain.
- `DataMasker.mask` (`char[]` filled before publication, never written element-wise — `AtomicReferenceArray`
  would be wrong), `DataMasker.patterns` (unmodifiable list).
- `communication/AbstractReceiver.startupConnectThread`, `.reconnectThread` (`Thread`).
- `communication/AbstractSender.failureSink` (stateless method reference).
- `communication/CommunicationFactory.resolvedSenderClass` (`Class`).
- `communication/DiscardMonitor.instance`, `communication/ThrottleMonitor.instance` (test-hook singletons).
- `communication/SenderConnector.startupError` (only null-checked).
- `communication/jms/AbstractJmsReceiver.messageFilter` (fresh immutable predicate per update; **protected
  field** — its type must not change).
- `configuration/Configuration.processFilter` (`ProcessFilter` immutable after construction).
- `model/svg/NjamsProcessDiagramFactory.postProcessXsltTemplates` (`Templates` is thread-safe).
- `utils/ReflectionCache.ClassEntry.publicInterfaces` (fully built before publication).

## Won't fix (re-apply on recurrence)

_No entries yet._
