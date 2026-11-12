---
globs: "njams-sdk/src/main/java/**/*.java"
---

# API Design Principles

This SDK is a **public API** consumed by nJAMS client implementations. Client implementations must use SDK functionality whenever it is available rather than reimplementing it themselves.

## API Contracts

Not all `public` Java code in this project is public API, and the API that does exist isn't one flat surface — it splits into three contracts with different audiences, plus a genuine internal bucket:

**Client Contract.** What SDK users write against: the methods and types they call, and the effect that has server-side. This is the contract the rest of this document (Javadoc, immutability, deprecation) is written for by default.

**SPI Contract.** The `META-INF/services` extension surface for implementing new SDK behavior — most commonly a new transport. Registered SPI types (as of 6.1.0): `communication.AbstractSender`, `communication.AbstractReceiver` (registered as `communication.Receiver` until 6.1.0), `communication.jms.JmsFactory`, `communication.jms.factory.JmsFactory`, `configuration.ConfigurationProvider`. (`settings.SettingsProvider` was a registered SPI type until it was removed in 6.1.0.) The two `JmsFactory` interfaces are the two namespace halves of one extension point (SDK-495): `communication.jms.JmsFactory` is the current, `jakarta.jms` one; `communication.jms.factory.JmsFactory` is its deprecated `javax.jms` predecessor. **That predecessor can never be moved, renamed or folded into the other one** — third-party `javax` factories ship a `META-INF/services/com.im.njams.sdk.communication.jms.factory.JmsFactory` file, so the interface's fully-qualified name is itself part of the contract, and relocating it would make those factories silently disappear. This isn't a client-facing surface — it's how the SDK itself gets extended, so it carries less client impact than the Client Contract, but it's just as stable and just as much in need of confirmation before changing. The sender/receiver SPI redesign (SDK-375 and its follow-ups SDK-472, SDK-473, SDK-489) has landed in 6.1.0; the surface is stable from here on. SDK-495 added the jakarta `JmsFactory` and sharpened the sender/receiver contract — several implementations may now share one `getName()`, and one declines by throwing `TransportNotApplicableException` from `init` (see `communication-layer.md`) — without adding a method to or changing a signature on `AbstractSender`/`AbstractReceiver` themselves. That ticket's other public-API movements, including one source-breaking retype, are inventoried in its spec (§5.8) and judged against the `breaking-change` label on the real diff. The transport-related types (`AbstractSender`, `AbstractReceiver`, and both `JmsFactory` interfaces) have a narrower practical audience than the rest of the SPI Contract: implementing a new transport requires a coordinated counterpart change in nJAMS Server, so in practice only nJAMS developers — not general SDK client teams — implement these. They remain public API and are just as bound by the immutability rules; the closed audience affects who is expected to use them, not whether they must stay stable.

**Wire Contract.** The njams-messageformat data models, message fragmentation (`communication/fragments/`), and `MessageHeaders`/properties. This must stay stable against the version of nJAMS Server it implements — it's the fixed contract between client and server, not an internal implementation detail. It's fully transparent to Client Contract users (they never see it), but it's binding on SPI Contract implementers: it defines *what* a transport must send, while a new transport only gets to choose *how*. See `communication-layer.md` for where this contract lives inside `communication/`, and `message-format-changes.md` for the confirmation process that governs it — changes here go through that gate, not the `breaking-change` Jira label.

**Internal.** Code that is `public`/`protected` purely to satisfy internal cross-package access and touches none of the three contracts above. This is genuinely free to change.

