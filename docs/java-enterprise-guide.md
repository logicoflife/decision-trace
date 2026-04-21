# Java Enterprise Guide

This guide documents the production-style adoption path that is actually implemented in the current Java SDK. It also calls out the parts that are not yet implemented so teams can plan accordingly.

## Recommended Adoption Path

Use the Spring Boot starter module:

- Module: [`sdk/java/decision-trace-spring-boot-starter`](../sdk/java/decision-trace-spring-boot-starter)
- Artifact: `io.github.logicoflife.decisiontrace:decision-trace-spring-boot-starter`

Use the core module directly only if you need manual runtime control outside Spring:

- Module: [`sdk/java/decision-trace-core`](../sdk/java/decision-trace-core)
- Artifact: `io.github.logicoflife.decisiontrace:decision-trace-core`

## What The Starter Auto-Configures

[`DecisionTraceAutoConfiguration`](../sdk/java/decision-trace-spring-boot-starter/src/main/java/io/decisiontrace/spring/autoconfigure/DecisionTraceAutoConfiguration.java) registers:

- `DecisionRuntimeMetrics`
- `DecisionDispatcher`
- `DecisionEmitter` as `LmaxDecisionEmitter`
- `DecisionContextHolder`
- `DecisionScopeHolder`
- `InboundTraceContext`
- `IdGenerator` as `UuidIdGenerator`
- `Clock` as `Clock.systemUTC()`
- `MeterRegistry`
  - default fallback is `SimpleMeterRegistry` if your app does not already provide one
- `DecisionTraceMetrics`
- `DecisionTraceAspect`
- `DecisionContext` as `DefaultDecisionContext`
- inbound servlet interceptor
- outbound `RestTemplate` customization
- outbound `WebClient` customization
- optional exporters depending on properties and beans

## Required Baseline Configuration

These properties are the practical minimum for a real service:

```yaml
decision-trace:
  tenant-id: tenant-a
  environment: prod
  actor-id: auth-service
  actor-type: SYSTEM
```

Property source: [`DecisionTraceProperties`](../sdk/java/decision-trace-spring-boot-starter/src/main/java/io/decisiontrace/spring/config/DecisionTraceProperties.java)

## Full Property Reference

All currently implemented configuration keys are:

```yaml
decision-trace:
  tenant-id: default-tenant
  environment: dev
  actor-id: decision-trace-java
  actor-type: SYSTEM
  actor-version:
  actor-org:
  trace-id-header: X-Decision-Trace-Trace-Id
  parent-decision-id-header: X-Decision-Trace-Parent-Decision-Id
  ring-buffer-size: 1024
  validation-enabled: false
  collector-endpoint:
  collector-batch-size: 50
  collector-connect-timeout-millis: 1000
  collector-request-timeout-millis: 3000
  json-ledger-path:
  otel-export-enabled: true
```

### What each key does

- `decision-trace.tenant-id`
  - added to every `DecisionTraceEvent`
- `decision-trace.environment`
  - added to every `DecisionTraceEvent`
- `decision-trace.actor-id`
  - default actor id for `@Decision` methods that do not set `actorId`
- `decision-trace.actor-type`
  - default actor type stored in the emitted `Actor`
- `decision-trace.actor-version`
  - optional actor metadata
- `decision-trace.actor-org`
  - optional actor metadata
- `decision-trace.trace-id-header`
  - inbound and outbound header name for trace id propagation
- `decision-trace.parent-decision-id-header`
  - inbound and outbound header name for parent decision propagation
- `decision-trace.ring-buffer-size`
  - size of the `LmaxDecisionEmitter` ring buffer
  - must be a power of two because `LmaxDecisionEmitter` enforces that constraint
- `decision-trace.validation-enabled`
  - enables bean validation inside `DefaultDecisionContext`
- `decision-trace.collector-endpoint`
  - enables collector exporter auto-configuration
- `decision-trace.collector-batch-size`
  - batch threshold for `CollectorBatchExporter`
- `decision-trace.collector-connect-timeout-millis`
  - `HttpClient` connect timeout
- `decision-trace.collector-request-timeout-millis`
  - request timeout for `HttpCollectorBatchSender`
- `decision-trace.json-ledger-path`
  - enables `JsonLedgerExporter`
- `decision-trace.otel-export-enabled`
  - controls OpenTelemetry exporter activation when an `OpenTelemetry` bean is present

## Service Instrumentation Pattern

The implemented starter pattern is:

1. Annotate a service method with `@Decision`.
2. Inject `DecisionContext`.
3. Emit lifecycle events inside the annotated method.
4. Let the aspect create and close the surrounding `DecisionScope`.

Example:

