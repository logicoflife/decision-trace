# Java Quickstart

This quickstart is grounded in the existing sample under [`sdk/java/decision-trace-samples`](../sdk/java/decision-trace-samples). It does not invent a new demo path.

## What You Will Run

The current Java example is the integration test [`sdk/java/decision-trace-samples/src/test/java/io/decisiontrace/samples/GoldenFlowIntegrationTest.java`](../sdk/java/decision-trace-samples/src/test/java/io/decisiontrace/samples/GoldenFlowIntegrationTest.java).

That test boots the sample Spring Boot application [`sdk/java/decision-trace-samples/src/main/java/io/decisiontrace/samples/GoldenFlowApplication.java`](../sdk/java/decision-trace-samples/src/main/java/io/decisiontrace/samples/GoldenFlowApplication.java), sends a real `POST /auth/login` request, then verifies the emitted decision graph and exporter outputs.

## Prerequisites

- Java 17
- Maven 3.9+
- A local environment that permits binding an HTTP port for Spring Boot tests

The parent build in [`sdk/java/pom.xml`](../sdk/java/pom.xml) sets:

- `maven.compiler.source=17`
- `maven.compiler.target=17`

## Run The Sample

From the repository root:

```bash
cd sdk/java
mvn test -pl decision-trace-samples -Dtest=GoldenFlowIntegrationTest -am
```

If you want the whole Java reactor instead:

```bash
cd sdk/java
mvn test
```

## What The Sample Contains

### Spring Boot modules involved

- [`sdk/java/decision-trace-samples/pom.xml`](../sdk/java/decision-trace-samples/pom.xml)
  - depends on `io.decisiontrace:decision-trace-core`
  - depends on `io.decisiontrace:decision-trace-spring-boot-starter`
  - uses `org.springframework.boot:spring-boot-starter-web`

### Application flow

- [`AuthController`](../sdk/java/decision-trace-samples/src/main/java/io/decisiontrace/samples/api/AuthController.java)
  - `POST /auth/login`
- [`AuthService.login`](../sdk/java/decision-trace-samples/src/main/java/io/decisiontrace/samples/service/AuthService.java)
  - annotated with `@Decision(decisionType = "AUTH_SERVICE_LOGIN", actorId = "auth-service")`
  - records `user_id` and `device_id` evidence
  - calls `/risk/evaluate` through `RestTemplate`
- [`RiskService.evaluate`](../sdk/java/decision-trace-samples/src/main/java/io/decisiontrace/samples/service/RiskService.java)
  - annotated with `@Decision(decisionType = "RISK_ORCHESTRATION", actorId = "risk-service")`
  - records evidence and a policy check through `DecisionContext`
  - creates nested manual `DecisionScope` instances for:
    - `DEVICE_TRUST_CHECK`
    - `VELOCITY_CHECK`
    - `FINAL_RISK_DECISION`
  - calls `/passkey/verify`
- [`PasskeyService.verify`](../sdk/java/decision-trace-samples/src/main/java/io/decisiontrace/samples/service/PasskeyService.java)
  - annotated with `@Decision(decisionType = "PASSKEY_SERVICE_VERIFY", actorId = "passkey-service")`
  - records an approval event

## Configuration Used By The Sample

The sample test sets these exact properties in `@SpringBootTest`:

```properties
decision-trace.tenant-id=tenant-a
decision-trace.environment=test
```

Those values are declared in [`GoldenFlowIntegrationTest`](../sdk/java/decision-trace-samples/src/test/java/io/decisiontrace/samples/GoldenFlowIntegrationTest.java).

The starter supplies the remaining defaults through [`DecisionTraceProperties`](../sdk/java/decision-trace-spring-boot-starter/src/main/java/io/decisiontrace/spring/config/DecisionTraceProperties.java):

