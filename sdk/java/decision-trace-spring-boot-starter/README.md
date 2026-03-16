# decision-trace-spring-boot-starter

The Spring Boot starter is the adoption surface for production services.

## What It Provides

- `@Decision` aspect for decision entry points
- `DecisionContext` for lifecycle emission from annotated business methods
- inbound propagation from servlet requests
- outbound propagation for `RestTemplate` and `WebClient`
- auto-configured async runtime, metrics, and exporters

## Key Properties

```yaml
decision-trace:
  tenant-id: tenant-a
  environment: prod
  actor-id: auth-service
  actor-type: SYSTEM
  trace-id-header: X-Decision-Trace-Trace-Id
  parent-decision-id-header: X-Decision-Trace-Parent-Decision-Id
  ring-buffer-size: 1024
  validation-enabled: true
  collector-endpoint: http://collector:8080/v1/events
  collector-batch-size: 50
  collector-connect-timeout-millis: 1000
  collector-request-timeout-millis: 3000
  json-ledger-path: build/decision-trace.jsonl
  otel-export-enabled: true
```

## Programming Model

```java
@Decision(decisionType = "RISK_ORCHESTRATION", actorId = "risk-service")
public RiskResponse evaluate(RiskRequest request) {
    decisionContext.evidence("risk_request_ip", request.ipAddress());
    decisionContext.policyCheck("risk_entry_policy", "continue");
    decisionContext.action(Map.of("action", "PASSKEY"));
    decisionContext.evaluation(Map.of("decision", "ALLOW"));
    return new RiskResponse("ALLOW", "PASSKEY");
}
```

`DecisionContext` is active only within an annotated decision scope. Validation failures are fail-open by default: invalid lifecycle inputs are dropped and counted rather than breaking request handling.

## Exporter Composition

- set `decision-trace.collector-endpoint` for batched collector export
- set `decision-trace.json-ledger-path` only for local development or debugging
- provide an `OpenTelemetry` bean to enable OTel projection export

See [Java runtime architecture](../../../docs/java-runtime-architecture.md) for threading and failure-isolation details.
