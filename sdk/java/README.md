# Decision Trace Java SDK

The Java SDK currently consists of a framework-agnostic core runtime, a Spring Boot starter, and a test-driven sample application under [`sdk/java/decision-trace-samples`](./decision-trace-samples).

This README is the Java entry point. Use it to choose the right path:

- For a first end-to-end run, start with [`docs/java-quickstart.md`](../../docs/java-quickstart.md).
- For service adoption guidance, use [`docs/java-enterprise-guide.md`](../../docs/java-enterprise-guide.md).
- For runtime internals, see [`docs/java-runtime-architecture.md`](../../docs/java-runtime-architecture.md).

## Module Map

- `decision-trace-core`
  - Artifact: `io.github.logicoflife.decisiontrace:decision-trace-core`
  - Purpose: canonical event model, `DecisionScope`, emitters, exporters, runtime metrics
- `decision-trace-spring-boot-starter`
  - Artifact: `io.github.logicoflife.decisiontrace:decision-trace-spring-boot-starter`
  - Purpose: Spring Boot auto-configuration, `@Decision`, `DecisionContext`, HTTP propagation, Micrometer metrics
- `decision-trace-samples`
  - Artifact: `io.github.logicoflife.decisiontrace:decision-trace-samples`
  - Purpose: auth/risk/passkey sample used by the integration tests
- `decision-trace-benchmarks`
  - Artifact: `io.github.logicoflife.decisiontrace:decision-trace-benchmarks`
  - Purpose: JMH benchmarks for runtime overhead
- `decision-trace-bom`
  - Artifact: `io.github.logicoflife.decisiontrace:decision-trace-bom`
  - Purpose: version alignment for the published Java modules

## What Is Implemented

### Core runtime

Implemented in [`sdk/java/decision-trace-core`](./decision-trace-core):

- Canonical event type: `io.decisiontrace.core.model.DecisionTraceEvent`
- Lifecycle helper: `io.decisiontrace.core.DecisionScope`
- Emitters:
  - `io.decisiontrace.core.emitter.InMemoryDecisionEmitter`
  - `io.decisiontrace.core.emitter.LmaxDecisionEmitter`
- Exporters:
  - `io.decisiontrace.core.exporter.http.CollectorBatchExporter`
  - `io.decisiontrace.core.exporter.json.JsonLedgerExporter`
  - `io.decisiontrace.core.exporter.otel.OpenTelemetryDecisionExporter`
  - `io.decisiontrace.core.exporter.InMemoryDecisionExporter`

### Spring Boot integration

Implemented in [`sdk/java/decision-trace-spring-boot-starter`](./decision-trace-spring-boot-starter):

- `@Decision` aspect around annotated methods
- `DecisionContext` APIs for:
  - `evidence`
  - `policyCheck`
  - `action`
  - `approval`
  - `evaluation`
- Inbound servlet propagation through `DecisionTraceHandlerInterceptor`
- Outbound propagation for `RestTemplate` and `WebClient`
- Micrometer metrics backed by `DecisionRuntimeMetrics`
- Property binding through `decision-trace.*`

### Sample application

Implemented in [`sdk/java/decision-trace-samples`](./decision-trace-samples):

- Spring Boot sample app class: `io.decisiontrace.samples.GoldenFlowApplication`
- Sample services:
  - `io.decisiontrace.samples.service.AuthService`
  - `io.decisiontrace.samples.service.RiskService`
  - `io.decisiontrace.samples.service.PasskeyService`
- End-to-end sample test:
  - `io.decisiontrace.samples.GoldenFlowIntegrationTest`

The current sample is best exercised through tests. `GoldenFlowApplication` is annotated with `@SpringBootApplication`, but there is no `main` method in the module today.

## Quickstart Path

Use the existing sample integration test as the first runnable path:

```bash
cd sdk/java
mvn test -pl decision-trace-samples -Dtest=GoldenFlowIntegrationTest -am
```

That path exercises:

- `@Decision` entry points
- `DecisionContext` lifecycle events
- nested manual scopes in `RiskService`
- `RestTemplate` header propagation between sample endpoints
- exporter fan-out to in-memory, JSON ledger, and OpenTelemetry test exporters

Quickstart details live in [`docs/java-quickstart.md`](../../docs/java-quickstart.md).

## Production Adoption Path

For real services, depend on the Spring Boot starter and configure one or more exporters through `decision-trace.*` properties.

Starter dependency:

```xml
<dependency>
    <groupId>io.github.logicoflife.decisiontrace</groupId>
    <artifactId>decision-trace-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Minimal production-style configuration:

```yaml
decision-trace:
  tenant-id: tenant-a
  environment: prod
  actor-id: auth-service
  actor-type: SYSTEM
  collector-endpoint: http://collector:8080/v1/events
  collector-batch-size: 50
  collector-connect-timeout-millis: 1000
  collector-request-timeout-millis: 3000
  ring-buffer-size: 1024
  validation-enabled: true
```

Production guidance, constraints, and caveats live in [`docs/java-enterprise-guide.md`](../../docs/java-enterprise-guide.md).

## Build And Test Commands

From [`sdk/java`](/Users/shobha/projects/decision-trace/sdk/java):

```bash
mvn test
mvn test -pl decision-trace-core -Dtest=DecisionScopeTest,LmaxDecisionEmitterTest,CollectorBatchExporterTest -am
mvn test -pl decision-trace-spring-boot-starter -am
mvn test -pl decision-trace-samples -Dtest=GoldenFlowIntegrationTest -am
mvn -pl decision-trace-benchmarks -am -DskipTests package
```

## Current Limits To Know Up Front

- The sample path is integration-test-driven, not a standalone `main`-class demo.
- The BOM module is present but not populated with dependency management yet.
- The collector exporter supports endpoint and timeout configuration, but no built-in retry, auth header, or TLS customization properties.
- The JSON ledger exporter is file-based and truncates the target file when `JsonLedgerExporter` starts.
- OpenTelemetry export is a projection of canonical events, not the source of truth.

## Related Docs

- [`decision-trace-core/README.md`](./decision-trace-core/README.md)
- [`decision-trace-spring-boot-starter/README.md`](./decision-trace-spring-boot-starter/README.md)
- [`decision-trace-samples/README.md`](./decision-trace-samples/README.md)
- [`docs/java-quickstart.md`](../../docs/java-quickstart.md)
- [`docs/java-enterprise-guide.md`](../../docs/java-enterprise-guide.md)
- [`docs/java-runtime-architecture.md`](../../docs/java-runtime-architecture.md)
- [`docs/java-golden-flow-walkthrough.md`](../../docs/java-golden-flow-walkthrough.md)
- [`docs/otel-mapping-notes.md`](../../docs/otel-mapping-notes.md)
