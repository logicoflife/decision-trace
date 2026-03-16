# ADR 0001: Decision Trace Java SDK Invariants

## Status

Accepted

## Context

The Java SDK is being introduced into a repository that already has a Python SDK and a shared event contract. Phase 0 requires a stable contract boundary and explicit invariants before semantic core or Spring integration work begins.

## Decision

The Java SDK will follow these invariants:

1. Canonical events are the source of truth.
2. `contracts/decision-event.schema.yaml` is the universal cross-language contract.
3. OpenTelemetry is a projection/export path only and must not redefine event semantics.
4. `trace_id` is the distributed correlation identity.
5. `decision_id` is the semantic node identity.
6. Cross-service calls create a new local decision node and preserve upstream lineage via `parent_decision_id`.
7. The core Java module remains free of Spring dependencies.
8. The emitter abstraction is part of the core API; LMAX is hidden behind that abstraction.
9. Telemetry is fail-open and must not block the business path.
10. Phase 0 establishes a Maven multi-module boundary:
    - `decision-trace-bom`
    - `decision-trace-core`
    - `decision-trace-spring-boot-starter`
    - `decision-trace-samples`

## Consequences

- Phase 1 can build the semantic model without introducing framework coupling.
- Spring-specific behavior will be constrained to the starter module.
- Runtime engine choices can evolve behind the emitter abstraction without changing the canonical model.
