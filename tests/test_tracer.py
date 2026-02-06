import pytest

from decision_trace.model import ActorType, EventType
from decision_trace.tracer import decision


class RecordingExporter:
    def __init__(self) -> None:
        self.events = []

    def export(self, event) -> None:
        self.events.append(event)

    def flush(self) -> None:
        return None


def test_context_emits_start_event():
    exporter = RecordingExporter()
    with decision(
        tenant_id="t1",
        environment="test",
        decision_type="risk_check",
        actor={"actor_id": "u1", "actor_type": ActorType.HUMAN},
        exporter=exporter,
    ):
        pass

    assert exporter.events
    assert exporter.events[0].event_type == EventType.DECISION_START


def test_exception_emits_error_event():
    exporter = RecordingExporter()
    with pytest.raises(RuntimeError):
        with decision(
            tenant_id="t1",
            environment="test",
            decision_type="risk_check",
            actor={"actor_id": "u1", "actor_type": ActorType.SYSTEM},
            exporter=exporter,
        ):
            raise RuntimeError("boom")

    assert any(event.event_type == EventType.DECISION_ERROR for event in exporter.events)
