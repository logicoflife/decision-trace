import pytest

from decision_trace.model import ActorType, EventType
from decision_trace.exporters.file import FileJsonlExporter
from decision_trace.tracer import decision
from decision_trace.validate import validate_event


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
        actor={"id": "u1", "type": ActorType.HUMAN},
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
            actor={"id": "u1", "type": ActorType.SYSTEM},
            exporter=exporter,
        ):
            raise RuntimeError("boom")

    assert any(event.event_type == EventType.DECISION_ERROR for event in exporter.events)


def test_validate_good_event_passes():
    exporter = RecordingExporter()
    with decision(
        tenant_id="t1",
        environment="test",
        decision_type="risk_check",
        actor={"id": "u1", "type": ActorType.HUMAN},
        exporter=exporter,
    ):
        pass

    event_dict = exporter.events[0].model_dump(exclude_none=True)
    event_dict["parent_decision_id"] = None
    validate_event(event_dict)


def test_validate_bad_event_fails_with_message():
    exporter = RecordingExporter()
    with decision(
        tenant_id="t1",
        environment="test",
        decision_type="risk_check",
        actor={"id": "u1", "type": ActorType.SYSTEM},
        exporter=exporter,
    ):
        pass

    bad_event = exporter.events[0].model_dump(exclude_none=True)
    bad_event["parent_decision_id"] = None
    bad_event["event_type"] = "decision.invalid"

    with pytest.raises(ValueError) as excinfo:
        validate_event(bad_event)

    assert "event_type" in str(excinfo.value)


def test_decision_validate_raises_on_invalid_actor():
    exporter = RecordingExporter()
    with pytest.raises(ValueError) as excinfo:
        with decision(
            tenant_id="t1",
            environment="test",
            decision_type="risk_check",
            actor={"id": "u1", "type": "robot"},
            exporter=exporter,
            validate=True,
        ):
            pass

    assert "actor.type" in str(excinfo.value)


def test_file_exporter_writes_lines(tmp_path):
    output_path = tmp_path / "events.jsonl"
    exporter = FileJsonlExporter(str(output_path))

    with decision(
        tenant_id="t1",
        environment="test",
        decision_type="risk_check",
        actor={"id": "u1", "type": ActorType.HUMAN},
        exporter=exporter,
    ):
        pass

    content = output_path.read_text(encoding="utf-8").strip().splitlines()
    assert content
    assert any("decision.start" in line for line in content)
