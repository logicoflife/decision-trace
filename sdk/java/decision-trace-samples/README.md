# decision-trace-samples

This module demonstrates the intended Spring Boot adoption pattern.

## Flow

- `AuthService` starts `AUTH_SERVICE_LOGIN`
- `RiskService` emits evidence and policy checks for `RISK_ORCHESTRATION`
- nested manual scopes record `DEVICE_TRUST_CHECK`, `VELOCITY_CHECK`, and `FINAL_RISK_DECISION`
- `PasskeyService` emits approval telemetry for `PASSKEY_SERVICE_VERIFY`

The sample exports the same canonical events to three destinations at once:

- in-memory recording exporter for assertions
- JSON ledger exporter for ledger projection checks
- OpenTelemetry exporter for span projection checks

## Running

```bash
mvn test -pl decision-trace-samples -am
```

The main integration coverage is in `GoldenFlowIntegrationTest`, which boots the sample app on a random local port and verifies:

- cross-service causal propagation
- nested DAG shape
- lifecycle events across services
- ledger and OTel projection consistency

For a step-by-step walkthrough of the emitted decisions, see [docs/java-golden-flow-walkthrough.md](../../../docs/java-golden-flow-walkthrough.md).
