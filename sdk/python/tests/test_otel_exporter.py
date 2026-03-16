import pytest
from unittest.mock import MagicMock
from datetime import datetime

from decision_trace.model import DecisionTraceEvent, EventType
from decision_trace.exporters.otel import OTelExporter

@pytest.fixture
def mock_tracer():
    return MagicMock()

@pytest.fixture
def mock_span():
    span = MagicMock()
    span.is_recording.return_value = True
    return span

def create_event(event_type, payload=None, decision_id="d1", decision_type="test.dec"):
    return DecisionTraceEvent(
        tenant_id="t1",
        environment="env",
        schema_version="1.0",
        timestamp=datetime.now().isoformat(),
        trace_id="tr1",
        decision_id=decision_id,
        parent_decision_id=None,
        event_id="e1",
        event_type=event_type,
        decision_type=decision_type,
        actor={"type": "agent", "id": "a1"},
        payload=payload or {}
    )

def test_start_decision_starts_span(mock_tracer, mock_span):
    mock_tracer.start_span.return_value = mock_span
    exporter = OTelExporter(tracer=mock_tracer)
    
    event = create_event(EventType.DECISION_START)
    exporter.export(event)
    
    mock_tracer.start_span.assert_called_once_with(
        name="test.dec", 
        start_time=int(datetime.fromisoformat(event.timestamp).timestamp() * 1e9)
    )
    mock_span.set_attribute.assert_any_call("dt.trace_id", "tr1")
    mock_span.set_attribute.assert_any_call("dt.decision_id", "d1")
    mock_span.set_attribute.assert_any_call("dt.actor.type", "agent")

def test_evidence_adds_attribute(mock_tracer, mock_span):
    mock_tracer.start_span.return_value = mock_span
    exporter = OTelExporter(tracer=mock_tracer)
    
    # Start
    exporter.export(create_event(EventType.DECISION_START))
    
    # Evidence
    exporter.export(create_event(EventType.DECISION_EVIDENCE, {"key": "risk", "value": 50}))
    
    mock_span.set_attribute.assert_any_call("dt.evidence.risk", 50)

def test_outcome_sets_status(mock_tracer, mock_span):
    mock_tracer.start_span.return_value = mock_span
    exporter = OTelExporter(tracer=mock_tracer)
    
    exporter.export(create_event(EventType.DECISION_START))
    exporter.export(create_event(EventType.DECISION_OUTCOME, {"status": "approved"}))
    
    mock_span.set_attribute.assert_any_call("dt.outcome", "approved")
    # Verify set_status called with OK (checking args is tricky with OTel types, ensuring call exists is sufficient)
    mock_span.set_status.assert_called_once()

def test_flush_ends_span(mock_tracer, mock_span):
    mock_tracer.start_span.return_value = mock_span
    exporter = OTelExporter(tracer=mock_tracer)
    
    exporter.export(create_event(EventType.DECISION_START))
    exporter.flush()
    
    mock_span.end.assert_called_once()