**Practical implications:**
- Do not expose SPI Contract types, Wire Contract types, or transport details through the Client Contract surface.
- When assessing a `breaking-change` label: judge Client Contract and SPI Contract changes against it as usual (a broken SPI contract counts, a purely internal `public` member doesn't). Wire Contract changes are judged separately, through `message-format-changes.md`'s human-confirmation + `SER`-ticket gate — don't try to resolve them with the breaking-change label instead.
- Do not implement or change anything about the boundaries between these contracts without asking first.

## SPI Implementations Are Unknown

When implementing a feature or fix that affects a type created via SPI (the registered SPI types listed under "SPI Contract" above), always consider implementations the SDK does not know — custom providers, senders, receivers, factories written outside this repository. Do not only reason about the built-in implementations.

**Prefer a solution that does not require an SPI implementation to follow a contract or requirement** — such as "must call X after loading", "must override Y to make the feature work", or "must populate Z". Such requirements cannot be enforced, and a custom implementation that misses one fails silently. Put the behavior where the SDK controls it instead: in the SDK-side caller, in the shared base class (e.g. `AbstractConfigurationProvider`), or in a `default` interface method with a sensible fallback. Adding a `default` interface method is non-breaking, but it still extends the SPI, so ask first (see "Do not implement or change anything about the boundaries between these contracts without asking first" above).

Example (SDK-367): settings-based process-exclude patterns must reach every `Configuration`, whatever provider created it. Requiring each `ConfigurationProvider` to call `Configuration.initFilter(...)` was rejected. Instead, `AbstractConfigurationProvider` compiles the patterns once in `configure()`, and `Configuration` pulls them from its provider via a `default` method on the interface.

If no solution without a requirement is feasible, document the requirement on the SPI interface's Javadoc and raise it with the user.

## Relocated (Shaded) Third-Party Dependencies

The shaded SDK artifact relocates several third-party libraries into the `com.im.*` namespace at packaging time via the Maven Shade plugin (e.g. `com.fasterxml` → `com.im.fasterxml`, `net.sf.saxon` → `com.im.saxon`, and likewise Woodstox, jmespath, oshi, and others — see the `<relocations>` in the build).

**Hard constraint: types or functionality from a relocated third-party API must never appear on the public API surface and must not become available to SDK users.** This means:

- No `public` or `protected` method signature, return type, parameter type, public field, type parameter, thrown exception, or annotation may reference a type that gets relocated during packaging.
- Do not expose such types indirectly either (e.g. returning a collection of them, or a public type that extends/implements one).

The reason is twofold: after relocation these types no longer exist under their original coordinates, so a consumer cannot name them — and even if they could, it would leak an internal packaging detail and couple clients to the SDK's bundled, relocated copy. Keep relocated third-party types strictly internal (use `private`/package-private, wrap or adapt them behind SDK-owned types, and convert to/from SDK or JDK types at the boundary).

**When adding or modifying any `public` or `protected` member, always verify its full signature (return type, parameter types, type parameters, thrown exceptions, annotations) against the relocated package list in `checkstyle.xml`. Flag any violation immediately — do not proceed with code that exposes a relocated type.**

## Visibility and Scoping

Access control is critical. Users of the SDK will use everything that is accessible, so anything not intended for external use must be actively hidden:

- **Prefer the most restrictive scope possible.** Use `private` or package-private (no modifier) for internal implementation details. Reserve `public` and `protected` for intentional API surface.
- **Use interfaces to hide implementations.** When a type is part of the public API but its implementation should not be, expose an interface (or abstract class) and keep the concrete class package-private or internal. `Job`, `Activity`, and `Group` are examples of this pattern — they are interfaces rather than exposing their implementation classes directly.
- **Be deliberate about `protected`.** Protected methods are also API surface — subclasses in external code can call them. Only use `protected` when subclassing is an intentional extension point.
- **New public members are permanent commitments.** Adding a `public` method or class is easy; removing or changing it is a breaking change for all client implementations. Introduce new public API with care.

When adding or modifying functionality, always ask: should external code be able to see and call this? If not, restrict the scope or introduce an interface boundary.

## Implementing New Functionality

When implementing new functionality, keep all implementation details private by default and only expose what is explicitly intended as public API. If it is not obvious what the public API surface should be, **ask before planning or writing any code**. See `development-workflow-skills.md`.

## Javadoc Requirement

**All `public` and `protected` members must have Javadoc.** What Javadoc and code comments say, how deprecation is
documented on overrides, and how Javadoc is verified is governed by `documentation.md`.

## Immutability of Existing Public API

**All existing `public` and `protected` API must be treated as in active use by external client implementations and must not be changed unless explicitly requested.** This is a hard rule, not a guideline. Changing a method signature, return type, behavior, or removing a member is a breaking change. If you identify a problem with an existing public API, raise it with the user rather than fixing it silently.

**Extending the public API with new methods, classes, or overloads is permitted without explicit request.** Adding is safe; changing or removing is not.

**When changing existing public API is explicitly requested**, do not remove the old API. Instead, keep it in place, mark it `@Deprecated`, and add a Javadoc `@deprecated` tag that references the new replacement:

```java
/**
 * @deprecated Use {@link #newMethod()} instead.
 */
@Deprecated
public void oldMethod() {
    return newMethod(); // delegate to new implementation where possible
}
```

The deprecated member is still existing code being modified — test coverage must be established for it before making any changes, exactly as for any other code change. See `development-workflow-skills.md` for the `njams-safe-modification` skill this requires.

**This deprecate-and-delegate pattern does not work for SPI Contract members the SDK itself calls into** — template methods such as `AbstractSender.doReconnect`, which the SDK invokes on the implementer's behalf rather than the implementer calling it. Marking one `@Deprecated` and delegating to a replacement doesn't help: the SDK simply stops calling the old override, so its behavior silently disappears instead of continuing to work as a deprecated path. If a change like this is requested, raise the problem with the user instead of applying the normal pattern — it needs a different migration strategy.