```java
package com.example.risk;

import io.decisiontrace.spring.annotation.Decision;
import io.decisiontrace.spring.context.DecisionContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
class RiskService {
    private final DecisionContext decisionContext;

    RiskService(DecisionContext decisionContext) {
        this.decisionContext = decisionContext;
    }

    @Decision(decisionType = "RISK_ORCHESTRATION", actorId = "risk-service")
    String evaluate(String userId, String ipAddress) {
        decisionContext.evidence("risk_request_ip", ipAddress);
        decisionContext.policyCheck("risk_entry_policy", "continue", Map.of("user_id", userId), null);
        decisionContext.evaluation(new LinkedHashMap<>(Map.of("decision", "ALLOW")));
        return "ALLOW";
    }
}
```

Implemented lifecycle methods in [`DecisionContext`](../sdk/java/decision-trace-spring-boot-starter/src/main/java/io/decisiontrace/spring/context/DecisionContext.java):

- `evidence(String key, Object value)`
- `evidence(String key, Object value, List<CausalLink> causalLinks)`
- `policyCheck(String policy, String result)`
- `policyCheck(String policy, String result, Map<String, Object> inputs, List<CausalLink> causalLinks)`
- `action(Map<String, Object> payload)`
- `approval(Map<String, Object> payload)`
- `evaluation(Map<String, Object> payload)`

## Manual Nested Decisions

Nested decisions are implemented today through the core API, not through a Spring abstraction. The sample demonstrates this in [`RiskService`](../sdk/java/decision-trace-samples/src/main/java/io/decisiontrace/samples/service/RiskService.java) by calling:

- `DecisionSpec.builder()`
- `DecisionScope.open(...)`

Use that pattern when a single annotated method needs child decisions with their own `decision_type`.

## Exporter Options

### 1. Collector exporter

Enabled when `decision-trace.collector-endpoint` is set.

Implemented classes:

- [`CollectorBatchExporter`](../sdk/java/decision-trace-core/src/main/java/io/decisiontrace/core/exporter/http/CollectorBatchExporter.java)
- [`HttpCollectorBatchSender`](../sdk/java/decision-trace-core/src/main/java/io/decisiontrace/core/exporter/http/HttpCollectorBatchSender.java)

Implemented behavior:

- batches canonical events into a JSON array payload
- sends `Content-Type: application/json`
- flushes when `collector-batch-size` is reached
- also flushes on emitter `flush()` and `close()`
- throws on non-2xx response
- leaves the batch pending if send fails so a later `flush()` can retry in-process

What is not implemented:

- retry policy configuration
- exponential backoff
- persistent local buffering
- authentication headers
- custom TLS configuration properties
- compression settings

### 2. JSON ledger exporter

Enabled when `decision-trace.json-ledger-path` is set.

Implemented class:

- [`JsonLedgerExporter`](../sdk/java/decision-trace-core/src/main/java/io/decisiontrace/core/exporter/json/JsonLedgerExporter.java)

Implemented behavior:

- writes one canonical event per line
- creates parent directories when needed
- truncates the file on startup because it opens with `StandardOpenOption.TRUNCATE_EXISTING`

Recommended use:

- local debugging
- short-lived verification environments

Not recommended as the primary production sink unless you intentionally want local file output and manage file shipping separately.

### 3. OpenTelemetry exporter

Enabled when both conditions are true:

- an `io.opentelemetry.api.OpenTelemetry` bean exists
- `decision-trace.otel-export-enabled=true`

Implemented class:

- [`OpenTelemetryDecisionExporter`](../sdk/java/decision-trace-core/src/main/java/io/decisiontrace/core/exporter/otel/OpenTelemetryDecisionExporter.java)

Implemented behavior:

- creates one `SpanKind.INTERNAL` span per canonical event
- stores canonical identifiers and metadata as `dt.*` attributes
- stores the serialized event as `dt.payload_json`
- marks `decision.error` events with `StatusCode.ERROR`

Important limitation:

- this exporter creates spans from canonical events, but it does not set an OpenTelemetry parent context from the active application span

## Propagation

### Inbound HTTP

Implemented for servlet-based Spring MVC through:

- [`DecisionTraceHandlerInterceptor`](../sdk/java/decision-trace-spring-boot-starter/src/main/java/io/decisiontrace/spring/http/DecisionTraceHandlerInterceptor.java)

Inbound behavior:

- reads `decision-trace.trace-id-header`
- reads `decision-trace.parent-decision-id-header`
- falls back to parsing the W3C `traceparent` header for a trace id

### Outbound HTTP

Implemented for:

- `RestTemplate` via [`DecisionTraceRestTemplateInterceptor`](../sdk/java/decision-trace-spring-boot-starter/src/main/java/io/decisiontrace/spring/http/DecisionTraceRestTemplateInterceptor.java)
- `WebClient` via [`DecisionTraceWebClientFilter`](../sdk/java/decision-trace-spring-boot-starter/src/main/java/io/decisiontrace/spring/http/DecisionTraceWebClientFilter.java)

Outbound behavior:

