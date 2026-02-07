
# 🚀 Your First Decision Graph  
*A hands-on tutorial with the Decision Trace SDK*

Modern systems don’t just produce outputs — they make decisions.

Refund approvals. Risk scoring. Feature flags. AI tool selection.  
Behind every outcome is a chain of reasoning.

Most systems throw that reasoning away.

The Decision Trace SDK is built around a simple idea:

> Decisions should be observable, inspectable, and composable.

In this tutorial, you’ll build your first decision graph — not just log events, but model real reasoning as a structured trace.

By the end, you’ll understand:

- how to represent decisions as graphs
- how to link decisions together
- how to capture evidence and outcomes
- how to inspect and debug decision flows

No prior knowledge of the SDK required.

---

## The problem: a simple refund system

Let’s start with a realistic scenario.

A refund pipeline has 3 steps:

1. classify → is this refund eligible?
2. approve → should we approve it?
3. execute → actually process the refund

Most systems log:

```
refund processed
```

That’s useless when debugging.

What we want is:

- why was it eligible?
- what evidence was used?
- which policy approved it?
- what decision led to execution?

We’re going to model this as a **decision graph**.

```
classify
   ↓
approve
   ↓
execute
```

Each step is a decision node.

Each node records:

- evidence
- reasoning
- outcome
- relationships to other decisions

That structure is the core of the SDK.

---

## Step 1 — Install and setup

Install the SDK:

```
pip install decision-trace
```

Start a local collector:

```
decision-trace collector
```

By default this runs at:

```
http://127.0.0.1:8711
```

Now we’re ready to emit decisions.

---

## Step 2 — Writing your first decision

Let’s model the classification step.

```python
from decision_trace import decision
from decision_trace.exporters.http import HttpExporter

exporter = HttpExporter("http://127.0.0.1:8711")

with decision(
    decision_type="refund.classify.v1",
    actor={"type": "agent", "id": "classifier"},
    tenant_id="demo",
    environment="dev",
    exporter=exporter,
    validate=True,
) as classify:

    classify.evidence("order_value", 120)
    classify.evidence("days_since_purchase", 3)

    classify.outcome("eligible")
```

Let’s break this down.

### decision_type

```
refund.classify.v1
```

This is your schema contract.

It defines what kind of decision this is.  
Versioning is built in — you can evolve logic safely over time.

---

### evidence

```
classify.evidence("order_value", 120)
```

Evidence is structured reasoning input.

Not logs. Not strings.  
Data that explains *why* the decision happened.

Think:

> inputs to reasoning

---

### outcome

```
classify.outcome("eligible")
```

This is the decision result.

Every decision ends with an explicit outcome.

No ambiguity. No guessing later.

---

## Step 3 — Linking decisions into a graph

Now we extend the pipeline.

We want approval to depend on classification.

The SDK supports this via:

- trace_id → ties decisions into one session
- parent_decision_id → links nodes

```python
with decision(
    decision_type="refund.approve.v1",
    actor={"type": "agent", "id": "approver"},
    tenant_id="demo",
    environment="dev",
    exporter=exporter,
    trace_id=classify._trace_id,
    parent_decision_id=classify._decision_id,
    validate=True,
) as approve:

    approve.evidence("policy_version", "2026-01")
    approve.outcome("approved")
```

This creates a parent-child relationship:

```
classify → approve
```

Now we add execution:

```python
with decision(
    decision_type="refund.execute.v1",
    actor={"type": "system", "id": "executor"},
    tenant_id="demo",
    environment="dev",
    exporter=exporter,
    trace_id=classify._trace_id,
    parent_decision_id=approve._decision_id,
    validate=True,
) as execute:

    execute.outcome("refund_sent")
```

We now have a full decision graph.

Not a log stream.

A reasoning structure.

---

## Step 4 — Inspecting the trace

Run:

```
decision-trace inspect
```

You’ll see a tree:

```
refund.classify.v1 → eligible
  └── refund.approve.v1 → approved
        └── refund.execute.v1 → refund_sent
```

Each node contains:

- evidence
- timestamps
- actor metadata
- versioned schema
- parent/child links

This is your reasoning audit trail.

---

## Step 5 — Why this is powerful

Once you have decision graphs, you unlock:

### Debugging

Why was a refund approved?

→ inspect evidence chain

---

### Analytics

Which policy versions approve more refunds?

→ aggregate decision outcomes

---

### AI explainability

Why did an agent choose this action?

→ decision tree replay

---

### Governance

What reasoning led to this financial action?

→ audit-ready decision lineage

---

## Step 6 — Common design patterns

Here are 3 patterns you’ll use often.

---

### Pattern 1: Policy gate

```python
with decision("policy.check.v1", exporter=exporter) as gate:
    gate.evidence("user_age", 17)
    gate.outcome("denied")
```

Use for rule enforcement.

---

### Pattern 2: Human approval

```python
with decision("human.review.v1", exporter=exporter) as review:
    review.evidence("ticket_id", "A123")
    review.outcome("approved")
```

Use for manual interventions.

---

### Pattern 3: AI tool chain

```python
with decision("ai.plan.v1", exporter=exporter) as plan:
    plan.outcome("use_refund_agent")
```

Use for LLM orchestration or agent routing.

---

## Step 7 — Production thinking

The SDK is designed for real systems.

When moving beyond local experiments:

- enable schema contracts
- redact sensitive evidence
- isolate decision failures from business logic
- deploy collectors behind auth
- version decision types intentionally

Treat decisions like APIs.

They evolve. They deserve contracts.

---

## Mental model recap

You are not logging.

You are modeling reasoning.

Each decision is:

- structured
- versioned
- linked
- inspectable
- composable

Over time, your system becomes a graph of explainable actions.

That graph is more valuable than raw logs.

It’s institutional memory.

---

## Where to go next

Explore:

- decision contracts
- graph analytics
- replay tooling
- governance workflows
- AI decision explainability

The SDK is a foundation.

Decision graphs are the architecture.
