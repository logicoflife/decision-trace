# OpenTelemetry Mapping Notes

OpenTelemetry is treated as a projection of canonical decision events, not the source of truth.

## Span Model

- one span is emitted per canonical decision event
- the span name is derived from the decision event type and decision type
- the canonical event remains the authoritative payload for lake ingestion and replay

## Attribute Mapping

The exporter attaches `dt.*` attributes so telemetry backends can correlate spans back to the canonical model:

- `dt.tenant_id`
- `dt.environment`
- `dt.schema_version`
- `dt.trace_id`
- `dt.decision_id`
- `dt.parent_decision_id`
- `dt.event_id`
- `dt.event_type`
- `dt.decision_type`
- `dt.actor.id`
- `dt.actor.type`
- `dt.actor.version` when present
- `dt.actor.org` when present

Selected payload fields are also emitted as `dt.payload.*` attributes when they are scalar values.

## Status Mapping

- `decision.error` maps to error span status
- non-error lifecycle events emit normal spans with `dt.event_type` identifying the lifecycle stage

## Operational Guidance

- Use canonical events for compliance and replay.
- Use OTel spans for interactive observability and correlation with existing traces.
- Do not infer canonical field completeness from span data alone because span attribute limits may truncate payload detail.
