
import pytest
from unittest.mock import MagicMock
from decision_trace import decision
from decision_trace.exporters.base import Exporter

class CrashingExporter(Exporter):
    def export(self, event):
        raise RuntimeError("Exporter crashed!")

    def flush(self):
        raise RuntimeError("Flush crashed!")

class InMemoryExporter(Exporter):
    def __init__(self):
        self.events = []

    def export(self, event):
        self.events.append(event)

    def flush(self):
        pass

def test_evidence_snapshotting():
    """Verify that mutating payload after emission does not affect the recorded event."""
    exporter = InMemoryExporter()
    payload = {"info": "initial"}
    
    with decision(
        decision_type="test", 
        actor={"type": "system", "id": "test"}, 
        tenant_id="t", 
        environment="e", 
        exporter=exporter
    ) as ctx:
        ctx.evidence("data", payload)
        payload["info"] = "mutated"  # Should not affect recorded event
        
    assert len(exporter.events) >= 2  # start + evidence
    evidence_event = next(e for e in exporter.events if e.event_type == "decision.evidence")
    assert evidence_event.payload["value"]["info"] == "initial"

def test_failure_isolation():
    """Verify that exporter failures do not crash the application."""
    exporter = CrashingExporter()
    
    # This should NOT raise RuntimeError
    with decision(
        decision_type="test", 
        actor={"type": "system", "id": "test"}, 
        tenant_id="t", 
        environment="e", 
        exporter=exporter
    ) as ctx:
        ctx.evidence("key", "value")
        ctx.outcome("ok")

def test_redaction_defaults():
    """Verify that default keys are redacted."""
    exporter = InMemoryExporter()
    
    with decision(
        decision_type="test", 
        actor={"type": "system", "id": "test"}, 
        tenant_id="t", 
        environment="e", 
        exporter=exporter
    ) as ctx:
        ctx.evidence("safe", "visible")
        ctx.evidence("password", "super_secret")
        ctx.evidence("nested", {"api_key": "12345", "other": "ok"})
        
    evidence_events = [e for e in exporter.events if e.event_type == "decision.evidence"]
    print("\nDEBUG EVENTS:", [e.payload for e in evidence_events])
    
    # Test case 1: Nested dictionary key
    try:
        nested_event = next(e for e in evidence_events if e.payload["key"] == "nested")
        assert nested_event.payload["value"]["api_key"] == "[REDACTED]"
        assert nested_event.payload["value"]["other"] == "ok"
    except StopIteration:
        pytest.fail("Could not find nested event")

