# Java Runtime Architecture

The Java SDK is built around one rule: decision telemetry must never block the business request thread.

## Runtime Shape

1. Application code enters a decision scope through `@Decision` or `DecisionScope.open(...)`.
2. Lifecycle APIs construct canonical `DecisionTraceEvent` objects on the caller thread.
3. `DecisionEmitter` enqueues events to the LMAX-backed runtime.
4. `DecisionDispatcher` fans events out to exporters on the disruptor consumer thread.
5. Exporters project the same canonical event to collector, JSON ledger, and OpenTelemetry outputs.

## Guarantees

- Request threads do not flush on scope close.
- Exporter latency is isolated from business latency after enqueue.
- Event drops due to saturation are counted in runtime metrics.
- Exporter failures are recorded and isolated; they do not crash the service.
- Spring-specific concerns remain outside `decision-trace-core`.

## Fail-Open Model

Fail-open is deliberate. The SDK prefers incomplete telemetry over interfering with business traffic.

- full ring buffer: event is dropped and `decision_trace.events.dropped` increases
- exporter throws: failure is recorded and sibling exporters still run
- validation failure in `DecisionContext`: invalid event is not enqueued, request continues
- shutdown: emitter flushes bounded in time, then closes exporters

## Metrics

The starter publishes Micrometer metrics backed by runtime state:

- `decision_trace.buffer.occupancy`
- `decision_trace.events.dropped`
- `decision_trace.export.latency`
- `decision_trace.export.failures`

## Propagation

Inbound servlet requests and outbound `RestTemplate` / `WebClient` calls propagate:

- `X-Decision-Trace-Trace-Id`
- `X-Decision-Trace-Parent-Decision-Id`

These names are configurable via `DecisionTraceProperties`.
