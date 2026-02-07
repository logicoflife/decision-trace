
# Safety & Privacy Guarantees

Decision Trace is designed for production use in sensitive environments. We provide strict guarantees around data integrity, availability, and privacy.

## 1. Evidence Integrity (Snapshotting)

**Rule**: Modifying an object after passing it to the tracer must not change the recorded history.

**Mechanism**:
The SDK performs a `deepcopy` of all dictionaries and lists passed to `evidence()` or `action()` at the moment of capture.
- **Why?** To prevent race conditions where a mutable object is modified by business logic before the async exporter writes it to disk.
- **Guarantee**: The ledger reflects the exact state of the object when `d.evidence()` was called.

## 2. Failure Isolation

**Rule**: Determining *why* something happened is secondary to *making* it happen.

**Mechanism**:
All `tracer` operations (record, flush, close) are wrapped in exception handlers that catch `Exception`.
- Errors are logged to standard error (`sys.stderr`) with a `[DecisionTrace]` prefix.
- The exception is suppressed, and control flow is returned to the application.
- **Guarantee**: A bug in Decision Trace or an outage in the collector will never crash your agent.

## 3. Privacy-Safe Defaults

**Rule**: PII should not leak into traces by default.

**Mechanism**:
The SDK includes a default redaction list (`password`, `token`, `secret`, `api_key`, `auth`, `credential`).
- Any dictionary key matching these terms (case-insensitive) will have its value replaced with `[REDACTED]`.
- This applies recursively to nested payloads.

## 4. Enum Enforcement

**Rule**: Metadata must be queryable.

**Mechanism**:
We enforce strict Enums for:
- **Actor Types**: `human`, `system`, `agent`. (No "bot", "service", "user" confusion).
- **Causal Links**: `depends_on`, `triggered_by`, `uses_evidence_from`.

This discipline ensures that your decision graph remains structurally consistent for analysis tools.
