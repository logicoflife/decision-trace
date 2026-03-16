# decision-trace-samples

Spring Boot sample module used as the current Java quickstart and end-to-end integration path.

For the guided first-run flow, use:

- [`sdk/java/README.md`](../README.md)
- [`docs/java-quickstart.md`](../../../docs/java-quickstart.md)
- [`docs/java-golden-flow-walkthrough.md`](../../../docs/java-golden-flow-walkthrough.md)

## Module Role

This module demonstrates:

- `@Decision` on service entry points
- `DecisionContext` lifecycle emission
- nested manual `DecisionScope` usage
- `RestTemplate` propagation between local endpoints
- exporter fan-out across multiple sinks

## Main Classes

- Application:
  - [`GoldenFlowApplication.java`](./src/main/java/io/decisiontrace/samples/GoldenFlowApplication.java)
- Services:
  - [`AuthService.java`](./src/main/java/io/decisiontrace/samples/service/AuthService.java)
  - [`RiskService.java`](./src/main/java/io/decisiontrace/samples/service/RiskService.java)
  - [`PasskeyService.java`](./src/main/java/io/decisiontrace/samples/service/PasskeyService.java)
- Runtime wiring:
  - [`SampleRuntimeConfiguration.java`](./src/main/java/io/decisiontrace/samples/config/SampleRuntimeConfiguration.java)
- Integration test:
  - [`GoldenFlowIntegrationTest.java`](./src/test/java/io/decisiontrace/samples/GoldenFlowIntegrationTest.java)

## Exporters Wired In The Sample

`SampleRuntimeConfiguration` assembles a primary `DecisionEmitter` with:

- `io.decisiontrace.samples.telemetry.RecordingDecisionExporter`
- `io.decisiontrace.samples.telemetry.ResettableJsonLedgerExporter`
- `io.decisiontrace.core.exporter.otel.OpenTelemetryDecisionExporter`

This is sample-specific wiring for test coverage. It is separate from the property-driven exporter auto-configuration in `decision-trace-spring-boot-starter`.

## Run

```bash
cd sdk/java
mvn test -pl decision-trace-samples -Dtest=GoldenFlowIntegrationTest -am
```

## What The Integration Test Verifies

- root and child decision lineage across:
  - `AUTH_SERVICE_LOGIN`
  - `RISK_ORCHESTRATION`
  - `DEVICE_TRUST_CHECK`
  - `VELOCITY_CHECK`
  - `FINAL_RISK_DECISION`
  - `PASSKEY_SERVICE_VERIFY`
- canonical event schema shape
- JSON ledger consistency
- OpenTelemetry projection consistency
- cross-service propagation using local HTTP calls

## Current Limitation

[`GoldenFlowApplication.java`](./src/main/java/io/decisiontrace/samples/GoldenFlowApplication.java) does not currently define a `main` method, so the sample is best treated as a test-driven demo rather than a standalone runnable app.
