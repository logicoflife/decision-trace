
# Core Concepts

## What is a Decision?

A **Decision** is a discrete unit of agent reasoning. Unlike a log line (which says *what happened*) or a trace span (which says *how long it took*), a Decision captures *why* something happened.

It is a structured event containing:
1.  **Context**: Who made the decision (Actor) and where (Decision Type).
2.  **Input**: The data used to make the decision (Evidence).
3.  **Logic**: The rules applied (Policy Checks).
4.  **Outcome**: The final result (Outcome/Action).

## Logs vs. Traces vs. Decisions

| Type | Question Answered | Example |
|------|-------------------|---------|
| **Log** | What happened? | `INFO: processing order #123` |
| **Trace** | How long did it take? | `span: process_order (200ms)` |
| **Decision** | **Why did we do it?** | "Approved order #123 because risk_score < 10" |

Decision Trace complements your observability stack. It doesn't replace Logs or OpenTelemetry; it adds a semantic layer for governance and reasoning.

## The Decision DAG

Decisions rarely happen in isolation. They form a Directed Acyclic Graph (DAG).
- An AI Agent proposes a change.
- A Human reviews it.
- A System executes it.

Decision Trace links these events via **Causal Links** (`depends_on`, `triggered_by`), creating a navigable graph of cause and effect.

## Semantic vs Control Flow

- **Control Flow**: "Function A called Function B." (Implementation detail)
- **Semantic Flow**: "Fraud Check blocked Payment." (Business logic)

Decision Trace focuses on Semantic Flow. You instrument the *choices*, not just the functions.
