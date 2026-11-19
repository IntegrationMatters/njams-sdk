# Command Replies

Tickets: SDK-422

How the SDK answers a command from nJAMS Server, and what the reply must not carry.

## Design

The reply to a command is the inbound `Instruction` itself, completed with a `Response`. There is no separate reply
object:

1. A receiver parses the inbound `Instruction` and calls `AbstractReceiver.onInstruction(Instruction)`, which runs
   every `InstructionListener` in `NjamsCommands.list()` synchronously on that one instance. If no listener set a
   response, it sets a default one (result code 1).
2. The SDK's built-in commands are handled by `NjamsCommands.dispatch(Instruction)`, registered as a listener at
   `Njams.start()`. It writes its result onto the same instance (`setResponseResultCode`, `setResponseParameter`,
   `setResponse`).
3. The receiver then serializes that same instance — request parameters included — as the reply:
   `HttpSseReceiver.sendReply`, `JmsReceiver.reply` / `SharedJmsReceiver.sendReply` (both JMS namespace trees), and
   the Kafka receivers. `SharedReceiverSupport.onInstruction` follows the same pattern for shared receivers.

Consequence: anything left in the request is echoed back to the server on every transport. Changing what a reply
contains is therefore done once, on the `Instruction`, in the command's handler — never in a transport's reply path.

## Replay start-data is removed from the reply

A `REPLAY` request carries the replay start-data in the request parameter `Payload`
(`ReplayRequest.PARAM_PAYLOAD`), which can be large. The server keeps its own copy from the inbound request and does
not read it back from the reply (server counterpart SER-6148), so echoing it would only double the data on the wire.

`NjamsReplay.handleReplayRequest(Instruction)` removes it in a `finally` block via `removeStartData`, after the
`ReplayHandler` has run. Invariants:

1. **Always removed.** Success, a throwing handler (result code 2) and no registered handler (result code 1) all end
   with `Payload` gone.
2. **Case-insensitive.** Every key equal to `Payload` ignoring case is removed, matching
   `Instruction.getRequestParameterByName`, which looks parameters up with `equalsIgnoreCase`. A plain
   `remove("Payload")` would miss a differently-cased key.
3. **Only `Payload`.** All other request parameters (`Process`, `processPath`, `Test`, `Deeptrace`, …) stay in the
   reply; the response written by `ReplayResponse.addParametersToInstruction` is unaffected.
4. **The handler still sees the start-data.** `ReplayRequest` copies `Payload` and the full parameter map out of the
   `Instruction` in its constructor, before removal.
5. **One name, one constant.** `ReplayRequest.PARAM_PAYLOAD` (public) is used both to read the parameter and to
   remove it; do not repeat the literal.

No wire-format change: the reply keeps its structure, only one parameter value is missing.

## Rejected alternatives

1. **Omit the request from the reply entirely** — the reply *is* the request plus a response; dropping it would mean
   redesigning the command/reply format.
2. **Strip generically in `AbstractReceiver.onInstruction` after the listeners** — that layer has no command
   knowledge and could drop parameters the server still expects.
3. **Strip in each transport's reply method** — duplicates the logic across HTTP/JMS/Kafka, and the reply layer must
   not know command semantics.

## Classes

- `com.im.njams.sdk.NjamsReplay` — replay handling and start-data removal
- `com.im.njams.sdk.NjamsCommands` — built-in command dispatch, listener registry
- `com.im.njams.sdk.communication.AbstractReceiver` — listener invocation, default response
- `com.im.njams.sdk.communication.ReplayRequest` / `ReplayResponse` — request parameter names, response parameters
- Reply paths: `communication.http.HttpSseReceiver`, `communication.jms.JmsReceiver`,
  `communication.jms.SharedJmsReceiver` (and their `communication.jms.javax` copies),
  `communication.SharedReceiverSupport`
