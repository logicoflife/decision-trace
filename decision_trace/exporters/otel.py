from typing import Dict, Optional
from opentelemetry import trace
from opentelemetry.trace import Span, Status, StatusCode

from ..model import DecisionTraceEvent, EventType
from .base import Exporter

class OTelExporter(Exporter):
    def __init__(self, tracer: Optional[trace.Tracer] = None):
        self._tracer = tracer or trace.get_tracer("decision-trace")
        self._spans: Dict[str, Span] = {}

    def export(self, event: DecisionTraceEvent) -> None:
        # Map decision.start -> start_span
        if event.event_type == EventType.DECISION_START:
            from datetime import datetime
            # timestamp is a string in the model, but might be passed as datetime if model is loose?
            # Model definition says str usually. 
            # If using Pydantic V2, it might handle types. 
            # Let's assume valid ISO string.
            ts = event.timestamp
            if isinstance(ts, str):
                ts = datetime.fromisoformat(ts)
                
            span = self._tracer.start_span(
                name=event.decision_type,
                start_time=int(ts.timestamp() * 1e9)
            )
            # Set standard attributes
            span.set_attribute("dt.trace_id", event.trace_id)
            span.set_attribute("dt.decision_id", event.decision_id)
            span.set_attribute("dt.tenant_id", event.tenant_id)
            span.set_attribute("dt.environment", event.environment)
            
            # Actor attributes
            # actor is an Actor model (pydantic)
            span.set_attribute("dt.actor.type", event.actor.type.value)
            span.set_attribute("dt.actor.id", event.actor.id)
            if event.actor.org:
                span.set_attribute("dt.actor.org", event.actor.org)
            
            self._spans[event.decision_id] = span
            return

        # For other events, we need the active span
        span = self._spans.get(event.decision_id)
        if not span:
            # If we missed the start event or it's implicitly part of a parent?
            # For now, ignore if no span found.
            return

        payload = event.payload or {}
        
        if event.event_type == EventType.DECISION_EVIDENCE:
            key = payload.get("key")
            value = payload.get("value")
            if key and value is not None:
                # OTel attributes must be primitive types. stringify if complex?
                if isinstance(value, (dict, list)):
                    value = str(value)
                span.set_attribute(f"dt.evidence.{key}", value)
                
        elif event.event_type == EventType.DECISION_OUTCOME:
            status = payload.get("status")
            if status:
                span.set_attribute("dt.outcome", status)
            span.set_status(Status(StatusCode.OK))
            
        elif event.event_type == EventType.DECISION_ERROR:
            msg = payload.get("message", "Unknown error")
            err_type = payload.get("error_type", "Error")
            span.record_exception(Exception(msg), attributes={"exception.type": err_type})
            span.set_status(Status(StatusCode.ERROR, description=msg))
            
        elif event.event_type == EventType.DECISION_POLICY_CHECK:
            policy = payload.get("policy")
            result = payload.get("result")
            if policy:
                span.set_attribute(f"dt.policy.{policy}", result)

    def flush(self) -> None:
        # End all tracked spans
        for span in self._spans.values():
            if span.is_recording():
                span.end()
        self._spans.clear()
