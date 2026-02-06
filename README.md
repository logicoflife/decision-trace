# Decision Trace

**Decision Trace** is a semantic tracing framework for AI agents and automated decision systems. It provides an append-only, immutable record of *why* a system did what it did, structured as a causal graph of decisions.

> **Status**: v1.0 (Beta)

## Features

- **Semantic Events**: Specific event types for `evidence`, `policy_check`, `outcome`, etc.
- **Causal Graph**: Auto-links decisions into a DAG (Decision A -> caused -> Decision B).
- **Zero-Infrastructure**: Defaults to local `jsonl` files. No database required.
- **CLI Inspector**: Visualize traces in the terminal with `decision-trace inspect`.
- **Contract Linting**: Enforce organization-wide schemas with `contract.yaml`.
- **OpenTelemetry Bridge**: Seamlessly export decision traces as OTel spans.

## Installation

```bash
pip install decision-trace
```

## Quickstart

```python
from decision_trace import decision

# Record a decision
with decision(
    decision_type="loan.origination.v1",
    actor={"type": "system", "id": "risk-engine"},
    tenant_id="acme",
    environment="prod",
) as ctx:
    ctx.evidence("credit_score", 720)
    ctx.policy_check("min_score_600", "pass")
    ctx.outcome("approved")

print(f"Recorded Trace ID: {ctx.trace_id}")
```

This writes to `./decision-trace.jsonl` by default.

## CLI Usage

### Inspect a Trace
Visualize the decision tree in your terminal:

```bash
decision-trace inspect <trace_id> --verbose
```

### Build an Index
For large files, create a SQLite index for O(1) lookups:

```bash
decision-trace index -f data/events.jsonl
```

### Lint Contracts
Ensure your application emits valid event types:

```bash
decision-trace lint-contract contract.yaml
```

**Example `contract.yaml`**:
```yaml
decisions:
  - type: loan.origination.v1
actors:
  - type: system
```

## Comparisons

| Feature | Conventional Logging | File-Based Tracing | Decision Trace |
| :--- | :--- | :--- | :--- |
| **Structure** | Unstructured text | Spans (Timing focus) | Semantic Graph (Logic focus) |
| **Linking** | Manual correlation IDs | Parent/Child Spans | Explicit Causal Links |
| **Schema** | None / Loose | OTel SemConv | Enforced Event Types |
| **Use Case** | Debugging errors | Performance / Latency | Audit / Safety / Explainability |

## OpenTelemetry Integration

Bridge Decision Trace events to your observability stack (Jaeger, Honeycomb, etc.):

```python
from opentelemetry import trace
from decision_trace.exporters.otel import OTelExporter

# Use your existing OTel tracer
exporter = OTelExporter(tracer=trace.get_tracer(__name__))

with decision(..., exporter=exporter) as ctx:
    ctx.outcome("approved")
```

## Contributing

1. Install dev dependencies: `pip install -e ".[test,collector]"`
2. Run tests: `pytest`
