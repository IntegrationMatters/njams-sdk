# nJAMS SDK

The nJAMS SDK is a Java library for building nJAMS clients. It instruments your application to track process execution and stream monitoring data — process model definitions and job execution records — to an nJAMS Server. The server can also push commands back to the client (replay, instrumentation changes) through the same connection.

**Requirements:** Java 11+, Maven 3.8+

## Key concepts

| Concept | Description |
|---------|-------------|
| `Njams` | Main entry point — manages lifecycle, process registry, sender threads, and server command handling |
| `ProcessModel` | Static definition of a process (activities + transitions); factory for `Job` instances |
| `ActivityModel` / `TransitionModel` | Individual nodes and edges in a process definition |
| `Job` | Runtime execution record for one run of a process |
| `Activity` / `Group` | Execution state for a single step or a structured group of steps |
| `ClientSettings` | Key/value configuration view passed to the `Njams` constructor |
| `NjamsSettings` | Constants for all configuration property keys |

## Further reading

- **[FAQ](FAQ)** — settings reference, all transport configurations, flush tuning, data masking, Argos metrics, custom layouters
- **[CommonBfsModelLayouter Algorithm](CommonBfsModelLayouter-Algorithm)** — how activity coordinates are calculated for process diagrams
- **[Poly-line process-diagram routing](Polyline-Process-Diagram-Routing)** — opt-in alternative that routes transitions around activity icons
- **[JavaDoc](https://integrationmatters.github.io/njams-sdk/index-njams-sdk.html)** — full API reference
- **[Sample client](https://github.com/IntegrationMatters/njams-sdk/tree/master/njams-sdk-sample-client/src/main/java/com/faizsiegeln/test)** — runnable usage examples
- **[Full settings file](https://github.com/IntegrationMatters/njams-sdk/blob/master/njams-sdk-sample-client/src/main/resources/settings_full.properties)** — annotated reference for every available property
