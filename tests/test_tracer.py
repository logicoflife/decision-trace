import pytest

from decision_trace.exporters.file import FileJsonlExporter
from decision_trace.exporters.http import HttpExporter
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


def test_sdk_emitted_events_validate():
    exporter = RecordingExporter()
    with decision(
        tenant_id="t1",
        environment="test",
        decision_type="risk_check",
        actor={"id": "u1", "type": ActorType.HUMAN},
        exporter=exporter,
        validate=True,
    ) as ctx:
        ctx.evidence("order_value", 120)
        ctx.policy_check(policy="risk_v1", result="pass")
        ctx.action({"action": "approve"})
        ctx.outcome("approved")


def test_envelope_fields_present():
    exporter = RecordingExporter()
    with decision(
        tenant_id="t1",
        environment="test",
        decision_type="risk_check",
        actor={"id": "u1", "type": ActorType.HUMAN},
        exporter=exporter,
    ):
        pass

    required = {
        "tenant_id",
        "environment",
        "schema_version",
        "timestamp",
        "trace_id",
        "decision_id",
        "parent_decision_id",
        "event_id",
        "event_type",
        "decision_type",
        "actor",
        "payload",
    }
    event_dict = exporter.events[0].model_dump()
    assert required.issubset(event_dict.keys())


def test_payload_is_object_always():
    exporter = RecordingExporter()
    with decision(
        tenant_id="t1",
        environment="test",
        decision_type="risk_check",
        actor={"id": "u1", "type": ActorType.HUMAN},
        exporter=exporter,
    ) as ctx:
        ctx.evidence("order_value", 120)
        ctx.policy_check(policy="risk_v1", result="pass")
        ctx.action({"action": "approve"})
        ctx.outcome("approved")

    assert all(isinstance(event.payload, dict) for event in exporter.events)


def test_http_exporter_unreachable_error_is_clean():
    exporter = HttpExporter("http://127.0.0.1:1")
    with pytest.raises(RuntimeError) as excinfo:
        with decision(
            tenant_id="t1",
            environment="test",
            decision_type="risk_check",
            actor={"id": "u1", "type": ActorType.HUMAN},
            exporter=exporter,
        ):
            pass

    assert str(excinfo.value) == "Collector unreachable at http://127.0.0.1:1. Is it running?"


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
