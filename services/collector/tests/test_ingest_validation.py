from pathlib import Path
import sys

import pytest
from fastapi.testclient import TestClient

from decision_trace.tracer import decision
from decision_trace.validate import SchemaUnavailableError
from decision_trace.model import ActorType


ROOT = Path(__file__).resolve().parents[2]
COLLECTOR_ROOT = ROOT / "services" / "collector"
sys.path.append(str(COLLECTOR_ROOT))

from decision_trace_collector.api import create_app


class RecordingExporter:
    def __init__(self) -> None:
        self.events = []

    def export(self, event) -> None:
        self.events.append(event)

    def flush(self) -> None:
        return None


def _sample_event() -> dict:
    exporter = RecordingExporter()
    with decision(
        tenant_id="t1",
        environment="test",
        decision_type="risk_check",
        actor={"id": "u1", "type": ActorType.HUMAN},
        exporter=exporter,
    ):
        pass

    event_dict = exporter.events[0].model_dump(mode="json", exclude_none=True)
    event_dict["parent_decision_id"] = None
    return event_dict


def test_ingest_valid_and_invalid(tmp_path):
    data_path = tmp_path / "events.jsonl"
    app = create_app(data_path)
    client = TestClient(app)

    valid_event = _sample_event()
    response = client.post("/v1/events", json=[valid_event])

    assert response.status_code == 200
    assert data_path.exists()
    assert data_path.read_text(encoding="utf-8").strip()

    invalid_event = dict(valid_event)
    invalid_event.pop("tenant_id")
    response = client.post("/v1/events", json=[invalid_event])

    assert response.status_code == 400
    body = response.json()
    assert "errors" in body
    assert body["errors"][0]["index"] == 0


def test_ingest_uses_env_configured_data_path(tmp_path, monkeypatch):
    data_path = tmp_path / "mounted" / "events.jsonl"
    monkeypatch.setenv("DECISION_TRACE_DATA_PATH", str(data_path))

    app = create_app()
    client = TestClient(app)

    response = client.post("/v1/events", json=[_sample_event()])

    assert response.status_code == 200
    assert data_path.exists()
    assert data_path.read_text(encoding="utf-8").strip()


def test_create_app_fails_fast_when_schema_unavailable(tmp_path, monkeypatch):
    monkeypatch.setattr(
        "decision_trace_collector.api.load_event_schema",
        lambda: (_ for _ in ()).throw(SchemaUnavailableError("missing schema")),
    )

    with pytest.raises(RuntimeError, match="Collector startup failed: missing schema"):
        create_app(tmp_path / "events.jsonl")


def test_create_app_fails_fast_when_output_path_is_unwritable(tmp_path, monkeypatch):
    class BrokenStorage:
        def __init__(self, file_path):
            self.path = file_path

        def ensure_ready(self):
            raise PermissionError("read-only filesystem")

    monkeypatch.setattr("decision_trace_collector.api.JsonlStorage", BrokenStorage)
    monkeypatch.setattr("decision_trace_collector.api.load_event_schema", lambda: {})

    with pytest.raises(RuntimeError, match="output path .* is not writable"):
        create_app(tmp_path / "events.jsonl")
