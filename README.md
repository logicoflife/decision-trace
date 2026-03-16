
# Decision Trace™

**Open Standard for Decision Telemetry.**

Decision Trace is an append-only ledger for recording the *reasoning* of systems. It captures decisions, evidence, policy checks, and outcomes in a structured, queryable graph.

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Safety](https://img.shields.io/badge/safety-hardened-green.svg)](docs/safety.md)

---

## ⚡️ 5-Minute Quickstart

### Python SDK
```bash
pip install decision-trace[collector]
decision-trace dev
```

```python
from decision_trace import decision

with decision("loan.approval", actor={"type": "system", "id": "risk_engine"}) as d:
    d.evidence("credit_score", 720)
    d.policy_check("min_score_700", "pass")
    d.outcome("approved")
```

### Java SDK
Add the Spring Boot starter and point it at a collector:

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
  environment: prod
  actor-id: auth-service
  collector-endpoint: http://collector:8080/v1/events
```

```java
@Decision(decisionType = "AUTH_SERVICE_LOGIN", actorId = "auth-service")
AuthResponse login(LoginRequest request) {
    decisionContext.evidence("user_id", request.userId());
    decisionContext.policyCheck("risk_entry_policy", "continue");
    decisionContext.evaluation(Map.of("decision", "ALLOW"));
    return new AuthResponse("ALLOW");
}
```

---

## 🧠 What is a Decision?

A **Decision** is a unit of reasoning. Unlike a log (what happened) or a trace (how long), a Decision captures **why**. It is a is a semantic unit of business logic.

It captures:
1.  **Context**: Who is acting? (Human, Agent, System)
2.  **Evidence**: What data was used?
3.  **Logic**: What policies were checked?
4.  **Outcome**: What was the result?

Decisions exist in AI agents, but also in traditional APIs, services,
risk engines, approval systems, and compliance pipelines.

See [**Core Concepts**](docs/concepts.md) for more.

---

## 🎯 When to Use Decision Trace

Use Decision Trace when you need to answer:
- *Why did the system invoke this tool?*
- *Which policy blocked this user?*
- *Did a human review this approval?*

It is designed for:
- **Agent Governance**: tracking tool usage and reasoning.
- **Human-in-the-Loop**: linking AI proposals to human approvals.
- **Policy Enforcement**: proving compliance with business rules.

---

## 🏗 Architecture Principles

- **Append-Only Ledger**: History is immutable.
- **Schema-First**: All events are validated against a strict schema.
- **Failure Isolation**: Tracing failures never crash your app.

See [**Architecture**](docs/architecture.md) for deep dive.

---

## 📚 Documentation

- [**Core Concepts**](docs/concepts.md) - Logs vs Decisions, The DAG.
- [**Architecture**](docs/architecture.md) - Ledger, Collector, Principles.
- [**Safety & Privacy**](docs/safety.md) - Evidence integrity, PII redaction.
- [**Migration Guide**](docs/migration.md) - Integrating with existing logging.
- [**Java SDK Overview**](sdk/java/README.md) - Spring Boot quickstart and module map.
- [**Java Runtime Architecture**](docs/java-runtime-architecture.md) - Async runtime and fail-open guarantees.
- [**Java Golden Flow Walkthrough**](docs/java-golden-flow-walkthrough.md) - Auth/risk/passkey sample flow.
- [**OTel Mapping Notes**](docs/otel-mapping-notes.md) - Canonical event to span projection.

### Examples

- **[Refund Workflow](sdk/python/examples/refund_workflow/)**: Classify → Approve → Execute DAG.
- **[Agent Chain](sdk/python/examples/agent_approval_chain/)**: AI Proposal → Human Review.
- **[Policy Failure](sdk/python/examples/policy_failure_and_evaluation/)**: Recording denials.
- **[Java Golden Flow](sdk/java/decision-trace-samples/)**: Spring Boot auth/risk/passkey DAG.

---

## 🤝 Contributing, Attribution & License

### Attribution

If you build on this framework or create a derivative project, we ask that you credit the original repository. Attribution helps sustain open-source work and signals respect for shared infrastructure.

### Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

### License

Licensed under MIT.

---
### Trademarks
Decision Trace™ is a common law trademark of Shobha Sethuraman / logicoflife. 

Initial Development: February 5, 2026

Public Release: February 7, 2026

Whitepaper Publication: February 9, 2026