- writes the configured trace id header
- writes the configured parent decision id header

Current boundary:

- inbound server-side propagation is implemented for servlet applications
- there is no separate server-side WebFlux inbound integration class in the current starter

## Validation And Fail-Open Semantics

When `decision-trace.validation-enabled=true` and a `jakarta.validation.Validator` is available, [`DefaultDecisionContext`](../sdk/java/decision-trace-spring-boot-starter/src/main/java/io/decisiontrace/spring/context/DefaultDecisionContext.java) validates:

- evidence keys must be non-blank
- policy names must be non-blank
- policy results must be non-blank
- action, approval, and evaluation payloads must be non-empty maps

If validation fails:

- the event is dropped
- `decision_trace.instrumentation.failures` increments
- the business method continues

The same fail-open approach also applies to:

- exporter exceptions
- full ring buffer conditions
- instrumentation exceptions in the aspect

## Metrics

Implemented Micrometer meters:

- `decision_trace.instrumentation.failures`
- `decision_trace.buffer.occupancy`
- `decision_trace.events.dropped`
- `decision_trace.export.failures`
- `decision_trace.export.latency`

Implementation:

- [`DecisionTraceMetrics`](../sdk/java/decision-trace-spring-boot-starter/src/main/java/io/decisiontrace/spring/metrics/DecisionTraceMetrics.java)
- [`DecisionRuntimeMetrics`](../sdk/java/decision-trace-core/src/main/java/io/decisiontrace/core/runtime/DecisionRuntimeMetrics.java)

Recommended production practice:

- provide your normal application `MeterRegistry` bean so these meters land in your existing metrics pipeline

## Runtime Behavior Relevant To Production

`LmaxDecisionEmitter` uses the LMAX Disruptor and publishes to exporters off the business thread.

Implemented guarantees demonstrated by tests:

- slow exporters do not block the annotated business method after enqueue
- full buffers drop events instead of blocking
- exporter failures do not stop sibling exporters
- `flush()` and `close()` perform bounded draining

Primary test coverage:

- [`DecisionTraceRuntimeIntegrationTest`](../sdk/java/decision-trace-spring-boot-starter/src/test/java/io/decisiontrace/spring/DecisionTraceRuntimeIntegrationTest.java)
- [`LmaxDecisionEmitterTest`](../sdk/java/decision-trace-core/src/test/java/io/decisiontrace/core/runtime/LmaxDecisionEmitterTest.java)

## Recommended Production Baseline

This baseline stays within the currently implemented feature set:

```yaml
decision-trace:
  tenant-id: tenant-a
  environment: prod
  actor-id: auth-service
  actor-type: SYSTEM
  actor-version: 1.0.0
  actor-org: trust-platform
  trace-id-header: X-Decision-Trace-Trace-Id
  parent-decision-id-header: X-Decision-Trace-Parent-Decision-Id
  ring-buffer-size: 1024
  validation-enabled: true
  collector-endpoint: http://collector:8080/v1/events
  collector-batch-size: 50
  collector-connect-timeout-millis: 1000
  collector-request-timeout-millis: 3000
  otel-export-enabled: true
```

Recommended operational practices around that baseline:

- keep the collector exporter as the primary sink
- use the JSON ledger exporter only for targeted debugging
- keep `ring-buffer-size` as a power of two
- turn on validation in shared service code
- explicitly decide on your header names before cross-service rollout
- define evidence redaction rules in application code before emitting sensitive values

Recommended, but not implemented by this starter:

- collector auth and credential rotation
- collector retry/backoff policy
- durable local spillover
- automatic PII redaction
- remote configuration or sampling controls

## BOM Status

[`sdk/java/decision-trace-bom/pom.xml`](../sdk/java/decision-trace-bom/pom.xml) exists as a module, but it does not currently contribute dependency management entries.

Practical implication:

- use explicit versions for now
- do not document BOM import as a required path until the BOM is populated

## Suggested Rollout Sequence

1. Run [`GoldenFlowIntegrationTest`](../sdk/java/decision-trace-samples/src/test/java/io/decisiontrace/samples/GoldenFlowIntegrationTest.java) to understand the event model and propagation behavior.
2. Add `decision-trace-spring-boot-starter` to one internal Spring Boot service.
3. Instrument one or two high-value decision methods with `@Decision` and `DecisionContext`.
4. Enable collector export first.
5. Add OpenTelemetry projection only if your observability stack will consume the extra spans.
6. Add manual nested `DecisionScope` usage only where a single service method truly contains multiple business decisions.

## Related Docs

- [`sdk/java/README.md`](../sdk/java/README.md)
- [`docs/java-quickstart.md`](./java-quickstart.md)
- [`docs/java-runtime-architecture.md`](./java-runtime-architecture.md)
- [`docs/java-golden-flow-walkthrough.md`](./java-golden-flow-walkthrough.md)
