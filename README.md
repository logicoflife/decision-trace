
# Decision Trace SDK (Python)

**Open Standard for Decision Telemetry.**

Decision Trace is an append-only ledger for recording the *reasoning* of systems. It captures decisions, evidence, policy checks, and outcomes in a structured, queryable graph.

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Safety](https://img.shields.io/badge/safety-hardened-green.svg)](docs/safety.md)

---

## ⚡️ 5-Minute Quickstart

### 1. Install
```bash
pip install decision-trace[collector]
```

### 2. Start Local Dev Collector
```bash
decision-trace dev
```

### 3. Run a Trace
```python
from decision_trace import decision

with decision("loan.approval", actor={"type": "system", "id": "risk_engine"}) as d:
    d.evidence("credit_score", 720)
    d.policy_check("min_score_700", "pass")
    d.outcome("approved")
```

### 4. Inspect
```bash
decision-trace inspect --last --verbose
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

### Examples

- **[Refund Workflow](examples/refund_workflow/)**: Classify → Approve → Execute DAG.
- **[Agent Chain](examples/agent_approval_chain/)**: AI Proposal → Human Review.
- **[Policy Failure](examples/policy_failure_and_evaluation/)**: Recording denials.

---

## 🤝 Contributing, Attribution & License

### Attribution

If you build on this framework or create a derivative project, we ask that you credit the original repository. Attribution helps sustain open-source work and signals respect for shared infrastructure.

### Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

### License

Licensed under MIT.
