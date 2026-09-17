# SDK-422 — Strip replay start-data from command replies

- **Jira:** SDK-422 (server counterpart: SER-6148)
- **Date:** 2026-06-18
- **Branch:** 6.0-dev

## Problem

When the SDK answers a server command, it serializes the **entire `Instruction`** back to the
server as the reply. The `Instruction` still holds the original `Request` together with all of its
parameters.

For a `REPLAY` command, the request carries the replay start-data in the request parameter
`Payload`. This payload can be large. Echoing it back in the reply doubles the data on the wire for
no benefit — the server already has the start-data from the inbound request and does not need it
returned.

### Original vs. re-scoped intent

The original ticket asked to remove the request from the reply entirely. That is infeasible: the
reply message *is* the request message, completed with reply content. Fully omitting the request
would require redesigning the command/reply format, which is out of scope.

The re-scoped goal: **clear the large start-data (`Payload`) from the replay request before the
reply is sent.** This ticket covers the replay `Payload` only — no generic large-parameter
mechanism.

## Current flow

1. A transport receiver parses the inbound `Instruction`.
2. `AbstractReceiver.onInstruction()` runs the registered instruction listeners synchronously.
3. For a replay command, dispatch reaches `NjamsCommands.dispatch()` →
   `NjamsReplay.handleReplayRequest(instruction)`.
4. `handleReplayRequest` builds a `ReplayRequest` (copying out the values it needs), invokes the
   `ReplayHandler`, and writes the `Response` onto the **same** `Instruction`.
5. The transport reply path (`HttpSseReceiver.sendReply`, and the JMS/Kafka equivalents) serializes
   that same `Instruction` — request parameters included — and sends it back.

Because all transports serialize the one in-place-mutated `Instruction`, the `Payload` is echoed
back on every transport.

## Solution

Strip the replay `Payload` request parameter in `NjamsReplay.handleReplayRequest`, after the handler
has consumed it. Since every transport serializes the same mutated `Instruction` for its reply,
clearing the parameter once here removes the start-data from the reply on **all** transports, with
no per-transport change and no wire-format change.

### Behaviour

- **Only the replay `Payload` parameter is removed.** All other replay parameters (`Process`,
  `Test`, `Deeptrace`) are small and remain in the reply.
- **Case-insensitive removal.** Request parameters are looked up case-insensitively
  (`Instruction.getRequestParameterByName` uses `equalsIgnoreCase`), so a plain
  `parameters.remove("Payload")` could miss a differently-cased key. Removal must drop any key that
  equals `Payload` ignoring case.
- **Always strip.** The removal runs in a `finally` block so the start-data is dropped whether the
  replay succeeds, throws, or no handler is registered. The reply never needs the start-data in any
  of these cases.

### The `Payload` constant

The parameter name is currently a private constant `PARAM_PAYLOAD` in `ReplayRequest`. To avoid
duplicating the literal, it will be shared (e.g. raised to a package-visible constant) and reused by
both `ReplayRequest` and the stripping logic.

### Sketch

```java
void handleReplayRequest(Instruction instruction) {
    try {
        if (replayHandler != null) {
            final ReplayRequest replayRequest = new ReplayRequest(instruction);
            final ReplayResponse replayResponse = replayHandler.replay(replayRequest);
            replayResponse.addParametersToInstruction(instruction);
            if (!replayRequest.getTest()) {
                jobs.setReplayMarker(replayResponse.getMainLogId(), replayRequest.getDeepTrace());
                LOG.debug("Processed replay response {}", replayResponse.getMainLogId());
            }
        } else {
            instruction.setResponseResultCode(1);
            instruction.setResponseResultMessage("No replay handler registered.");
        }
    } catch (final Exception ex) {
        instruction.setResponseResultCode(2);
        instruction.setResponseResultMessage("Error while executing replay: " + ex.getMessage());
        instruction.setResponseParameter("Exception", String.valueOf(ex));
    } finally {
        stripStartData(instruction); // remove the "Payload" request parameter, case-insensitive
    }
}
```

## Approaches considered

| | Approach | Verdict |
|---|---|---|
| **A** | Strip `Payload` in `NjamsReplay.handleReplayRequest` | **Chosen** — command-specific, transport-agnostic, minimal |
| B | Strip generically in `AbstractReceiver.onInstruction` after listeners | Rejected — no command knowledge there; risks dropping parameters the server still expects; broader than the ticket |
| C | Strip in each transport's reply method before serialize | Rejected — duplicated across HTTP/JMS/Kafka; the reply layer should not know replay semantics |

## Out of scope

- A generic "strip any large request parameter" mechanism.
- Any change to the command/reply wire format.
- Changes to non-replay commands.

## Testing

- Replay with a `Payload`: the reply `Instruction`'s request no longer contains `Payload`; the
  response (result code, message, `MainLogId`) is unchanged.
- Differently-cased payload key (e.g. `payload`) is also removed.
- Test replay (`Test=true`): `Payload` is stripped; response unchanged.
- Replay failure (handler throws): error response is set **and** `Payload` is stripped.
- No replay handler registered: result code 1 is set and the (already small) request is unaffected
  except for `Payload` removal being a no-op.
- Non-payload parameters (`Process`, `Test`, `Deeptrace`) survive in the reply.

## API / labelling impact

- **No public-API change.** This is communication-layer reply behaviour, not public API. It is
  coordinated with the server via SER-6148. No `breaking-change` label on SDK-422.
- The server side (SER-6148, updated) must not read the start-data back from the reply; it relies on
  the copy retained from the inbound request.
