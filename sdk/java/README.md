# Decision Trace Java SDK

The Java SDK is intended for Spring Boot service adoption where decision telemetry must stay off the business request path.

## Modules

- `decision-trace-core`: canonical event model, decision scopes, async emitter, exporters
- `decision-trace-spring-boot-starter`: Spring Boot auto-configuration, `@Decision`, propagation, `DecisionContext`
- `decision-trace-samples`: runnable auth/risk/passkey example and end-to-end integration test
- `decision-trace-benchmarks`: JMH benchmark suite for runtime overhead tracking

## Quickstart

Use the starter in a Spring Boot service:

```xml
<dependency>
    <groupId>io.decisiontrace</groupId>
    <artifactId>decision-trace-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Configure the service:

```yaml
decision-trace:
  tenant-id: tenant-a
  environment: prod
  actor-id: auth-service
  actor-type: SYSTEM
  collector-endpoint: http://collector:8080/v1/events
  collector-batch-size: 50
  ring-buffer-size: 1024
  validation-enabled: true
```

Annotate decision entry points and emit lifecycle events from business code:

```java
@Service
class AuthService {
    private final DecisionContext decisionContext;

    AuthService(DecisionContext decisionContext) {
        this.decisionContext = decisionContext;
    }

    @Decision(decisionType = "AUTH_SERVICE_LOGIN", actorId = "auth-service")
    AuthResponse login(LoginRequest request) {
        decisionContext.evidence("user_id", request.userId());
        decisionContext.policyCheck("risk_entry_policy", "continue");
        decisionContext.action(Map.of("action", "STEP_UP"));
        decisionContext.evaluation(Map.of("decision", "ALLOW"));
        return new AuthResponse("ALLOW");
    }
}
```

## Developer Workflow

- Run all Java tests: `mvn test`
- Run sample integration tests only: `mvn test -pl decision-trace-samples -am`
- Run JMH benchmarks: `mvn -pl decision-trace-benchmarks -am -DskipTests package`

## Additional Docs

- [Core module](./decision-trace-core/README.md)
- [Spring Boot starter](./decision-trace-spring-boot-starter/README.md)
- [Samples](./decision-trace-samples/README.md)
- [Java runtime architecture](../../docs/java-runtime-architecture.md)
- [Golden auth/risk/passkey walkthrough](../../docs/java-golden-flow-walkthrough.md)
- [OTel mapping notes](../../docs/otel-mapping-notes.md)
