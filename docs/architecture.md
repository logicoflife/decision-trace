
# Architecture

Decision Trace is built on principles of immutability, schema safety, and failure isolation.

## 1. Append-Only Ledger

The fundamental storage model is an **Append-Only Ledger**.
- Decisions are recorded as a stream of immutable events (`start`, `evidence`, `outcome`).
- You cannot "update" a decision; you can only append new information.
- This ensures a tamper-evident audit trail. Even if a process crashes or is interrupted, the partial history is preserved.

## 2. Schema-First Philosophy

Data quality is paramount for AI evaluation.
- All events are validated against a strict schema (Pydantic models) at the edge.
- **Actors** must have defined types (`system`, `human`, `agent`).
- **Outcomes** are structured.
- **Causal Links** are typed.

This prevents the "log soup" problem where data becomes unqueryable due to format drift.

## 3. The Collector Role

The **Collector** is a decoupled component responsible for:
- Accepting events from the SDK.
- Persisting them to storage (file, database, S3).
- Indexing for query.

Decoupling the collector ensures that the tracing overhead on your application is minimal (serialization + network/disk flush).

## 4. Failure Isolation

The SDK is designed to be **Crash Safe**.
- The tracing layer wraps all export operations in `try-except` blocks.
- If the Collector is down, or the disk is full, the SDK will log an error to `stderr` and swallow the exception.
- **Your application logic will never crash because of a tracing failure.**

See [Safety Guarantees](./safety.md) for more details.
