# Advanced Adoption Guide
## Scaling the Decision Trace SDK in Production Systems

The Decision Trace SDK is intentionally lightweight.

You can start with a single script and grow into a distributed system without changing the mental model. The same primitives that work in local development extend naturally into production environments.

This guide focuses on:

- scaling patterns
- production best practices
- OpenTelemetry integration
- governance-friendly design
- collector architecture
- organizational rollout

The goal is simple:

adopt once, scale without rewriting.

---

## A stable mental model at any scale

Whether you run one service or hundreds, the core idea stays the same:

a decision is structured reasoning captured as telemetry

That means Decision Trace behaves like:

- logs
- traces
- metrics

It is an observability signal — not application control flow.

This design allows you to scale safely without coupling decision recording to business logic.

Your systems keep running even if telemetry pipelines are slow or temporarily unavailable.

---

## Architecture overview

A common production layout looks like:

Applications / Agents / Services
        ↓
Decision Trace SDK
        ↓
Exporter buffer
        ↓
Collector layer
        ↓
Storage + analytics

The SDK focuses on capture.

Collectors focus on ingestion.

Storage focuses on analysis.

Each layer scales independently.

This separation is what keeps adoption simple.

---

## Async export as the default production pattern

In production, exporters should run asynchronously.

Instead of blocking application threads:

app → background queue → exporter → collector

This ensures:

- decision capture never delays requests
- network jitter doesn’t impact latency
- collectors can scale independently
- applications remain responsive

This mirrors how modern telemetry systems operate.

Decisions are treated as a structured observability stream.

---

## Graceful degradation

Decision Trace is designed to degrade gracefully.

If exporters fail:

- events can buffer
- retries can occur
- low-priority drops are acceptable

Business logic should never depend on successful export.

This makes adoption safe in high-availability systems.

You get reasoning telemetry without introducing operational risk.

---

## Versioned decision contracts

As systems evolve, decision schemas evolve too.

Versioning keeps analytics stable:

refund.approve.v1  
refund.approve.v2

This allows:

- reproducible historical analysis
- safe rollout of new logic
- compatibility across services
- long-term governance

Treat decision types like APIs: explicit, versioned, intentional.

This is a foundation for scaling across teams.

---

## Redaction and sensitive data handling

Decision evidence should focus on reasoning, not raw secrets.

In production environments, establish patterns like:

- hashing identifiers
- storing derived attributes
- masking personal data
- tenant isolation rules

Examples:

Instead of:

email = user@example.com

Prefer:

email_domain = example.com

This preserves analytical value while protecting sensitive information.

Decision Trace captures why, not private content.

---

## Integrating with OpenTelemetry

Decision Trace pairs naturally with OpenTelemetry.

They describe different dimensions of the same system:

- OpenTelemetry → execution flow
- Decision Trace → reasoning flow

Together they form a complete picture.

---

### Linking decisions to active traces

Attach decision IDs to spans:

```python
from opentelemetry import trace

span = trace.get_current_span()

with decision(...) as d:
    span.set_attribute("decision.trace_id", d._trace_id)
    span.set_attribute("decision.id", d._decision_id)
```

This enables seamless pivoting between:

request trace ↔ decision graph

Execution and reasoning stay connected.

---

### Emitting decision summaries as span events

You can emit lightweight decision summaries into traces:

```python
span.add_event(
    "decision",
    {
        "decision.type": d.decision_type,
        "decision.outcome": d.outcome_value,
    }
)
```

Tracing tools show quick context.

Decision Trace stores full reasoning detail.

Fast view + deep replay.

---

### Unified correlation

Best practice is shared identifiers across systems:

- request ID
- trace ID
- decision trace ID

When aligned, engineers can move instantly between:

logs → traces → decisions → analytics

Decision Trace becomes another first-class signal.

---

## Scaling collectors

Collectors scale like any telemetry ingestion system.

A typical production setup uses:

- stateless collector replicas
- load balancing
- queue-backed pipelines
- durable storage

Example:

SDK exporters
      ↓
Load balancer
      ↓
Collector cluster
      ↓
Streaming queue
      ↓
Storage + analytics

Collectors remain simple.

Scaling happens horizontally.

No SDK changes required.

---

## Storage considerations

Decision graphs are structured, time-aware data.

Good storage systems support:

- time slicing
- graph traversal
- aggregation
- lineage reconstruction

Common patterns include:

- document stores
- graph databases
- analytics warehouses
- hybrid pipelines

The SDK stays storage-agnostic.

You choose based on your analytics needs.

---

## Governance-friendly deployment

Decision data is valuable organizational memory.

Enterprises often layer:

- access control
- tenant separation
- retention policies
- audit logging
- export boundaries

These features sit above the SDK and collector layer.

They grow naturally with adoption.

Nothing special is required in application code.

---

## Rolling out across teams

Adoption works best when teams see immediate benefit.

A practical rollout approach:

1. instrument one workflow
2. visualize the decision graph
3. debug a real incident faster
4. share the success
5. expand organically

Engineers adopt tools that make their work easier.

Decision Trace should feel like a superpower, not a mandate.

---

## Production readiness checklist

Before scaling widely:

- async exporters enabled
- graceful degradation tested
- schema versioning conventions defined
- redaction patterns documented
- collectors horizontally scalable
- retention strategy chosen
- OpenTelemetry correlation wired
- dashboards in place

This ensures smooth growth without surprises.

---

## A system that remembers reasoning

Over time, decision graphs become:

- an audit trail
- an analytics layer
- a debugging tool
- an explainability system
- institutional memory

You are preserving reasoning.

That’s what allows systems to scale responsibly.

---

## Where to go next

Advanced directions include:

- decision replay systems
- reasoning analytics
- anomaly detection
- policy drift monitoring
- evaluation pipelines
- governance dashboards

The SDK stays simple.

The ecosystem grows around it.
