# decision-trace-spring-boot-starter

Spring Boot adoption surface for the Java SDK.

For end-to-end setup and production guidance, use:

- [`sdk/java/README.md`](../README.md)
- [`docs/java-quickstart.md`](../../../docs/java-quickstart.md)
- [`docs/java-enterprise-guide.md`](../../../docs/java-enterprise-guide.md)

## Artifact

```xml
<dependency>
    <groupId>io.github.logicoflife.decisiontrace</groupId>
    <artifactId>decision-trace-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Main Types

- `io.decisiontrace.spring.annotation.Decision`
- `io.decisiontrace.spring.context.DecisionContext`
- `io.decisiontrace.spring.autoconfigure.DecisionTraceAutoConfiguration`
- `io.decisiontrace.spring.config.DecisionTraceProperties`

## What This Module Adds

- `@Decision` aspect around annotated Spring methods
- `DecisionContext` lifecycle APIs inside active decision scopes
- inbound servlet propagation via `DecisionTraceHandlerInterceptor`
- outbound propagation for `RestTemplate` and `WebClient`
- Micrometer metrics backed by `DecisionRuntimeMetrics`
- optional exporter auto-configuration for:
  - collector export
  - JSON ledger export
  - OpenTelemetry projection export

## Auto-Configuration Entry

Spring Boot registration lives in:

- [`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`](./src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)

It imports:

- `io.decisiontrace.spring.autoconfigure.DecisionTraceAutoConfiguration`

## Config Surface

All configuration keys are declared in:

- [`DecisionTraceProperties.java`](./src/main/java/io/decisiontrace/spring/config/DecisionTraceProperties.java)

Key properties include:

- `decision-trace.tenant-id`
- `decision-trace.environment`
- `decision-trace.actor-id`
- `decision-trace.actor-type`
- `decision-trace.actor-version`
- `decision-trace.actor-org`
- `decision-trace.trace-id-header`
- `decision-trace.parent-decision-id-header`
- `decision-trace.ring-buffer-size`
- `decision-trace.validation-enabled`
- `decision-trace.collector-endpoint`
- `decision-trace.collector-batch-size`
- `decision-trace.collector-connect-timeout-millis`
- `decision-trace.collector-request-timeout-millis`
- `decision-trace.json-ledger-path`
- `decision-trace.otel-export-enabled`

Use the enterprise guide for the full property reference and behavior notes:

- [`docs/java-enterprise-guide.md`](../../../docs/java-enterprise-guide.md)

## Tests

Primary coverage for this module lives in:

- [`DecisionTraceSpringBootStarterTest.java`](./src/test/java/io/decisiontrace/spring/DecisionTraceSpringBootStarterTest.java)
- [`DecisionTraceRuntimeIntegrationTest.java`](./src/test/java/io/decisiontrace/spring/DecisionTraceRuntimeIntegrationTest.java)
- [`DecisionTraceExporterAutoConfigurationTest.java`](./src/test/java/io/decisiontrace/spring/DecisionTraceExporterAutoConfigurationTest.java)

Run only this module and its dependencies:

```bash
cd sdk/java
mvn test -pl decision-trace-spring-boot-starter -am
```
