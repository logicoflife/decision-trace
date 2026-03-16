# decision-trace-core

`decision-trace-core` contains the runtime that must remain framework-agnostic.

## Responsibilities

- canonical `DecisionTraceEvent` model
- `DecisionScope` lifecycle helpers
- `DecisionEmitter` abstraction with async `LmaxDecisionEmitter`
- exporter fan-out and runtime metrics
- JSON ledger, collector batch, and OpenTelemetry exporters

## Invariants

- business request threads only enqueue telemetry; exporter work happens off-thread
- LMAX Disruptor remains behind the `DecisionEmitter` abstraction
- OpenTelemetry is a projection layer, not the canonical event model
- exporter failures are isolated and do not throw back into business code after enqueue

## Typical Usage

Use this module directly when you need manual control without Spring:

```java
DecisionDispatcher dispatcher = new DecisionDispatcher(List.of(exporter), new DecisionRuntimeMetrics());
DecisionEmitter emitter = new LmaxDecisionEmitter(1024, dispatcher, new DecisionRuntimeMetrics());

try (DecisionScope scope = DecisionScope.open(spec, emitter, holder, idGenerator, Clock.systemUTC())) {
    scope.evidence("device_id", "device-7");
    scope.policyCheck("risk_v1", "pass", Map.of("score", 0.08), null);
    scope.outcome("ALLOW");
}
```

## Tests

- unit and stress coverage live under `src/test/java`
- `HttpCollectorBatchSenderIntegrationTest` verifies the real HTTP exporter path using a local collector-style endpoint