```yaml
decision-trace:
  tenant-id: default-tenant
  environment: dev
  actor-id: decision-trace-java
  actor-type: SYSTEM
  actor-version: null
  actor-org: null
  trace-id-header: X-Decision-Trace-Trace-Id
  parent-decision-id-header: X-Decision-Trace-Parent-Decision-Id
  ring-buffer-size: 1024
  validation-enabled: false
  collector-endpoint: null
  collector-batch-size: 50
  collector-connect-timeout-millis: 1000
  collector-request-timeout-millis: 3000
  json-ledger-path: null
  otel-export-enabled: true
```

## Exporters Used By The Sample

The sample does not use property-driven exporter auto-configuration. Instead, [`SampleRuntimeConfiguration`](../sdk/java/decision-trace-samples/src/main/java/io/decisiontrace/samples/config/SampleRuntimeConfiguration.java) overrides the runtime and wires a primary `DecisionEmitter` with three exporters:

- `io.decisiontrace.samples.telemetry.RecordingDecisionExporter`
- `io.decisiontrace.samples.telemetry.ResettableJsonLedgerExporter`
- `io.decisiontrace.core.exporter.otel.OpenTelemetryDecisionExporter`

Important distinction:

- Implemented in the sample:
  - exporter fan-out is assembled manually in `SampleRuntimeConfiguration`
- Implemented in the starter:
  - collector, JSON ledger, and OpenTelemetry exporters can be auto-configured from properties and beans

## What To Look For In The Test

[`GoldenFlowIntegrationTest`](../sdk/java/decision-trace-samples/src/test/java/io/decisiontrace/samples/GoldenFlowIntegrationTest.java) verifies:

- decision types:
  - `AUTH_SERVICE_LOGIN`
  - `RISK_ORCHESTRATION`
  - `DEVICE_TRUST_CHECK`
  - `VELOCITY_CHECK`
  - `FINAL_RISK_DECISION`
  - `PASSKEY_SERVICE_VERIFY`
- one shared `trace_id` across the whole flow
- correct `parent_decision_id` lineage
- consistency across:
  - canonical events
  - JSON ledger lines
  - OpenTelemetry span projection

## Minimal Starter Usage In Your Own Service

When you move from the sample to a real service, the shortest implemented path is the Spring Boot starter:

```xml
<dependency>
    <groupId>io.decisiontrace</groupId>
    <artifactId>decision-trace-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
decision-trace:
  tenant-id: tenant-a
  environment: dev
  actor-id: auth-service
```

```java
package com.example.auth;

import io.decisiontrace.spring.annotation.Decision;
import io.decisiontrace.spring.context.DecisionContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
class AuthService {
    private final DecisionContext decisionContext;

    AuthService(DecisionContext decisionContext) {
        this.decisionContext = decisionContext;
    }

    @Decision(decisionType = "AUTH_SERVICE_LOGIN", actorId = "auth-service")
    String login(String userId, String deviceId) {
        decisionContext.evidence("user_id", userId);
        decisionContext.evidence("device_id", deviceId);
        decisionContext.evaluation(new LinkedHashMap<>(Map.of("decision", "ALLOW")));
        return "ALLOW";
    }
}
```

That example uses only APIs implemented in:

- [`Decision`](../sdk/java/decision-trace-spring-boot-starter/src/main/java/io/decisiontrace/spring/annotation/Decision.java)
- [`DecisionContext`](../sdk/java/decision-trace-spring-boot-starter/src/main/java/io/decisiontrace/spring/context/DecisionContext.java)

## Current Constraints

- The sample application is not currently packaged with a `main` method.
- The quickstart path is therefore test-first, not `spring-boot:run` first.
- `RestTemplate` propagation is demonstrated in the sample; `WebClient` propagation is covered by starter tests, not by the sample app.
- Full collector export is implemented, but the sample test does not send to the collector service.

## Next Step

After the sample passes, continue with [`docs/java-enterprise-guide.md`](./java-enterprise-guide.md) to move from the sample runtime to property-driven service integration.
